package com.app.hamfilm.desktop.video

import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import java.io.File
import javax.swing.JComponent

/**
 * vlcj-based playback engine (plays every format VLC plays — local files, URLs, streams).
 *
 * Sync contract mirrors the Android ExoPlayer engine:
 *  - media is ALWAYS loaded paused (no auto-play — a member presses play)
 *  - [onPlayingChange] fires for local AND remote-initiated state changes; the caller
 *    guards with [isSyncing] + [lastMediaLoadAt] exactly like LobbyScreen does.
 */
class VideoEngine(
    private val onTimeChanged: (Long, Long) -> Unit,
    private val onPlayingChange: (Boolean) -> Unit,
    private val onMediaReady: () -> Unit,
    private val onEnded: () -> Unit,
    private val onError: (String) -> Unit,
    private val onTracksChanged: () -> Unit
) {

    val component: JComponent
    private val player: uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer

    /** true while a remote sync is being applied — suppress local broadcasts (isSyncing) */
    @Volatile
    var isSyncing = false

    /** set right after a (re)load — suppresses the 0-time pause blip broadcast */
    @Volatile
    var lastMediaLoadAt = 0L

    /** libvlc cannot always seek before the input is seekable — park it here */
    @Volatile
    private var pendingSeekMs = -1L

    init {
        val comp = EmbeddedMediaPlayerComponent()
        component = comp
        player = comp.mediaPlayer()
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                onTimeChanged(newTime, mediaPlayer.status().length())
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                onTimeChanged(mediaPlayer.status().time(), newLength)
            }

            override fun playing(mediaPlayer: MediaPlayer) {
                applyPendingSeek(mediaPlayer)
                onPlayingChange(true)
                onTracksChanged()
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                onPlayingChange(false)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                onPlayingChange(false)
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                onPlayingChange(false)
                onEnded()
            }

            override fun error(mediaPlayer: MediaPlayer) {
                onPlayingChange(false)
                onError("خطا در پخش — لینک یا فایل را بررسی کنید")
            }

            override fun mediaPlayerReady(mediaPlayer: MediaPlayer) {
                onMediaReady()
                onTracksChanged()
            }

            override fun seekableChanged(mediaPlayer: MediaPlayer, newSeekable: Int) {
                if (newSeekable != 0) applyPendingSeek(mediaPlayer)
            }
        })
    }

    private fun applyPendingSeek(mediaPlayer: MediaPlayer) {
        val target = pendingSeekMs
        if (target >= 0) {
            pendingSeekMs = -1
            try { mediaPlayer.controls().setTime(target) } catch (_: Exception) {}
        }
    }

    val isPlaying: Boolean
        get() = try { player.status().isPlaying } catch (_: Exception) { false }

    val currentTimeMs: Long
        get() = try { player.status().time() } catch (_: Exception) { 0L }

    val durationMs: Long
        get() = try { player.status().length() } catch (_: Exception) { 0L }

    val isSeekable: Boolean
        get() = try { player.status().isSeekable } catch (_: Exception) { false }

    /** Load media PAUSED — playback starts only when someone presses play. */
    fun loadPaused(mrl: String) {
        lastMediaLoadAt = System.currentTimeMillis()
        pendingSeekMs = -1L
        try {
            player.media().startPaused(mrl)
        } catch (e: Exception) {
            onError("بارگذاری رسانه ناموفق بود: ${e.message ?: ""}")
        }
    }

    fun play() {
        try {
            if (!player.status().isPlaying) player.controls().play()
        } catch (_: Exception) {}
    }

    fun pause() {
        try {
            if (player.status().isPlaying) player.controls().pause()
        } catch (_: Exception) {}
    }

    fun stop() {
        try { player.controls().stop() } catch (_: Exception) {}
    }

    fun seekTo(ms: Long) {
        try {
            if (isSeekable) {
                player.controls().setTime(ms.coerceAtLeast(0))
            } else {
                pendingSeekMs = ms
            }
        } catch (_: Exception) {}
    }

    fun setRate(rate: Float) {
        try { player.controls().setRate(rate) } catch (_: Exception) {}
    }

    // ---- tracks ----
    fun audioTracks(): List<TrackDescription> =
        try { player.audio().trackDescriptions().filterNotNull() } catch (_: Exception) { emptyList() }

    fun setAudioTrack(id: Int) {
        try { player.audio().setTrack(id) } catch (_: Exception) {}
    }

    fun subtitleTracks(): List<TrackDescription> =
        try { player.subpictures().trackDescriptions().filterNotNull() } catch (_: Exception) { emptyList() }

    fun setSubtitleTrack(id: Int) {
        try { player.subpictures().setTrack(id) } catch (_: Exception) {}
    }

    /** id of the currently selected audio track (-1 = none/disable) */
    val currentAudioTrackId: Int
        get() = try { player.audio().track() } catch (_: Exception) { -1 }

    /** id of the currently selected subtitle track (-1 = none/disable) */
    val currentSubtitleTrackId: Int
        get() = try { player.subpictures().track() } catch (_: Exception) { -1 }

    val currentRate: Float
        get() = try { player.status().rate() } catch (_: Exception) { 1f }

    fun addSubtitleFile(path: String) {
        try { player.subpictures().setSubTitleFile(File(path)) } catch (_: Exception) {}
    }

    fun release() {
        try { player.release() } catch (_: Exception) {}
    }

    companion object {
        /** Probe libvlc availability (shows the install help if the user lacks VLC). */
        fun isVlcAvailable(): Boolean = try {
            uk.co.caprica.vlcj.factory.MediaPlayerFactory().release()
            true
        } catch (_: Throwable) {
            false
        }
    }
}
