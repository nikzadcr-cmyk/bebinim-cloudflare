package com.app.bebinim.data.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.app.bebinim.R

/**
 * Plays the new-message notification chime.
 * Full volume on the music/media stream (the one users keep raised while watching),
 * so the chime is clearly audible over the movie.
 */
object SoundPlayer {
    @Volatile
    private var player: MediaPlayer? = null

    fun playMessageSound(context: Context) {
        try {
            player?.release()
            player = null
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            player = MediaPlayer.create(context, R.raw.notification_sound, attrs, 0)?.apply {
                setVolume(1.0f, 1.0f)
                setOnCompletionListener {
                    try { it.release() } catch (_: Exception) {}
                }
                start()
            }
        } catch (_: Exception) {
        }
    }
}
