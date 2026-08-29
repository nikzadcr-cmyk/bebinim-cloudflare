package com.app.bebinim.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.app.bebinim.ui.theme.SurfaceDark
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.viewmodel.LobbyViewModel

/** Standalone player route — same behaviour as the original VideoPlayerScreen. */
@Composable
fun VideoPlayerScreen(navController: NavHostController, videoUrl: String) {
    val lobbyViewModel: LobbyViewModel = viewModel()
    val context = LocalContext.current
    val videoSyncState by lobbyViewModel.videoSyncState.collectAsState()
    val users by lobbyViewModel.users.collectAsState()
    var showUsersList by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            // original: no autoplay — playback follows the room sync (someone must start it)
            playWhenReady = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // apply incoming sync flag (declared before listener so it can be captured)
    var isSyncing by remember { mutableStateOf(false) }

    val playerListener = remember {
        object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isSyncing) lobbyViewModel.updateVideoState(exoPlayer.currentPosition / 1000.0, isPlaying)
            }
        }
    }
    LaunchedEffect(exoPlayer) { exoPlayer.addListener(playerListener) }

    LaunchedEffect(videoSyncState) {
        videoSyncState?.let { sync ->
            isSyncing = true
            exoPlayer.seekTo((sync.currentTime * 1000).toLong())
            exoPlayer.playWhenReady = sync.isPlaying
            kotlinx.coroutines.delay(400)
            isSyncing = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // top bar (portrait)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = WhiteText)
            }
            Text("پخش‌کننده", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WhiteText, modifier = Modifier.weight(1f))
            IconButton(onClick = { showUsersList = !showUsersList }) {
                Icon(
                    Icons.Filled.People,
                    contentDescription = "کاربران آنلاین (${users.size})",
                    tint = WhiteText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (showUsersList) {
            Text(
                "👥 کاربران آنلاین (${users.size})",
                fontSize = 13.sp,
                color = WhiteText,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = exoPlayer } },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

