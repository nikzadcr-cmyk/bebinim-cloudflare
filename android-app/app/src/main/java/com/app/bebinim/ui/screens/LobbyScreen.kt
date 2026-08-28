package com.app.bebinim.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.app.bebinim.data.websocket.ChatMessage
import com.app.bebinim.data.websocket.ConnectionState
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.GreenAccent
import com.app.bebinim.ui.theme.LightGrayText
import com.app.bebinim.ui.theme.MediumGrayText
import com.app.bebinim.ui.theme.RedAccent
import com.app.bebinim.ui.theme.SurfaceDark
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.LobbyViewModel

/**
 * Movie lobby — video modes: direct link / radio / webview / shared local file.
 * (archive mode removed by design)
 */
@Composable
fun LobbyScreen(
    navController: NavHostController,
    lobbyCode: String,
    lobbyType: String
) {
    val lobbyViewModel: LobbyViewModel = viewModel()
    val context = LocalContext.current

    val connectionState by lobbyViewModel.connectionState.collectAsState()
    val users by lobbyViewModel.users.collectAsState()
    val messages by lobbyViewModel.messages.collectAsState()
    val currentVideoUrl by lobbyViewModel.currentVideoUrl.collectAsState()
    val currentMode by lobbyViewModel.currentPlaybackMode.collectAsState()
    val videoSyncState by lobbyViewModel.videoSyncState.collectAsState()
    val playbackSyncState by lobbyViewModel.playbackSyncState.collectAsState()
    val isHost by lobbyViewModel.isHost.collectAsState()
    val lobbyClosed by lobbyViewModel.lobbyClosed.collectAsState()
    val allUsersReady by lobbyViewModel.allUsersReady.collectAsState()
    val readyStatus by lobbyViewModel.readyStatus.collectAsState()
    val isMicEnabled by lobbyViewModel.isMicEnabled.collectAsState()
    val sharedFileName by lobbyViewModel.sharedFileName.collectAsState()
    val joinSuccess by lobbyViewModel.joinSuccess.collectAsState()

    var linkInput by remember { mutableStateOf("") }
    var showModeDialog by remember { mutableStateOf(false) }
    var showUsersDialog by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(joinSuccess.isNotBlank()) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }

    // on first entry into an existing lobby ensure alias asked
    LaunchedEffect(joinSuccess) {
        if (joinSuccess.isNotBlank()) showAliasDialog = true
    }

    // ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // load video when URL/mode changes
    LaunchedEffect(currentVideoUrl, currentMode) {
        if ((currentMode == "link" || currentMode == "archive") && currentVideoUrl.isNotBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentVideoUrl))
            exoPlayer.prepare()
        }
    }

    // apply incoming sync (video)
    LaunchedEffect(videoSyncState) {
        videoSyncState?.let { sync ->
            if (currentVideoUrl.isNotBlank()) {
                exoPlayer.seekTo((sync.currentTime * 1000).toLong())
                exoPlayer.playWhenReady = sync.isPlaying
            }
        }
    }

    // apply late-join playback sync
    LaunchedEffect(playbackSyncState) {
        playbackSyncState?.let { sync ->
            if (!sync.videoUrl.isNullOrBlank() && sync.mode == "link") {
                exoPlayer.setMediaItem(MediaItem.fromUri(sync.videoUrl))
                exoPlayer.prepare()
            }
            exoPlayer.seekTo((sync.currentTime * 1000).toLong())
            exoPlayer.playWhenReady = sync.isPlaying
        }
    }

    // player event → sync others (host drives, but all send like the original)
    val playerListener = remember {
        object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lobbyViewModel.updateVideoState(exoPlayer.currentPosition / 1000.0, isPlaying)
            }
        }
    }
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(playerListener)
    }

    // voice chat init
    LaunchedEffect(joinSuccess) {
        if (joinSuccess.isNotBlank()) {
            lobbyViewModel.initVoiceChat()
        }
    }

    // lobby closed → go home
    LaunchedEffect(lobbyClosed) {
        if (lobbyClosed) {
            kotlinx.coroutines.delay(4000)
            navController.popBackStack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050C1A))
            .padding(bottom = 12.dp)
    ) {
        // ---------- header ----------
        Surface(
            color = SurfaceDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showModeDialog = true }) {
                    Icon(Icons.Filled.GridView, "انتخاب حالت پخش", tint = CyanAccent)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "کد لابی: $lobbyCode",
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WhiteText
                    )
                    Text(
                        when (currentMode) {
                            "radio" -> "در حال پخش رادیو موزیک"
                            "shared" -> "فایل مشترک: ${sharedFileName.ifBlank { "فایل محلی" }}"
                            "webview" -> "پخش WebView"
                            else -> "حالت لینک مستقیم"
                        },
                        fontSize = 12.sp,
                        color = if (currentMode == "radio") GreenAccent else MediumGrayText
                    )
                }
                IconButton(onClick = { showInviteDialog = true }) {
                    Icon(androidx.compose.ui.res.painterResource(com.app.bebinim.R.drawable.ic_invite), "دعوت", tint = YellowAccent, modifier = Modifier.size(22.dp))
                }
            }
        }

        // connection banner
        if (connectionState is ConnectionState.Error || connectionState is ConnectionState.Disconnected) {
            Text(
                "قطع ارتباط — در حال اتصال مجدد...",
                fontSize = 12.sp, color = RedAccent,
                modifier = Modifier.fillMaxWidth().background(RedAccent.copy(alpha = 0.1f)).padding(6.dp),
                textAlign = TextAlign.Center
            )
        }

        // ---------- link input ----------
        if (currentMode == "link") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = linkInput,
                    onValueChange = { linkInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("لینک مستقیم ویدیو را وارد کنید...", fontSize = 12.sp, color = MediumGrayText) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = CyanAccent.copy(alpha = 0.25f)
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (linkInput.isNotBlank()) {
                            lobbyViewModel.sendVideoLink(linkInput)
                            linkInput = ""
                        }
                    },
                    enabled = linkInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("ثبت", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ---------- player / radio / webview ----------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(Color.Black)
        ) {
            when (currentMode) {
                "radio" -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(androidx.compose.ui.res.painterResource(com.app.bebinim.R.drawable.ic_radio), null, tint = GreenAccent, modifier = Modifier.size(48.dp))
                        Text("شما در حال گوش کردن به رادیو موزیک هستید", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GreenAccent)
                    }
                }
                "shared" -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("📂 حالت فایل مشترک فعال شد", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WhiteText)
                        Text("فایل درخواستی: ${sharedFileName.ifBlank { "نامشخص" }}", fontSize = 12.sp, color = YellowAccent)
                        Text(
                            "لطفاً فایلی با همین نام (یا مشابه) را از گوشی خود انتخاب کنید تا پخش همزمان با سایرین داشته باشید.",
                            fontSize = 11.sp, color = MediumGrayText, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
                else -> {
                    // ExoPlayer
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (currentVideoUrl.isBlank()) {
                        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            Text("لینک ویدیو را وارد کنید تا پخش برای همه شروع شود", fontSize = 13.sp, color = MediumGrayText, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
                        }
                    }
                }
            }

            // ready overlay
            if (!allUsersReady && readyStatus != null && (readyStatus?.readyCount ?: 0) < (readyStatus?.totalCount ?: 1)) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanAccent, strokeWidth = 2.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "منتظر بقیه کاربران...  ${readyStatus?.readyCount ?: 0} / ${readyStatus?.totalCount ?: 0} آماده‌اند",
                            fontSize = 12.sp, color = WhiteText
                        )
                    }
                }
            }
        }

        // ---------- chat ----------
        ChatSection(
            messages = messages,
            currentUserId = lobbyViewModel.currentUserId.collectAsState().value,
            modifier = Modifier.weight(1f)
        )

        // message input
        var messageText by remember { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("پیام خود را بنویسید...", fontSize = 12.sp, color = MediumGrayText) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent.copy(alpha = 0.4f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0xFF14243C),
                    unfocusedContainerColor = Color(0xFF14243C)
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { lobbyViewModel.sendMicToggle(!isMicEnabled) },
                modifier = Modifier
                    .size(42.dp)
                    .background(if (isMicEnabled) GreenAccent.copy(alpha = 0.2f) else Color(0xFF14243C), CircleShape)
            ) {
                Icon(
                    if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = if (isMicEnabled) "خاموش کردن میکروفون" else "روشن کردن میکروفون",
                    tint = if (isMicEnabled) GreenAccent else RedAccent
                )
            }
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        lobbyViewModel.sendMessage(messageText)
                        messageText = ""
                    }
                }
            ) {
                Icon(Icons.Filled.Send, "ارسال", tint = CyanAccent)
            }
        }

        // ---------- users / management ----------
        UsersSection(users)

        ManagementButtonsGrid(
            onHelpClick = { },
            onExitClick = { showExitDialog = true },
            onCloseLobbyClick = { showCloseDialog = true },
            isHost = isHost,
            onUsersClick = { showUsersDialog = true }
        )
    }

    // ---------------- dialogs ----------------
    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color(0xFF0E1928),
            title = { Text("خوش آمدید! 👋", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = {
                Column {
                    Text("برای شروع، نام نمایشی خود را وارد کنید", fontSize = 15.sp, color = LightGrayText)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        placeholder = { Text("مثلاً: علی", color = MediumGrayText) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanAccent)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (aliasInput.isNotBlank()) {
                            lobbyViewModel.sendAlias(aliasInput)
                            showAliasDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) { Text("ثبت", color = Color.White) }
            }
        )
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("دعوت دوستان 🎉", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = { Text("کد لابی رو با دوستات به اشتراک بذار!\n\nکد: $lobbyCode", fontSize = 14.sp, color = LightGrayText) },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Lobby Code", lobbyCode))
                        android.widget.Toast.makeText(context, "کد لابی کپی شد! ✅", android.widget.Toast.LENGTH_SHORT).show()
                        showInviteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                ) { Text("کپی کد", color = Color(0xFF050C1A)) }
            },
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) { Text("بستن", color = CyanAccent) }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("خروج از لابی", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = { Text("آیا می‌خواهید از لابی خارج شوید؟", fontSize = 15.sp, color = LightGrayText) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    lobbyViewModel.exitLobby()
                    navController.popBackStack()
                }) { Text("بله، خروج", color = RedAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("ماندن", color = CyanAccent) }
            }
        )
    }

    if (showCloseDialog && isHost) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("بستن لابی", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = { Text("آیا مطمئن هستید که می‌خواهید لابی را ببندید؟\nهمه کاربران از لابی خارج می‌شوند.", fontSize = 14.sp, color = LightGrayText) },
            confirmButton = {
                TextButton(onClick = {
                    showCloseDialog = false
                    lobbyViewModel.closeLobby()
                    navController.popBackStack()
                }) { Text("بستن لابی", color = RedAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false }) { Text("انصراف", color = CyanAccent) }
            }
        )
    }

    if (showUsersDialog) {
        AlertDialog(
            onDismissRequest = { showUsersDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("کاربران آنلاین (${users.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = {
                Column {
                    if (users.isEmpty()) Text("در حال بارگذاری...", fontSize = 13.sp, color = MediumGrayText)
                    users.forEach { user ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            if (user.isHost) {
                                Icon(Icons.Filled.Star, "میزبان", tint = YellowAccent, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(user.displayName, fontSize = 14.sp, color = WhiteText)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUsersDialog = false }) { Text("بستن", color = CyanAccent) }
            }
        )
    }

    com.app.bebinim.ui.components.LobbyClosedDialog(
        lobbyClosed = lobbyClosed,
        onTimeout = { navController.popBackStack() }
    )
}

@Composable
private fun ChatSection(messages: List<ChatMessage>, currentUserId: String, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (messages.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("هنوز پیامی ارسال نشده!", fontSize = 13.sp, color = MediumGrayText)
                    Text("اولین نفر باشید که پیام می‌فرستد 💬", fontSize = 12.sp, color = MediumGrayText)
                }
            }
        }
        items(messages) { message ->
            MessageItem(message, currentUserId)
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage, currentUserId: String) {
    val isOwn = message.userId == currentUserId
    if (message.isSystemMessage) {
        Text(
            message.message,
            fontSize = 11.sp,
            color = Color(0xFFB9C2D0),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            if (!isOwn) {
                Text(message.username, fontSize = 11.sp, color = Color(0xFF9BA8BC), fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .background(
                        if (isOwn) Color(0xFF1E5AA8) else Color(0xFF22314A),
                        RoundedCornerShape(16.dp, 16.dp, if (isOwn) 4.dp else 16.dp, if (isOwn) 16.dp else 4.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(message.message, fontSize = 14.sp, color = WhiteText)
            }
            Text(
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp)),
                fontSize = 10.sp, color = WhiteText.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun UsersSection(users: List<com.app.bebinim.data.websocket.LobbyUser>) {
    Surface(color = SurfaceDark, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.People, null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("کاربران آنلاین (${users.size})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WhiteText)
            }
            if (users.isEmpty()) {
                Text("در حال بارگذاری...", fontSize = 12.sp, color = MediumGrayText, modifier = Modifier.padding(top = 6.dp))
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    users.take(5).forEach { user ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (user.isHost) CyanAccent else Color(0xFF1A2537)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (user.isHost) {
                                    Icon(Icons.Filled.Star, "میزبان", tint = WhiteText, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(user.displayName, fontSize = 11.sp, color = WhiteText)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementButtonsGrid(
    onHelpClick: () -> Unit,
    onExitClick: () -> Unit,
    onCloseLobbyClick: () -> Unit,
    onUsersClick: () -> Unit,
    isHost: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ManagementButton("کاربران", com.app.bebinim.R.drawable.ic_users, Color(0xFF4A9EFF), Modifier.weight(1f), onUsersClick)
        ManagementButton("راهنما", com.app.bebinim.R.drawable.ic_help, Color(0xFF3B82F6), Modifier.weight(1f), onHelpClick)
        ManagementButton("خروج", com.app.bebinim.R.drawable.ic_exit, Color(0xFF718096), Modifier.weight(1f), onExitClick)
        if (isHost) {
            ManagementButton("بستن لابی", com.app.bebinim.R.drawable.ic_delete, RedAccent, Modifier.weight(1f), onCloseLobbyClick, isDanger = true)
        }
    }
}

@Composable
private fun ManagementButton(
    label: String,
    iconRes: Int,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    isDanger: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isDanger) RedAccent.copy(alpha = 0.12f) else Color(0xFF14243C),
        modifier = modifier.height(46.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResourceCompat(iconRes), null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
        }
    }
}

@Composable
private fun painterResourceCompat(res: Int): androidx.compose.ui.graphics.painter.Painter =
    androidx.compose.ui.res.painterResource(res)
