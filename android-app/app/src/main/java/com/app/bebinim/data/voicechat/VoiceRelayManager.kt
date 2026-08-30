package com.app.bebinim.data.voicechat

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.app.bebinim.data.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Voice chat over the lobby WebSocket (Cloudflare Durable Object relay).
 *
 * Packet framing (same as the original UDP relay):
 *   [0x10][4B BE senderSession][2B BE seq][payload]
 * Payload = raw PCM16 mono 16 kHz.
 *
 * NOTE: no payload encryption — frames travel inside the wss:// (TLS) socket, which
 * already provides confidentiality. The former AES-GCM layer caused the "voice
 * silent both ways" failure class (key delivery races / decrypt failures dropped
 * every frame) with zero real security benefit over TLS.
 *
 * Playback uses USAGE_MEDIA (media volume — the one the user is already raising to
 * watch the movie), NOT the voice-call stream whose volume is often near zero.
 */
class VoiceRelayManager private constructor(private val context: Context) {

    companion object {
        // packet types (same constants as the original relay)
        const val PKT_AUDIO = 0x10
        const val PKT_LEAVE = 0x11

        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE_SAMPLES = 320 // 20 ms @ 16 kHz
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2

        const val PEER_IDLE_TIMEOUT_MS = 8000L
        const val MAX_QUEUED_FRAMES = 12
        // small send queue: voice is realtime — a deep queue only ADDED latency
        // ("ویس دیر به گوش طرف می‌رسه"); overflow drops the OLDEST frame instead
        const val SEND_QUEUE_FRAMES = 16

        @Volatile
        private var instance: VoiceRelayManager? = null

        fun getInstance(context: Context): VoiceRelayManager =
            instance ?: synchronized(this) {
                instance ?: VoiceRelayManager(context.applicationContext).also { instance = it }
            }
    }

    private class PeerAudio {
        val track: AudioTrack = createTrack()
        var lastSeenMs: Long = System.currentTimeMillis()
        var lastSeq: Int = -1
        // thread-safe channel — a dedicated playback coroutine consumes frames
        val frames = Channel<ByteArray>(capacity = 64)
        @Volatile var playbackStarted = false

        companion object {
            fun createTrack(): AudioTrack {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                return AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    maxOf(minBuf, 11520),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                ).apply {
                    try { setVolume(1.0f) } catch (_: Exception) {}
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ws = WebSocketManager.getInstance()

    private val peers = ConcurrentHashMap<Int, PeerAudio>()
    private val sendChannel = Channel<ByteArray>(capacity = SEND_QUEUE_FRAMES)

    private var started = false
    @Volatile private var micEnabled = false
    private var mySessionId = 0
    private var seq = 0

    private var lobbyCode: String = ""
    private var myUserId: String = ""

    private var captureJob: Job? = null
    private var sendJob: Job? = null
    private var cleanupJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    /** binary router hook — receives relayed frames from WebSocketManager */
    init {
        WebSocketManager.VoiceRouter.binaryListener = { bytes -> handleBinary(bytes) }
    }

    @Synchronized
    fun start(lobbyCode: String, myUserId: String) {
        this.lobbyCode = lobbyCode
        this.myUserId = myUserId
        if (started) return
        started = true
        // drain stale frames queued from a previous call before reusing the channel
        while (sendChannel.tryReceive().isSuccess) { /* discard */ }
        seq = 0
        sendJob = scope.launch { sendLoop() }
        cleanupJob = scope.launch { cleanupLoop() }
    }

    private suspend fun cleanupLoop() {
        while (currentCoroutineContext().isActive && started) {
            cleanupPeers(System.currentTimeMillis())
            kotlinx.coroutines.delay(2000)
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        micEnabled = enabled
        if (enabled) setupCapture() else stopCapture()
        scope.launch { ws.sendMicStatus(enabled) }
    }

    fun leaveCall() {
        started = false
        micEnabled = false
        stopCapture()
        sendJob?.cancel()
        sendJob = null
        cleanupJob?.cancel()
        cleanupJob = null
        // NOTE: no LEAVE packet needed — the server drops the user's binary session when
        // the socket closes, and 0x11 frames are ignored by the relay anyway.
        peers.values.forEach {
            try { it.frames.close() } catch (_: Exception) {}
            try { it.track.stop(); it.track.release() } catch (_: Exception) {}
        }
        peers.clear()
    }

    // ---------------- capture / send ----------------
    @SuppressLint("MissingPermission")
    private fun setupCapture() {
        if (audioRecord != null) return
        if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        ) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // VOICE_COMMUNICATION gives the platform voice pipeline (AEC routing); fall back
        // to MIC if the device refuses to initialize that source.
        var source = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        var record = buildRecord(source, minBuf)
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            try { record?.release() } catch (_: Exception) {}
            source = MediaRecorder.AudioSource.MIC
            record = buildRecord(source, minBuf)
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) return
        audioRecord = record
        // hardware echo-cancel + noise-suppression — keeps the far end clean of room noise
        try {
            audioRecord?.audioSessionId?.let { sid ->
                echoCanceler = AcousticEchoCanceler.create(sid)
                noiseSuppressor = NoiseSuppressor.create(sid)
            }
        } catch (_: Exception) {}
        captureJob = scope.launch {
            val buf = ByteArray(FRAME_SIZE_BYTES)
            try {
                audioRecord?.startRecording()
                while (isActive && micEnabled && started) {
                    val read = audioRecord?.read(buf, 0, FRAME_SIZE_BYTES) ?: -1
                    if (read > 0) {
                        val frame = ByteArray(read)
                        System.arraycopy(buf, 0, frame, 0, read)
                        // never block the mic loop — if the queue is full, drop the OLDEST
                        // frame and enqueue this one (blocking here skewed realtime audio)
                        if (!sendChannel.trySend(frame).isSuccess) {
                            sendChannel.tryReceive()
                            sendChannel.trySend(frame)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { audioRecord?.stop() } catch (_: Exception) {}
            }
        }
    }

    private fun buildRecord(source: Int, minBuf: Int): AudioRecord? = try {
        AudioRecord(
            source,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, FRAME_SIZE_BYTES * 4)
        )
    } catch (_: Exception) {
        null
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    private suspend fun sendLoop() {
        for (frame in sendChannel) {
            try {
                ws.rawSendBinary(buildAudioPacket(frame))
            } catch (_: Exception) {
            }
        }
    }

    private fun buildAudioPacket(payload: ByteArray): ByteArray {
        seq = (seq + 1) and 0xFFFF
        val out = ByteArray(1 + 4 + 2 + payload.size)
        out[0] = PKT_AUDIO.toByte()
        writeInt32BE(out, 1, mySessionId) // server rewrites this with the assigned session
        writeInt16BE(out, 5, seq)
        System.arraycopy(payload, 0, out, 7, payload.size)
        return out
    }

    // ---------------- receive / playback ----------------
    private fun handleBinary(bytes: ByteArray) {
        if (bytes.size < 7) return
        when (bytes[0].toInt() and 0xFF) {
            PKT_AUDIO -> handleAudioPacket(bytes)
        }
    }

    private fun handleAudioPacket(bytes: ByteArray) {
        val senderSession = readInt32BE(bytes, 1)
        // NOTE: no local echo filter — the relay never sends a socket its own frames
        // (verified end-to-end); the server rewrites the session header per sender.
        val rseq = readInt16BE(bytes, 5) and 0xFFFF
        val peer = peers.getOrPut(senderSession) { PeerAudio() }
        peer.lastSeenMs = System.currentTimeMillis()
        val diff = (rseq - peer.lastSeq) and 0xFFFF
        if (diff == 0 || diff > 32768) return // duplicate / out of order
        peer.lastSeq = rseq
        val pcm = bytes.copyOfRange(7, bytes.size)
        if (pcm.size < 2) return
        // enqueue; a single playback coroutine per peer drains it in order
        if (peer.frames.trySend(pcm).isFailure) {
            peer.frames.tryReceive()
            peer.frames.trySend(pcm)
        }
        if (!peer.playbackStarted) {
            peer.playbackStarted = true
            scope.launch { playbackLoop(peer) }
        }
    }

    private suspend fun playbackLoop(peer: PeerAudio) {
        try {
            peer.track.play()
            for (data in peer.frames) {
                try {
                    peer.track.write(data, 0, data.size)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    // ---------------- utils ----------------
    private fun writeInt32BE(arr: ByteArray, off: Int, v: Int) {
        arr[off] = (v ushr 24).toByte(); arr[off + 1] = (v ushr 16).toByte()
        arr[off + 2] = (v ushr 8).toByte(); arr[off + 3] = v.toByte()
    }

    private fun writeInt16BE(arr: ByteArray, off: Int, v: Int) {
        arr[off] = (v ushr 8).toByte(); arr[off + 1] = v.toByte()
    }

    private fun readInt32BE(arr: ByteArray, off: Int): Int =
        ((arr[off].toInt() and 0xFF) shl 24) or ((arr[off + 1].toInt() and 0xFF) shl 16) or
            ((arr[off + 2].toInt() and 0xFF) shl 8) or (arr[off + 3].toInt() and 0xFF)

    private fun readInt16BE(arr: ByteArray, off: Int): Int =
        ((arr[off].toInt() and 0xFF) shl 8) or (arr[off + 1].toInt() and 0xFF)

    private fun cleanupPeers(now: Long) {
        peers.entries.removeIf { (sid, p) ->
            if (now - p.lastSeenMs > PEER_IDLE_TIMEOUT_MS) {
                try { p.frames.close() } catch (_: Exception) {}
                try { p.track.stop() } catch (_: Exception) {}
                try { p.track.release() } catch (_: Exception) {}
                true
            } else false
        }
    }
}
