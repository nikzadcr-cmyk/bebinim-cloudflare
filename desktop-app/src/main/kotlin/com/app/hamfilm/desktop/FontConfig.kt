package com.app.hamfilm.desktop

import java.io.File

/**
 * Makes chat emojis render in COLOR on Linux.
 *
 * Why this is needed:
 *  Compose picks ONE font per style from a bundled FontFamily — the bundled
 *  NotoColorEmoji never participates in missing-glyph fallback. On Linux the
 *  Skia fallback goes through fontconfig (system fonts), which does not know
 *  about fonts packed inside the jar. So emojis fell back to DejaVu/system
 *  fonts and appeared monochrome (or as tofu).
 *
 * Fix: at startup — BEFORE Skia initializes its font manager (i.e. before the
 * first Compose frame) — extract the color-emoji font into the user's font
 * directory and refresh the fontconfig cache for that single dir (fast).
 * Skia then resolves emoji codepoints to Noto Color Emoji (CBDT color bitmaps)
 * automatically and chat emojis come out in full color.
 */
object FontConfig {

    private const val FONT_RESOURCE = "fonts/NotoColorEmoji.ttf"
    private const val FONT_FILE_NAME = "NotoColorEmoji-CBDT.ttf"

    fun install() {
        try {
            val dir = File(System.getProperty("user.home"), ".local/share/fonts/hamfilm")
            if (!dir.isDirectory && !dir.mkdirs()) return

            val data = Res.bytes(FONT_RESOURCE) ?: return
            val out = File(dir, FONT_FILE_NAME)

            val changed = if (!out.exists() || out.length() != data.size.toLong()) {
                try {
                    val tmp = File(dir, "$FONT_FILE_NAME.tmp")
                    tmp.writeBytes(data)
                    if (out.exists()) out.delete()
                    if (!tmp.renameTo(out)) {
                        tmp.copyTo(out, overwrite = true)
                        tmp.delete()
                    }
                    true
                } catch (_: Exception) {
                    false
                }
            } else false

            // refresh fontconfig cache for this single dir (fast, ~100ms)
            if (changed) {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("fc-cache", "-f", dir.absolutePath))
                    val started = System.currentTimeMillis()
                    while (p.isAlive && System.currentTimeMillis() - started < 4000) {
                        Thread.sleep(40)
                    }
                    if (p.isAlive) p.destroyForcibly()
                } catch (_: Exception) {
                    // no fc-cache binary — fontconfig still scans the dir lazily
                }
            }
        } catch (_: Throwable) {
            // never crash the app because of font installation
        }
    }
}
