package com.app.bebinim.data.utils

import android.content.Context
import android.media.MediaPlayer
import com.app.bebinim.R

/** Plays the new-message notification sound (same behaviour as the original app). */
object SoundPlayer {
    @Volatile
    private var player: MediaPlayer? = null

    fun playMessageSound(context: Context) {
        try {
            player?.release()
            player = MediaPlayer.create(context, R.raw.notification_sound)?.apply {
                setVolume(0.7f, 0.7f)
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (_: Exception) {
        }
    }
}
