package com.app.bebinim.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import com.app.bebinim.data.websocket.MusicMetadata
import com.app.bebinim.ui.theme.MusicBgDark
import com.app.bebinim.ui.theme.MusicCardBg
import com.app.bebinim.ui.theme.MusicGreen
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.LobbyViewModel

/**
 * Music lobby — now-playing card, radio sheet, chat & users sheets.
 * Music selection comes from the Cloudflare music catalog.
 */
@Composable
fun MusicLobbyScreen(
    navController: NavHostController,
    lobbyCode: String
) {
    val lobbyViewModel: LobbyViewModel = viewModel()
    val context = LocalContext.current

    val musicMetadata by lobbyViewModel.musicMetadata.collectAsState()
    val currentVideoUrl by lobbyViewModel.currentVideoUrl.collectAsState()
    val messages by lobbyViewModel.messages.collectAsState()
    val users by lobbyViewModel.users.collectAsState()
    val currentUserId by lobbyViewModel.currentUserId.collectAsState()
    val isHost by lobbyViewModel.isHost.collectAsState()
    val joinSuccess by lobbyViewModel.joinSuccess.collectAsState()
    val isMicEnabled by lobbyViewModel.isMicEnabled.collectAsState()
    val lobbyClosed by lobbyViewModel.lobbyClosed.collectAsState()

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    // load current music
    LaunchedEffect(currentVideoUrl) {
        if (currentVideoUrl.isNotBlank() && exoPlayer.currentMediaItem?.localConfiguration?.uri.toString() != currentVideoUrl) {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentVideoUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = isPlaying
        }
    }

    LaunchedEffect(joinSuccess) { if (joinSuccess.isNotBlank()) lobbyViewModel.initVoiceChat() }

    // position ticker
    LaunchedEffect(Unit) {
        while (true) {
            positionMs = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0)
            kotlinx.coroutines.delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MusicBgDark)
            .padding(16.dp)
    ) {
        // header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("لابی موزیک", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MusicGreen)
                Text("کد: $lobbyCode", fontSize = 12.sp, color = WhiteText.copy(alpha = 0.5f))
            }
            IconButton(
                onClick = {
                    lobbyViewModel.exitLobby()
                    navController.popBackStack()
                }
            ) {
                Icon(Icons.Filled.ExitToApp, "خروج", tint = WhiteText)
            }
        }

        Spacer(Modifier.height(8.dp))

        // now playing
        NowPlayingCard(
            metadata = musicMetadata,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            onPlayPause = {
                isPlaying = !isPlaying
                exoPlayer.playWhenReady = isPlaying
                lobbyViewModel.updateVideoState(exoPlayer.currentPosition / 1000.0, isPlaying)
            },
            onSeek = { fraction ->
                val target = (durationMs * fraction).toLong()
                exoPlayer.seekTo(target)
                lobbyViewModel.seekVideo(target / 1000.0)
            }
        )

        Spacer(Modifier.height(16.dp))

        // catalog quick list
        Text("آهنگ‌های پیشنهادی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WhiteText)
        Spacer(Modifier.height(8.dp))
        var tracks by remember { mutableStateOf<List<com.app.bebinim.data.api.Music>>(emptyList()) }
        var loadingTracks by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            try {
                val response = com.app.bebinim.data.api.RetrofitClient.lobbyApiService.getAllMusic(limit = 20)
                tracks = response.body()?.musics ?: emptyList()
            } catch (_: Exception) {
            } finally {
                loadingTracks = false
            }
        }
        if (loadingTracks) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MusicGreen)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(tracks) { track ->
                Card(
                    onClick = {
                        lobbyViewModel.sendMusicWithMetadata(
                            audioUrl = track.audioUrl,
                            name = track.name,
                            artist = track.artistName ?: "",
                            coverImage = track.coverImage ?: "",
                            duration = track.duration ?: 0,
                            musicId = track.id
                        )
                        isPlaying = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MusicCardBg)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(40.dp).background(MusicGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(track.name.take(1), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MusicGreen)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WhiteText, maxLines = 1)
                            Text(track.artistName ?: "", fontSize = 11.sp, color = WhiteText.copy(alpha = 0.6f))
                        }
                        Text("${track.duration ?: 0}s", fontSize = 11.sp, color = WhiteText.copy(alpha = 0.4f))
                    }
                }
            }
        }

        // chat input
        var messageText by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیام خود را بنویسید...", fontSize = 12.sp, color = WhiteText.copy(alpha = 0.4f)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MusicGreen.copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MusicCardBg,
                    unfocusedContainerColor = MusicCardBg
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        lobbyViewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                modifier = Modifier.size(42.dp).background(MusicGreen, CircleShape)
            ) {
                Icon(Icons.Filled.Send, "ارسال", tint = Color.White)
            }
        }
    }
}

@Composable
private fun NowPlayingCard(
    metadata: MusicMetadata?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MusicCardBg.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (metadata == null) {
                Text("هنوز آهنگی انتخاب نشده", fontSize = 13.sp, color = WhiteText.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
            } else {
                Text(metadata.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText, maxLines = 1)
                Text(metadata.artist, fontSize = 12.sp, color = MusicGreen)
            }
            Spacer(Modifier.height(12.dp))
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = MusicGreen,
                    activeTrackColor = MusicGreen,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(positionMs), fontSize = 10.sp, color = WhiteText.copy(alpha = 0.5f))
                Text(formatTime(durationMs), fontSize = 10.sp, color = WhiteText.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onPlayPause,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MusicGreen)
            ) {
                Text(if (isPlaying) "توقف" else "پخش", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
