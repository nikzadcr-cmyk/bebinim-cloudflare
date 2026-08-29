package com.app.bebinim.data.voicechat

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.app.bebinim.data.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Voice chat over the lobby WebSocket (Cloudflare Durable Object relay).
 *
 * Adapted from the original UDP relay: identical packet framing
 *   [0x10][4B BE senderSession][2B BE seq][payload]
 * except the payload is PCM16 mono 16 kHz (no Opus native dependency),
 * optionally AES-GCM encrypted with the key delivered via basemsg-voice-token.
 */
class VoiceRelayManager private constructor(private val context: Context) {

    companion object {
        // packet types (same constants as the original relay)
        const val PKT_AUDIO = 0x10
        const val PKT_LEAVE = 0x11

        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE_SAMPLES = 320 // 20 ms @ 16 kHz
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2

        const val GCM_IV_LEN = 12
        const val GCM_TAG_BITS = 128

        const val KEEPALIVE_INTERVAL_MS = 5000L
        const val PEER_IDLE_TIMEOUT_MS = 8000L
        const val CREDENTIAL_REFRESH_MARGIN_MS = 30_000L
        const val MAX_QUEUED_FRAMES = 12

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
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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
                )
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ws = WebSocketManager.getInstance()

    private val peers = ConcurrentHashMap<Int, PeerAudio>()
    private val sendChannel = Channel<ByteArray>(capacity = 32)
    private val secureRandom = SecureRandom()

    private var aesKey: SecretKeySpec? = null
    private var credentialExpiresAtMs = 0L

    private var started = false
    @Volatile private var micEnabled = false
    private var mySessionId = 0
    private var seq = 0

    private var lobbyCode: String = ""
    private var myUserId: String = ""

    private var captureJob: Job? = null
    private var sendJob: Job? = null
    private var keepAliveJob: Job? = null
    private var cleanupJob: Job? = null
    private var audioRecord: AudioRecord? = null

    /** binary router hook — receives relayed frames from WebSocketManager */
    init {
        WebSocketManager.VoiceRouter.binaryListener = { bytes -> handleBinary(bytes) }
    }

    fun start(lobbyCode: String, myUserId: String) {
        if (started) return
        started = true
        this.lobbyCode = lobbyCode
        this.myUserId = myUserId
        // drain stale frames queued from a previous call before reusing the channel
        while (sendChannel.tryReceive().isSuccess) { /* discard */ }
        sendJob = scope.launch { sendLoop() }
        keepAliveJob = scope.launch { keepAliveLoop() }
        cleanupJob = scope.launch { cleanupLoop() }
        setupAudioRouting()
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
        keepAliveJob?.cancel()
        keepAliveJob = null
        cleanupJob?.cancel()
        cleanupJob = null
        // NOTE: no LEAVE packet needed — the server drops the user's binary session when
        // the socket closes, and 0x11 frames are ignored by the relay anyway.
        peers.values.forEach {
            try { it.frames.close() } catch (_: Exception) {}
            try { it.track.stop(); it.track.release() } catch (_: Exception) {}
        }
        peers.clear()
        resetAudioRouting()
    }

    // ---------------- credential / hello ----------------
    private suspend fun ensureFreshCredential() {
        val now = System.currentTimeMillis()
        if (aesKey != null && credentialExpiresAtMs > now + CREDENTIAL_REFRESH_MARGIN_MS) return
        val cred = ws.requestVoiceToken() ?: return
        try {
            val raw = Base64.decode(cred.keyBase64, Base64.NO_WRAP)
            aesKey = SecretKeySpec(raw, "AES")
            credentialExpiresAtMs = now + cred.expiresInSec * 1000
        } catch (_: Exception) {
        }
    }

    private suspend fun keepAliveLoop() {
        while (currentCoroutineContext().isActive && started) {
            try {
                ensureFreshCredential()
                // no-op HELLO: the relay infers liveness from mic-status + audio
            } catch (_: Exception) {}
            delay(KEEPALIVE_INTERVAL_MS)
        }
    }

    private suspend fun cleanupLoop() {
        while (currentCoroutineContext().isActive && started) {
            cleanupPeers(System.currentTimeMillis())
            delay(2000)
        }
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
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, FRAME_SIZE_BYTES * 4)
        )
        captureJob = scope.launch {
            val buf = ByteArray(FRAME_SIZE_BYTES)
            try {
                audioRecord?.startRecording()
                while (isActive && micEnabled && started) {
                    val read = audioRecord?.read(buf, 0, FRAME_SIZE_BYTES) ?: -1
                    if (read > 0) {
                        val frame = ByteArray(read)
                        System.arraycopy(buf, 0, frame, 0, read)
                        sendChannel.send(encryptAudio(frame))
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { audioRecord?.stop() } catch (_: Exception) {}
            }
        }
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    private suspend fun sendLoop() {
        for (frame in sendChannel) {
            try {
                sendBinary(buildAudioPacket(frame))
            } catch (_: Exception) {
            }
        }
    }

    private fun buildAudioPacket(payload: ByteArray): ByteArray {
        seq = (seq + 1) and 0xFFFF
        val out = ByteArray(1 + 4 + 2 + payload.size)
        out[0] = PKT_AUDIO.toByte()
        writeInt32BE(out, 1, mySessionId)
        writeInt16BE(out, 5, seq)
        System.arraycopy(payload, 0, out, 7, payload.size)
        return out
    }

    private fun buildLeave(): ByteArray {
        val out = ByteArray(5)
        out[0] = PKT_LEAVE.toByte()
        writeInt32BE(out, 1, mySessionId)
        return out
    }

    private suspend fun sendBinary(bytes: ByteArray) {
        // WebSocketManager sends raw binary through the active socket
        wsSendBinary(bytes)
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
        if (senderSession == mySessionId) return
        val rseq = readInt16BE(bytes, 5) and 0xFFFF
        val peer = peers.getOrPut(senderSession) { PeerAudio() }
        peer.lastSeenMs = System.currentTimeMillis()
        val diff = (rseq - peer.lastSeq) and 0xFFFF
        if (diff == 0 || diff > 32768) return // duplicate / out of order
        peer.lastSeq = rseq
        val payload = bytes.copyOfRange(7, bytes.size)
        val pcm = try { decryptAudio(payload) } catch (_: Exception) { payload }
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

    // ---------------- crypto (AES-GCM, same design as original) ----------------
    private fun encryptAudio(plain: ByteArray): ByteArray {
        val key = aesKey ?: return plain
        val iv = ByteArray(GCM_IV_LEN).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        val out = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, out, 0, iv.size)
        System.arraycopy(ct, 0, out, iv.size, ct.size)
        return out
    }

    private fun decryptAudio(payload: ByteArray): ByteArray {
        val key = aesKey ?: return payload
        if (payload.size <= GCM_IV_LEN) return payload
        val iv = payload.copyOfRange(0, GCM_IV_LEN)
        val ct = payload.copyOfRange(GCM_IV_LEN, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    // ---------------- audio routing ----------------
    private fun setupAudioRouting() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            // audio must be audible — route to the speakerphone like a call app
            am.isSpeakerphoneOn = true
        } catch (_: Exception) {
        }
    }

    private fun resetAudioRouting() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
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

    private fun wsSendBinary(bytes: ByteArray) {
        // access the raw socket through the manager's send channel
        WebSocketManager.getInstance().let { it.rawSendBinary(bytes) }
    }
}
