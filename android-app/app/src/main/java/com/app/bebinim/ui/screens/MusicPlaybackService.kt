package com.app.bebinim.ui.screens

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.app.bebinim.MainActivity
import com.app.bebinim.R

/**
 * Foreground music playback service — same design as the original
 * (channel "پخش موزیک", MediaSession, play/pause/next/prev/stop actions).
 */
class MusicPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.app.bebinim.action.PLAY"
        const val ACTION_PAUSE = "com.app.bebinim.action.PAUSE"
        const val ACTION_STOP = "com.app.bebinim.action.STOP"
        const val ACTION_NEXT = "com.app.bebinim.action.NEXT"
        const val ACTION_PREV = "com.app.bebinim.action.PREV"

        @JvmStatic
        var onNextTrack: (() -> Unit)? = null

        @JvmStatic
        var onPrevTrack: (() -> Unit)? = null
    }

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        exoPlayer = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateNotification()
                }
            })
        }
        mediaSession = MediaSessionCompat(this, "MusicLobbySession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { exoPlayer?.play() }
                override fun onPause() { exoPlayer?.pause() }
                override fun onStop() { stopSelf() }
                override fun onSeekTo(pos: Long) { exoPlayer?.seekTo(pos) }
                override fun onSkipToNext() { onNextTrack?.invoke() }
                override fun onSkipToPrevious() { onPrevTrack?.invoke() }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> exoPlayer?.play()
            ACTION_PAUSE -> exoPlayer?.pause()
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_NEXT -> onNextTrack?.invoke()
            ACTION_PREV -> onPrevTrack?.invoke()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    fun updateMetadata(title: String, artist: String, coverUrl: String?) {
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title.ifBlank { "در حال پخش موزیک" })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist.ifBlank { "لابی موزیک" })
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "لابی موزیک ببینیم")
                .build()
        )
        updateNotification()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.music_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.music_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val isPlaying = exoPlayer?.isPlaying == true

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("در حال پخش موزیک")
            .setContentText("لابی موزیک")
            .setSubText("لابی موزیک ببینیم")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setColor(-14829228)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_backward, "قبلی", pendingAction(ACTION_PREV, 1))
            .addAction(
                if (isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle,
                if (isPlaying) "توقف" else "پخش",
                pendingAction(if (isPlaying) ACTION_PAUSE else ACTION_PLAY, 2)
            )
            .addAction(R.drawable.ic_forward, "بعدی", pendingAction(ACTION_NEXT, 3))
            .addAction(R.drawable.ic_delete, "بستن", pendingAction(ACTION_STOP, 4))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun pendingAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, MusicPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (exoPlayer?.isPlaying != true) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        exoPlayer?.release()
        mediaSession?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
