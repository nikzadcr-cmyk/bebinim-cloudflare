package com.app.hamfilm.desktop.voice

import com.app.hamfilm.desktop.net.HamSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

/**
 * Voice chat over the lobby WebSocket — JavaSound port of the Android VoiceRelayManager.
 *
 * Packet framing (identical to the Android client and the DO relay):
 *   [0x10][4B BE senderSession][2B BE seq][payload]
 * Payload = raw PCM16 mono 16 kHz little-endian.
 *
 * No payload encryption — frames travel inside the wss:// (TLS) socket.
 * The relay never echoes a socket its own frames and rewrites the session header.
 */
class VoiceRelay private constructor() {

    companion object {
        const val PKT_AUDIO = 0x10
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE_SAMPLES = 320 // 20 ms @ 16 kHz
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2

        const val PEER_IDLE_TIMEOUT_MS = 8000L
        const val SEND_QUEUE_FRAMES = 16

        @Volatile
        private var instance: VoiceRelay? = null

        fun getInstance(): VoiceRelay =
            instance ?: synchronized(this) {
                instance ?: VoiceRelay().also { instance = it }
            }
    }

    private class PeerAudio {
        var line: SourceDataLine? = null
        var lastSeenMs: Long = System.currentTimeMillis()
        var lastSeq: Int = -1
        val frames = Channel<ByteArray>(capacity = 64)
        @Volatile var playbackStarted = false
        @Volatile var playbackJob: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ws = HamSocket.getInstance()

    private val peers = ConcurrentHashMap<Int, PeerAudio>()
    private val sendChannel = Channel<ByteArray>(capacity = SEND_QUEUE_FRAMES)

    @Volatile private var started = false
    @Volatile private var micEnabled = false
    private var mySessionId = 0
    private var seq = 0

    private var lobbyCode: String = ""
    private var myUserId: String = ""

    private var sendJob: Job? = null
    private var cleanupJob: Job? = null
    @Volatile private var micLine: TargetDataLine? = null
    @Volatile private var captureThread: Thread? = null

    /** binary router hook — receives relayed frames from the socket manager */
    init {
        HamSocket.VoiceRouter.binaryListener = { bytes -> handleBinary(bytes) }
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
            delay(2000)
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
        peers.values.forEach { releasePeer(it) }
        peers.clear()
    }

    // ---------------- capture / send ----------------
    private fun setupCapture() {
        if (micLine != null) return
        val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) return
        try {
            val line = AudioSystem.getTargetDataLine(format) as TargetDataLine
            line.open(format, FRAME_SIZE_BYTES * 8)
            line.start()
            micLine = line
            captureThread = Thread {
                val buf = ByteArray(FRAME_SIZE_BYTES)
                try {
                    while (!Thread.currentThread().isInterrupted && micEnabled && started) {
                        val read = line.read(buf, 0, FRAME_SIZE_BYTES)
                        if (read > 0) {
                            val frame = ByteArray(read)
                            System.arraycopy(buf, 0, frame, 0, read)
                            // never block the mic loop — drop-oldest on overflow
                            if (!sendChannel.trySend(frame).isSuccess) {
                                sendChannel.tryReceive()
                                sendChannel.trySend(frame)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }.apply {
                name = "hamfilm-mic"
                isDaemon = true
                start()
            }
        } catch (_: Exception) {
            try { micLine?.close() } catch (_: Exception) {}
            micLine = null
        }
    }

    private fun stopCapture() {
        captureThread?.interrupt()
        captureThread = null
        try { micLine?.stop() } catch (_: Exception) {}
        try { micLine?.close() } catch (_: Exception) {}
        micLine = null
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
        if (!started) return
        val senderSession = readInt32BE(bytes, 1)
        // no local echo filter — the relay never sends a socket its own frames
        val rseq = readInt16BE(bytes, 5) and 0xFFFF
        val peer = peers.getOrPut(senderSession) { PeerAudio() }
        peer.lastSeenMs = System.currentTimeMillis()
        val diff = (rseq - peer.lastSeq) and 0xFFFF
        if (diff == 0 || diff > 32768) return // duplicate / out of order
        peer.lastSeq = rseq
        val pcm = bytes.copyOfRange(7, bytes.size)
        if (pcm.size < 2) return
        if (peer.frames.trySend(pcm).isFailure) {
            peer.frames.tryReceive()
            peer.frames.trySend(pcm)
        }
        if (!peer.playbackStarted) {
            peer.playbackStarted = true
            peer.playbackJob = scope.launch { playbackLoop(peer) }
        }
    }

    private suspend fun playbackLoop(peer: PeerAudio) {
        try {
            val format = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) return
            val line = AudioSystem.getSourceDataLine(format)
            line.open(format, 11520)
            line.start()
            peer.line = line
            for (data in peer.frames) {
                try {
                    line.write(data, 0, data.size)
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun releasePeer(peer: PeerAudio) {
        try { peer.frames.close() } catch (_: Exception) {}
        try { peer.line?.stop() } catch (_: Exception) {}
        try { peer.line?.close() } catch (_: Exception) {}
        peer.line = null
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
        peers.entries.removeIf { (_, p) ->
            if (now - p.lastSeenMs > PEER_IDLE_TIMEOUT_MS) {
                releasePeer(p)
                true
            } else false
        }
    }
}
