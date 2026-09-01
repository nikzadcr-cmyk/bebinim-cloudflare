package com.app.hamfilm.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.hamfilm.desktop.AppBgGradient
import com.app.hamfilm.desktop.AvatarDef
import com.app.hamfilm.desktop.BlueAccent
import com.app.hamfilm.desktop.BorderGray
import com.app.hamfilm.desktop.CardStrokeColor
import com.app.hamfilm.desktop.ChipStrokeColor
import com.app.hamfilm.desktop.ChipDark
import com.app.hamfilm.desktop.DarkCardBackground
import com.app.hamfilm.desktop.DarkNavyBackground
import com.app.hamfilm.desktop.GreenAccent
import com.app.hamfilm.desktop.HeaderGrad
import com.app.hamfilm.desktop.LOBBY_ICONS
import com.app.hamfilm.desktop.LightGrayText
import com.app.hamfilm.desktop.MediumGrayText
import com.app.hamfilm.desktop.RedAccent
import com.app.hamfilm.desktop.NotifGrad
import com.app.hamfilm.desktop.Res
import com.app.hamfilm.desktop.SessionUser
import com.app.hamfilm.desktop.YellowAccent
import com.app.hamfilm.desktop.YellowGrad
import com.app.hamfilm.desktop.net.HamSocket
import com.app.hamfilm.desktop.video.VideoEngine
import com.app.hamfilm.desktop.voice.VoiceRelay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import kotlin.math.abs

/**
 * Lobby — desktop port of the Android LobbyScreen:
 * shared playback (link / local-file "shared" modes), sync engine, right-docked
 * chat with quick emojis + stickers, voice chat, users presence, alias dialog,
 * floating message notification, ready-sync, close/exit flows.
 */
@Composable
fun LobbyScreen(
    user: SessionUser,
    lobbyCode: String,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onExit: () -> Unit
) {
    val ws = remember { HamSocket.getInstance() }
    val voice = remember { VoiceRelay.getInstance() }
    val scope = rememberCoroutineScope()

    // ------- socket state -------
    val lobbyInfo by ws.lobbyInfo.collectAsState()
    val users by ws.users.collectAsState()
    val displayNames by ws.displayNames.collectAsState()
    val userIcons by ws.userIcons.collectAsState()
    val messages by ws.messages.collectAsState()
    val myUserId by ws.currentUserId.collectAsState()
    val isHost by ws.isHost.collectAsState()
    val lobbyClosed by ws.lobbyClosed.collectAsState()
    val currentVideoUrl by ws.currentVideoUrl.collectAsState()
    val currentMode by ws.currentPlaybackMode.collectAsState()
    val videoSyncState by ws.videoSyncState.collectAsState()
    val playbackSyncState by ws.playbackSyncState.collectAsState()
    val readyStatus by ws.readyStatus.collectAsState()
    val allUsersReady by ws.allUsersReady.collectAsState()
    val micEnabledUserIds by ws.micEnabledUserIds.collectAsState()
    val sharedFileName by ws.sharedFileName.collectAsState()
    val connectionError by ws.connectionState.collectAsState()

    // ------- local ui state -------
    var chatOpen by remember { mutableStateOf(!fullscreen) }
    LaunchedEffect(fullscreen) { if (fullscreen) chatOpen = false }
    var micOn by remember { mutableStateOf(false) }
    var currentTimeMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isPlayingLocal by remember { mutableStateOf(false) }
    var playerReady by remember { mutableStateOf(false) }
    var hasSentReady by remember { mutableStateOf(false) }
    var pendingPlayState by remember { mutableStateOf<Boolean?>(null) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0L) }
    var localFilePath by remember { mutableStateOf("") }
    var showAliasDialog by remember { mutableStateOf(true) }
    var aliasName by remember { mutableStateOf(user.name.ifBlank { user.username.ifBlank { "من" } }) }
    var aliasIcon by remember { mutableStateOf<String?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkInput by remember { mutableStateOf("") }
    var showVideoSettings by remember { mutableStateOf(false) }
    var tracksVersion by remember { mutableIntStateOf(0) }
    var playError by remember { mutableStateOf("") }
    var notifStamp by remember { mutableIntStateOf(0) }
    var notification by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    // holder for self-reference from engine event callbacks (created before engine)
    val engineHolder = remember { arrayOfNulls<VideoEngine>(1) }

    // ------- video engine (vlcj) -------
    val engine = remember {
        VideoEngine(
            onTimeChanged = { t, d -> currentTimeMs = t; durationMs = d },
            onPlayingChange = { playing ->
                isPlayingLocal = playing
                val e = engineHolder[0]
                if (e != null && !e.isSyncing && playerReady) {
                    val justReloaded = System.currentTimeMillis() - e.lastMediaLoadAt < 1500
                    if (!justReloaded || playing) {
                        ws.updateVideoState(e.currentTimeMs / 1000.0, playing)
                    }
                }
            },
            onMediaReady = {
                playerReady = true
                if (!hasSentReady) {
                    hasSentReady = true
                    ws.sendPlayerReady(lobbyCode)
                }
                if (pendingPlayState == true && ws.allUsersReady.value) {
                    pendingPlayState = null
                    engineHolder[0]?.play()
                }
            },
            onEnded = { /* end of media */ },
            onError = { msg -> playError = msg },
            onTracksChanged = { tracksVersion++ }
        ).also { engineHolder[0] = it }
    }

    DisposableEffect(Unit) {
        onDispose { engine.release() }
    }

    // voice: start relay on entry (mirrors initVoiceChat), stop on exit
    LaunchedEffect(Unit) {
        voice.start(lobbyCode, ws.currentUserId.value.ifBlank { user.userId })
    }
    DisposableEffect(Unit) {
        onDispose { voice.leaveCall() }
    }

    // ------- media loading (load PAUSED — no auto-play, like the app) -------
    val effectiveUrl = if (currentMode == "shared") localFilePath else currentVideoUrl
    LaunchedEffect(currentMode, effectiveUrl) {
        when (currentMode) {
            "webview", "aparat" -> Unit
            else -> {
                if (effectiveUrl.isNotBlank()) {
                    hasSentReady = false
                    pendingPlayState = null
                    playerReady = false
                    playError = ""
                    engine.loadPaused(effectiveUrl)
                } else {
                    engine.stop()
                    playerReady = false
                    hasSentReady = false
                    pendingPlayState = null
                }
            }
        }
    }

    // all members ready → start pending playback
    LaunchedEffect(allUsersReady) {
        if (allUsersReady && pendingPlayState == true) {
            pendingPlayState = null
            engine.play()
        }
    }

    // ------- incoming sync (play/pause + seek) -------
    LaunchedEffect(videoSyncState) {
        videoSyncState?.let { sync ->
            if (currentMode == "link" || currentMode == "shared") {
                engine.isSyncing = true
                val target = (sync.currentTime * 1000).toLong()
                if (sync.isSeek) {
                    engine.seekTo(target)
                } else if (sync.isPlaying && abs(engine.currentTimeMs - target) > 1500) {
                    engine.seekTo(target)
                }
                if (sync.isPlaying) engine.play() else engine.pause()
                delay(800)
                engine.isSyncing = false
            }
        }
    }

    // ------- late-join full playback sync -------
    LaunchedEffect(playbackSyncState) {
        playbackSyncState?.let { sync ->
            if (sync.mode == "link" || sync.mode == "shared") {
                engine.isSyncing = true
                if (sync.mode == "link" && !sync.videoUrl.isNullOrBlank() &&
                    currentVideoUrl != sync.videoUrl
                ) {
                    engine.loadPaused(sync.videoUrl)
                }
                val target = (sync.currentTime * 1000).toLong()
                if (abs(engine.currentTimeMs - target) > 1500) {
                    engine.seekTo(target)
                }
                if (sync.isPlaying) engine.play() else engine.pause()
                delay(800)
                engine.isSyncing = false
            }
        }
    }

    // ------- floating message notification + chime (when chat hidden) -------
    LaunchedEffect(messages.size) {
        val last = messages.lastOrNull()
        if (last != null && !last.isSystemMessage && last.userId != myUserId) {
            Res.playChime()
            notification = last.username to last.message
            val stamp = ++notifStamp
            delay(4500)
            if (stamp == notifStamp) notification = null
        }
    }

    // ------- exit confirm (host can close the lobby) -------
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("خروج از لابی", color = LightGrayText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isHost) "میزبان هستی — با خروج، لابی برای همه بسته می‌شود. مطمئنی؟"
                    else "از لابی خارج می‌شوی؟",
                    color = MediumGrayText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExitConfirm = false
                        if (isHost) {
                            voice.leaveCall()
                            ws.closeLobby()
                            ws.exitLobby()
                        } else {
                            voice.leaveCall()
                            ws.exitLobby()
                        }
                        onExit()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedAccent)
                ) { Text(if (isHost) "بستن لابی" else "خروج", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("ماندن") }
            }
        )
    }

    // ------- room closed -------
    if (lobbyClosed) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("لابی بسته شد", color = RedAccent, fontWeight = FontWeight.Bold) },
            text = { Text("میزبان این لابی را بسته است.", color = LightGrayText) },
            confirmButton = {
                Button(onClick = {
                    ws.disconnect()
                    onExit()
                }) { Text("باشه") }
            }
        )
    }

    // ------- alias dialog (first entry) -------
    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text("در این لابی با چه اسمی باشی؟", color = LightGrayText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    HamTextField(aliasName, "اسم نمایشی", { aliasName = it })
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LOBBY_ICONS.chunked(4).forEach { rowIcons ->
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowIcons.forEach { def ->
                                    AvatarPickCell(def, aliasIcon == def.id) {
                                        aliasIcon = if (aliasIcon == def.id) null else def.id
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ws.sendAlias(aliasName.ifBlank { "من" }, aliasIcon ?: "")
                        showAliasDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                ) { Text("ثبت", color = Color(0xFF10131A)) }
            }
        )
    }

    // ------- link dialog -------
    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("افزودن لینک فیلم", color = LightGrayText, fontWeight = FontWeight.Bold) },
            text = {
                HamTextField(linkInput, "لینک مستقیم فیلم (mp4/m3u8/...)", { linkInput = it })
            },
            confirmButton = {
                Button(
                    onClick = {
                        val link = linkInput.trim()
                        if (link.isNotBlank()) {
                            ws.sendVideoLink(link)
                            ws.updateVideoUrl(link)
                            ws.sendModeChange("link")
                        }
                        showLinkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                ) { Text("پخش برای همه", color = Color(0xFF10131A)) }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("لغو") }
            }
        )
    }

    // ------- video settings dialog (audio track / subtitle / speed) -------
    if (showVideoSettings) {
        VideoSettingsDialog(
            engine = engine,
            tracksVersion = tracksVersion,
            onDismiss = { showVideoSettings = false }
        )
    }

    // ================= LAYOUT (RTL: first child = visual RIGHT) =================
    Row(Modifier.fillMaxSize().background(AppBgGradient)) {
        // chat panel docked to the visual RIGHT side
        if (chatOpen) {
            ChatPanel(
                messages = messages,
                userIcons = userIcons,
                myUserId = myUserId,
                onClose = { chatOpen = false },
                onSend = { text -> ws.sendMessage(text, aliasName.ifBlank { "من" }) }
            )
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            // ---- header ----
            if (!fullscreen) {
                Row(
                    Modifier.fillMaxWidth().background(HeaderGrad)
                        .border(1.dp, CardStrokeColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // users chips (horizontal scroll)
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        users.take(8).forEach { u ->
                            val icon = userIcons[u.userId] ?: ""
                            Box(Modifier.padding(end = 6.dp)) {
                                UserChip(
                                    name = displayNames[u.userId] ?: u.displayName,
                                    iconId = icon,
                                    micOn = u.userId in micEnabledUserIds
                                )
                            }
                        }
                    }

                    // ready chip
                    readyStatus?.let { rs ->
                        Text(
                            "آماده: ${rs.readyCount}/${rs.totalCount}",
                            fontSize = 11.sp, color = GreenAccent,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    // lobby code + copy
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ChipDark)
                            .border(1.dp, ChipStrokeColor, RoundedCornerShape(8.dp))
                            .clickable {
                                try {
                                    val cb = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    cb.setContents(java.awt.datatransfer.StringSelection(lobbyCode), null)
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("کد: $lobbyCode", fontSize = 13.sp, color = YellowAccent, fontWeight = FontWeight.Bold)
                    }

                    // mic toggle
                    IconButton(onClick = {
                        val new = !micOn
                        micOn = new
                        voice.setMicrophoneEnabled(new)
                    }) {
                        Icon(
                            if (micOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = "میکروفون",
                            tint = if (micOn) GreenAccent else MediumGrayText
                        )
                    }

                    // chat toggle
                    IconButton(onClick = { chatOpen = !chatOpen }) {
                        Icon(
                            Icons.Filled.ChatBubble,
                            contentDescription = "گفتگو",
                            tint = if (chatOpen) YellowAccent else MediumGrayText
                        )
                    }

                    // exit (host gets the close-room option too)
                    IconButton(onClick = { showExitConfirm = true }) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "خروج", tint = RedAccent)
                    }
                }
            }

            // ---- floating message notification (visual LEFT edge = RTL TopEnd) ----
            AnimatedVisibility(
                visible = notification != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    Row(
                        Modifier
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NotifGrad)
                            .border(1.dp, ChipStrokeColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val n = notification
                        if (n != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.ChatBubble, contentDescription = null,
                                    tint = YellowAccent, modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${n.first}: ${n.second}",
                                    fontSize = 12.sp, color = LightGrayText,
                                    maxLines = 1, modifier = Modifier.widthIn(max = 420.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---- video area ----
            Box(
                Modifier.weight(1f).fillMaxWidth().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val mode = currentMode
                if ((mode == "link" || mode == "shared") && effectiveUrl.isNotBlank()) {
                    SwingPanel(
                        factory = { engine.component },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (mode == "webview" || mode == "aparat") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (mode == "aparat") "حالت آپارات" else "حالت وب",
                            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LightGrayText
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "این حالت روی دسکتاپ داخل خود برنامه پخش نمی‌شود.",
                            fontSize = 13.sp, color = MediumGrayText
                        )
                        if (currentVideoUrl.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        Desktop.getDesktop().browse(java.net.URI(currentVideoUrl))
                                    } catch (_: Exception) {}
                                }
                            ) { Text("باز کردن در مرورگر") }
                        }
                    }
                } else {
                    // no media — choose local file or link
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = MediumGrayText,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (mode == "shared")
                                (sharedFileName.ifBlank { "فایل محلی" }).ifBlank { "فایل محلی" }
                            else "فیلمی انتخاب نشده",
                            fontSize = 16.sp, color = LightGrayText, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (mode == "shared")
                                "فایل را از رایانه خودت انتخاب کن تا هم‌زمان ببینید"
                            else "یک فایل محلی بردار یا لینک بده",
                            fontSize = 12.sp, color = MediumGrayText
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val chooser = JFileChooser()
                                    val r = chooser.showOpenDialog(null)
                                    if (r == JFileChooser.APPROVE_OPTION) {
                                        val f = chooser.selectedFile
                                        localFilePath = f.absolutePath
                                        ws.sendSharedFileMode(f.name)
                                        ws.updateVideoUrl(localFilePath)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                elevation = null,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                modifier = Modifier.background(YellowGrad, RoundedCornerShape(12.dp))
                            ) { Text("انتخاب فایل محلی", color = Color(0xFF10131A), fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = { showLinkDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                elevation = null,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                modifier = Modifier.background(com.app.hamfilm.desktop.BlueGrad, RoundedCornerShape(12.dp))
                            ) { Text("افزودن لینک", color = Color(0xFF10131A), fontWeight = FontWeight.Bold) }
                        }
                        if (!playerReady && effectiveUrl.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            CircularProgressIndicator(Modifier.size(24.dp), color = YellowAccent, strokeWidth = 2.dp)
                        }
                    }
                }

                // play error toast
                if (playError.isNotBlank()) {
                    Box(Modifier.align(Alignment.TopCenter).padding(10.dp)) {
                        Text(
                            playError,
                            color = Color.White, fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedAccent)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // ---- controls bar (below the video — the timeline is never covered) ----
            Row(
                Modifier.fillMaxWidth().background(DarkCardBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(YellowGrad)
                        .clickable { if (isPlayingLocal) engine.pause() else engine.play() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlayingLocal) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "پخش/توقف",
                        tint = Color(0xFF10131A),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(formatTime(currentTimeMs), fontSize = 11.sp, color = LightGrayText, modifier = Modifier.width(48.dp))

                Slider(
                    value = if (durationMs > 0) {
                        (if (scrubbing) scrubValue.toFloat() else currentTimeMs.toFloat()) / durationMs.toFloat()
                    } else 0f,
                    onValueChange = {
                        scrubbing = true
                        scrubValue = (it * durationMs).toLong()
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0) {
                            engine.seekTo(scrubValue)
                            ws.seekVideo(scrubValue / 1000.0)
                        }
                        scrubbing = false
                    },
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = YellowAccent,
                        activeTrackColor = YellowAccent,
                        inactiveTrackColor = BorderGray
                    ),
                    modifier = Modifier.weight(1f)
                )

                Text(formatTime(durationMs), fontSize = 11.sp, color = MediumGrayText, modifier = Modifier.width(48.dp))

                // open local file
                IconButton(onClick = {
                    val chooser = JFileChooser()
                    val r = chooser.showOpenDialog(null)
                    if (r == JFileChooser.APPROVE_OPTION) {
                        val f = chooser.selectedFile
                        localFilePath = f.absolutePath
                        ws.sendSharedFileMode(f.name)
                        ws.updateVideoUrl(localFilePath)
                    }
                }) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = "انتخاب فایل محلی",
                        tint = MediumGrayText
                    )
                }

                // settings — full dialog with audio-track / subtitle / speed sections
                IconButton(onClick = { showVideoSettings = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "تنظیمات پخش", tint = MediumGrayText)
                }

                // fullscreen toggle
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = "تمام‌صفحه",
                        tint = MediumGrayText
                    )
                }
            }
        }
    }
}

@Composable
private fun UserChip(name: String, iconId: String, micOn: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ChipDark)
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AvatarOrRemote(iconId.ifBlank { null }, "", 24.dp)
            // online dot (everyone in the users list is connected)
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(GreenAccent)
                    .align(Alignment.BottomStart)
            )
            if (micOn) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(YellowAccent)
                        .align(Alignment.BottomEnd)
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(name, fontSize = 12.sp, color = LightGrayText, maxLines = 1)
    }
}

@Composable
private fun AvatarPickCell(def: AvatarDef, selected: Boolean, onClick: () -> Unit) {
    val bmp = Res.avatar(def.id)
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (selected) YellowAccent else ChipDark)
            .clickable { onClick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = def.label,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Text(def.label, fontSize = 11.sp, color = LightGrayText, textAlign = TextAlign.Center)
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
