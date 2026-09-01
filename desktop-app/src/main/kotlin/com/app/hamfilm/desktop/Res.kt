package com.app.hamfilm.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Classpath resource + image helpers + the message chime. */
object Res {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun bytes(path: String): ByteArray? =
        ClassLoader.getSystemResourceAsStream("hamfilm/$path")?.use { it.readBytes() }

    fun imageFromBytes(data: ByteArray): ImageBitmap =
        Image.makeFromEncoded(data).toComposeImageBitmap()

    fun image(path: String): ImageBitmap? =
        bytes(path)?.let { imageFromBytes(it) }

    // ---- bundled art ----
    val logo: ImageBitmap? by lazy { image("logo.png") }

    /** app icon for the window titlebar / taskbar */
    val icon: ImageBitmap? by lazy { image("icon.png") }

    private val avatars = ConcurrentHashMap<String, ImageBitmap?>()
    fun avatar(id: String?): ImageBitmap? {
        if (id.isNullOrBlank()) return null
        if (avatars.containsKey(id)) return avatars[id]
        val bmp = image("avatars/$id.png")
        avatars[id] = bmp
        return bmp
    }

    private val stickers = ConcurrentHashMap<String, ImageBitmap?>()
    fun sticker(fileName: String): ImageBitmap? {
        if (fileName.isBlank()) return null
        if (stickers.containsKey(fileName)) return stickers[fileName]
        val bmp = image("stickers/$fileName.png")
        stickers[fileName] = bmp
        return bmp
    }

    /** legacy icon ids → remote jpg (cached) */
    private val remoteImages = ConcurrentHashMap<String, ImageBitmap?>()
    suspend fun remoteImage(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        if (remoteImages.containsKey(url)) return@withContext remoteImages[url]
        val bmp = try {
            val data = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            } ?: return@withContext null
            imageFromBytes(data)
        } catch (_: Exception) {
            null
        }
        remoteImages[url] = bmp
        bmp
    }

    // ---------------- message chime (E5 → A5 → C#6 sparkle-bell, like the app sound) ----------------
    private val chimeLock = Any()

    fun playChime() {
        Thread {
            synchronized(chimeLock) {
                try {
                    val sampleRate = 44100f
                    val notes = arrayOf(
                        Triple(659.25, 0.00, 0.55), // E5
                        Triple(880.00, 0.13, 0.60), // A5
                        Triple(1108.73, 0.26, 0.90) // C#6
                    )
                    val totalSec = 1.35
                    val n = (sampleRate * totalSec).toInt()
                    val pcm = ShortArray(n)
                    for (i in 0 until n) {
                        val t = i / sampleRate
                        var s = 0.0
                        for ((f, start, dur) in notes) {
                            if (t >= start) {
                                val dt = t - start
                                val env = exp(-5.5 * dt) * (dt / 0.012).coerceAtMost(1.0)
                                val shimmer = 1.0 + 0.12 * sin(2 * PI * 6.0 * dt)
                                s += env * shimmer * (0.72 * sin(2 * PI * f * dt) +
                                        0.22 * sin(2 * PI * 2 * f * dt) +
                                        0.08 * sin(2 * PI * 3 * f * dt))
                            }
                        }
                        pcm[i] = (s * 0.62 * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    val format = AudioFormat(sampleRate, 16, 1, true, false)
                    val line = AudioSystem.getSourceDataLine(format)
                    line.open(format, 44100 * 4)
                    line.start()
                    val bytes = ByteArray(n * 2)
                    for (i in 0 until n) {
                        bytes[2 * i] = (pcm[i].toInt() and 0xFF).toByte()
                        bytes[2 * i + 1] = ((pcm[i].toInt() shr 8) and 0xFF).toByte()
                    }
                    line.write(bytes, 0, bytes.size)
                    line.drain()
                    line.stop()
                    line.close()
                } catch (_: Exception) {
                    // no audio device — silent
                }
            }
        }.apply { isDaemon = true }.start()
    }
}
