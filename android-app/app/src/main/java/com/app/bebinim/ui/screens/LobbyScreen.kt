package com.app.bebinim.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.TypedValue
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.app.bebinim.R
import com.app.bebinim.data.model.StickerCatalog
import com.app.bebinim.data.websocket.ChatMessage
import com.app.bebinim.data.websocket.ConnectionState
import com.app.bebinim.data.websocket.LobbyUser
import com.app.bebinim.data.websocket.SubtitleInfo
import com.app.bebinim.ui.components.ChatButtonGradient
import com.app.bebinim.ui.components.ControlButtonsRow
import com.app.bebinim.ui.components.DangerButtonGradient
import com.app.bebinim.ui.components.EmojiBlastOverlay
import com.app.bebinim.ui.components.EmojiStickerPickerPanel
import com.app.bebinim.ui.components.FloatingMicButton
import com.app.bebinim.ui.components.ImmersiveChatPanel
import com.app.bebinim.ui.components.LobbyClosedDialog
import com.app.bebinim.ui.components.LobbyHeader
import com.app.bebinim.ui.components.LobbyOnboardingGuide
import com.app.bebinim.ui.components.ManagementButtonsGrid
import com.app.bebinim.ui.components.movieLobbyGuideSteps
import com.app.bebinim.ui.components.hasSeenLobbyGuide
import com.app.bebinim.ui.components.setLobbyGuideShown
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.GreenAccent
import com.app.bebinim.ui.theme.MediumGrayText
import com.app.bebinim.ui.theme.RedAccent
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.LobbyViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private const val LOBBY_ICON_BASE_URL = "https://app.bebinim.me/hw-assets/images/"
private const val RADIO_URL = "https://streams.ilovemusic.de/iloveradio1.mp3"
private val EMOJIS = listOf("❤️", "😂", "🔥", "👍", "🎉")

data class LobbyIconDef(val id: String, val label: String, val placeholderColor: Color)
val LOBBY_ICONS = listOf(
    LobbyIconDef("foxy", "فاکسی", Color(0xFFFF7043)),
    LobbyIconDef("shipy", "شیپی", Color(0xFF42A5F5)),
    LobbyIconDef("tems", "تمس", Color(0xFF66BB6A)),
    LobbyIconDef("meymo", "میمو", Color(0xFFAB47BC))
)

/**
 * Movie lobby room — full recreation of the original Bebinim lobby:
 * header with mode tabs, ExoPlayer with subtitle support, radio/webview/shared
 * modes, immersive chat with stickers & emoji blasts, voice chat, ready-sync,
 * onboarding guide and all management dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@UnstableApi
@Composable
fun LobbyScreen(
    navController: NavHostController,
    lobbyCode: String,
    lobbyType: String
) {
    val lobbyViewModel: LobbyViewModel = viewModel()
    val context = LocalContext.current

    // ---------------- streams ----------------
    val connectionState by lobbyViewModel.connectionState.collectAsState()
    val users by lobbyViewModel.users.collectAsState()
    val messages by lobbyViewModel.messages.collectAsState()
    val currentVideoUrl by lobbyViewModel.currentVideoUrl.collectAsState()
    val currentMode by lobbyViewModel.currentPlaybackMode.collectAsState()
    val videoSyncState by lobbyViewModel.videoSyncState.collectAsState()
    val playbackSyncState by lobbyViewModel.playbackSyncState.collectAsState()
    val subtitleInfo by lobbyViewModel.subtitleInfo.collectAsState()
    val displayNames by lobbyViewModel.displayNames.collectAsState()
    val userIcons by lobbyViewModel.userIcons.collectAsState()
    val isHost by lobbyViewModel.isHost.collectAsState()
    val lobbyClosed by lobbyViewModel.lobbyClosed.collectAsState()
    val allUsersReady by lobbyViewModel.allUsersReady.collectAsState()
    val readyStatus by lobbyViewModel.readyStatus.collectAsState()
    val isMicEnabled by lobbyViewModel.isMicEnabled.collectAsState()
    val micEnabledUsers by lobbyViewModel.micEnabledUserIds.collectAsState()
    val sharedFileName by lobbyViewModel.sharedFileName.collectAsState()
    val joinSuccess by lobbyViewModel.joinSuccess.collectAsState()
    val currentUserId by lobbyViewModel.currentUserId.collectAsState()

    // ---------------- local ui state (original set) ----------------
    var showImmersiveChat by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showPlaybackModeDialog by remember { mutableStateOf(false) }
    var showWebViewUrlDialog by remember { mutableStateOf(false) }
    var webViewUrlInput by remember { mutableStateOf("") }
    var showSharedModeDialog by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showVideoSettingsSheet by remember { mutableStateOf(false) }
    var showUsersDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    var showCloseDialog by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(joinSuccess.isNotBlank()) }
    var showOnboardingGuide by remember { mutableStateOf(false) }
    var aliasInput by remember { mutableStateOf("") }
    var selectedIconId by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var videoLinkText by remember { mutableStateOf("") }
    var subtitleTextColor by remember { mutableStateOf(Color.White) }
    var subtitleSizeSp by remember { mutableStateOf(18f) }
    var floatingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var lastSeenMessageCount by remember { mutableIntStateOf(0) }
    var emojiBlasts by remember { mutableStateOf(listOf<Pair<String, Long>>()) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoFileName by remember { mutableStateOf("") }
    var selectedSubtitleUri by remember { mutableStateOf<Uri?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var currentCueText by remember { mutableStateOf("") }
    var playerReady by remember { mutableStateOf(false) }
    var pendingPlayState by remember { mutableStateOf<Boolean?>(null) }
    var hasSentReady by remember { mutableStateOf(false) }
    var micPermissionAsked by remember { mutableStateOf(false) }

    LaunchedEffect(joinSuccess) {
        if (joinSuccess.isNotBlank()) showAliasDialog = true
    }

    // onboarding on first visit
    LaunchedEffect(joinSuccess) {
        if (joinSuccess.isNotBlank() && !hasSeenLobbyGuide(context)) {
            delay(600)
            showOnboardingGuide = true
        }
    }

    // restore saved alias/icon for this lobby
    LaunchedEffect(lobbyCode) {
        val prefs = context.getSharedPreferences("lobby_prefs", Context.MODE_PRIVATE)
        aliasInput = prefs.getString("lobby_alias_$lobbyCode", "") ?: ""
        selectedIconId = prefs.getString("lobby_icon_$lobbyCode", "") ?: ""
        if (aliasInput.isNotBlank()) {
            lobbyViewModel.sendAlias(aliasInput, selectedIconId)
        }
    }

    // ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = false }
    }
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            lobbyViewModel.leaveLobbySilent()
        }
    }

    // radio URL auto-play detection (same heuristic as the original)
    fun isRadioUrl(url: String): Boolean =
        url.contains("ilovemusic", true) || url.contains("radio", true) || url.contains("stream", true)

    var shouldAutoPlay by remember { mutableStateOf(false) }

    // load media whenever url / mode / subtitle changes (original $9$1 logic)
    LaunchedEffect(currentVideoUrl, currentMode, subtitleInfo, selectedVideoUri) {
        when (currentMode) {
            "shared" -> {
                val uri = selectedVideoUri
                if (uri != null) {
                    val item = MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(selectedVideoFileName).build())
                        .setSubtitleConfigurations(buildSubtitleConfigs(subtitleInfo, selectedSubtitleUri, context))
                        .build()
                    exoPlayer.setMediaItem(item)
                    exoPlayer.prepare()
                }
            }
            "radio", "webview", "aparat" -> Unit
            else -> {
                // link mode
                if (currentVideoUrl.isNotBlank()) {
                    shouldAutoPlay = isRadioUrl(currentVideoUrl)
                    val builder = MediaItem.Builder().setUri(currentVideoUrl)
                    val subs = buildSubtitleConfigs(subtitleInfo, selectedSubtitleUri, context)
                    if (subs.isNotEmpty()) builder.setSubtitleConfigurations(subs)
                    exoPlayer.setMediaItem(builder.build())
                    exoPlayer.prepare()
                    if (shouldAutoPlay) exoPlayer.playWhenReady = true
                } else {
                    exoPlayer.stop()
                    exoPlayer.clearMediaItems()
                    playerReady = false
                    hasSentReady = false
                }
            }
        }
    }

    // apply incoming play/pause + seek sync from others
    LaunchedEffect(videoSyncState) {
        videoSyncState?.let { sync ->
            if (currentMode == "link" || currentMode == "shared") {
                exoPlayer.seekTo((sync.currentTime * 1000).toLong())
                exoPlayer.playWhenReady = sync.isPlaying
            }
        }
    }

    // late-join full playback sync
    LaunchedEffect(playbackSyncState) {
        playbackSyncState?.let { sync ->
            if (sync.mode == "link" && !sync.videoUrl.isNullOrBlank()) {
                exoPlayer.setMediaItem(MediaItem.Builder().setUri(sync.videoUrl).build())
                exoPlayer.prepare()
            }
            if (sync.mode == "link" || sync.mode == "shared") {
                exoPlayer.seekTo((sync.currentTime * 1000).toLong())
                exoPlayer.playWhenReady = sync.isPlaying
            }
        }
    }

    // player event listener — sync engine (original state machine)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        playerReady = true
                        if (!hasSentReady) {
                            hasSentReady = true
                            lobbyViewModel.sendPlayerReady(lobbyCode)
                        }
                        pendingPlayState?.let { pending ->
                            exoPlayer.playWhenReady = pending
                            pendingPlayState = null
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        playerReady = false
                    }
                    Player.STATE_IDLE, Player.STATE_ENDED -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (exoPlayer.playbackState == Player.STATE_READY) {
                    lobbyViewModel.updateVideoState(
                        exoPlayer.currentPosition / 1000.0, isPlaying
                    )
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                    exoPlayer.playbackState == Player.STATE_READY
                ) {
                    lobbyViewModel.seekVideo(exoPlayer.currentPosition / 1000.0)
                }
            }

            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                val text = cueGroup.cues.firstOrNull()?.text
                currentCueText = text?.toString() ?: ""
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // mic permission
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) lobbyViewModel.sendMicToggle(true)
    }

    // video file picker (shared mode) — original shows a confirm dialog first
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: "فایل محلی"
            showSharedModeDialog = name to uri
        }
    }

    // subtitle file picker (VTT / SRT only — like the original)
    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(context, uri) ?: ""
            val lower = name.lowercase()
            if (lower.endsWith(".vtt") || lower.endsWith(".srt")) {
                val cached = copySubtitleToCache(context, uri, name.ifBlank { "subtitle.vtt" })
                if (cached != null) {
                    selectedSubtitleUri = Uri.fromFile(cached)
                    lobbyViewModel.sendSubtitle(Uri.fromFile(cached).toString())
                }
            } else {
                android.widget.Toast.makeText(
                    context, "❌ فقط فایل‌های زیرنویس VTT و SRT قابل قبول هستند",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // lobby closed → back after 4s (original behavior)
    LaunchedEffect(lobbyClosed) {
        if (lobbyClosed) {
            delay(4000)
            navController.popBackStack()
        }
    }

    // new message → floating notification + unread badge
    LaunchedEffect(messages.size, showImmersiveChat) {
        if (showImmersiveChat) {
            lastSeenMessageCount = messages.size
        } else if (messages.isNotEmpty() && messages.size > lastSeenMessageCount) {
            val last = messages.last()
            if (last.userId != currentUserId) {
                floatingMessage = last
                delay(3200)
                if (floatingMessage == last) floatingMessage = null
            } else {
                lastSeenMessageCount = messages.size
            }
        }
    }
    LaunchedEffect(showImmersiveChat) {
        if (showImmersiveChat) lastSeenMessageCount = messages.size
    }

    val unreadCount = (messages.size - lastSeenMessageCount).coerceAtLeast(0)

    // emoji blast auto-cleanup
    LaunchedEffect(emojiBlasts.size) {
        if (emojiBlasts.isNotEmpty()) {
            delay(2200)
            val cutoff = System.currentTimeMillis() - 2000
            emojiBlasts = emojiBlasts.filter { it.second > cutoff }
        }
    }

    fun fireEmojiBlast(emoji: String) {
        emojiBlasts = emojiBlasts + (emoji to System.currentTimeMillis())
    }

    // ---------------- layout ----------------
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF050C1A))
            .statusBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            LobbyHeader(
                videoLink = videoLinkText,
                onVideoLinkChange = { videoLinkText = it },
                onSubmit = {
                    if (videoLinkText.isNotBlank()) {
                        lobbyViewModel.sendVideoLink(videoLinkText.trim())
                        videoLinkText = ""
                    }
                },
                currentMode = currentMode,
                onLinkTabClick = { lobbyViewModel.sendModeChange("link") },
                onRadioTabClick = { lobbyViewModel.sendRadioMode(RADIO_URL) },
                onSharedTabClick = { videoPicker.launch("video/*") },
                onSettingsClick = { showVideoSettingsSheet = true },
                onModeClick = { showPlaybackModeDialog = true }
            )

            // connection banner
            if (connectionState is ConnectionState.Error ||
                connectionState is ConnectionState.Disconnected
            ) {
                Text(
                    "قطع ارتباط — در حال اتصال مجدد...",
                    fontSize = 11.sp, color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RedAccent.copy(alpha = 0.85f))
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center
                )
            }

            // player area
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                when (currentMode) {
                    "radio" -> RadioPlayerPlaceholder()
                    "shared" -> SharedModePlaceholder(
                        sharedFileName = sharedFileName,
                        hasLocalFile = selectedVideoUri != null,
                        onPickFile = { videoPicker.launch("video/*") },
                        playerContent = {
                            PlayerSurface(exoPlayer, showController = true)
                        }
                    )
                    "webview", "aparat" -> WebViewPlayer(url = currentVideoUrl)
                    else -> {
                        if (currentVideoUrl.isNotBlank()) {
                            PlayerSurface(exoPlayer, showController = true)
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { showPlaybackModeDialog = true },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_movie), null,
                                    tint = MediumGrayText, modifier = Modifier.size(42.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "ویدیو در حال پخش",
                                    fontSize = 13.sp, color = MediumGrayText
                                )
                                Text(
                                    "برای انتخاب حالت پخش کلیک کنید (لینک / رادیو / فایل / وب‌ویو)",
                                    fontSize = 11.sp, color = CyanAccent
                                )
                            }
                        }
                    }
                }

                // custom subtitle overlay (size + color configurable, like the original)
                if (currentCueText.isNotBlank() && currentMode != "webview" && currentMode != "aparat") {
                    Text(
                        currentCueText,
                        fontSize = subtitleSizeSp.sp,
                        color = subtitleTextColor,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = (subtitleSizeSp * 1.25f).sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // buffering / waiting-for-ready overlay
                val showReadyOverlay = !allUsersReady && readyStatus != null &&
                    (readyStatus?.readyCount ?: 0) < (readyStatus?.totalCount ?: 1)
                if (showReadyOverlay) {
                    Surface2(
                        Modifier.align(Alignment.Center)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = CyanAccent, strokeWidth = 2.dp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "منتظر بقیه کاربران... ${readyStatus?.readyCount ?: 0} / ${readyStatus?.totalCount ?: 0} آماده‌اند",
                                fontSize = 12.sp, color = WhiteText
                            )
                            TextButton(onClick = {
                                lobbyViewModel.sendPlayerReady(lobbyCode)
                            }) { Text("شروع بدون انتظار", fontSize = 11.sp, color = CyanAccent) }
                        }
                    }
                }
            }

            // users chips row
            UsersChipsRow(
                users = users,
                userIcons = userIcons,
                micEnabledUsers = micEnabledUsers,
                onUsersClick = { showUsersDialog = true }
            )

            // chat list
            ChatMessagesList(
                messages = messages,
                currentUserId = currentUserId,
                userIcons = userIcons,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // sticker picker (compact, above input)
            AnimatedVisibility(visible = showEmojiPicker) {
                EmojiStickerPickerPanel(
                    onEmojiSelected = { emoji ->
                        lobbyViewModel.sendMessage(emoji)
                        fireEmojiBlast(emoji)
                        showEmojiPicker = false
                    },
                    onStickerSelected = { fileName ->
                        lobbyViewModel.sendMessage(StickerCatalog.STICKER_PREFIX + fileName)
                        showEmojiPicker = false
                    }
                )
            }

            // message input
            MessageInputRow(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onStickerClick = { showEmojiPicker = !showEmojiPicker },
                onSendClick = {
                    if (messageText.isNotBlank()) {
                        lobbyViewModel.sendMessage(messageText.trim())
                        messageText = ""
                    }
                },
                isMicEnabled = isMicEnabled,
                onMicClick = {
                    if (isMicEnabled) {
                        lobbyViewModel.sendMicToggle(false)
                    } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        lobbyViewModel.sendMicToggle(true)
                    } else if (!micPermissionAsked) {
                        micPermissionAsked = true
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
            )

            // main control buttons
            ControlButtonsRow(
                onChatClick = {
                    showImmersiveChat = true
                    lastSeenMessageCount = messages.size
                },
                onUsersClick = { showUsersDialog = true },
                onInviteClick = { showInviteDialog = true },
                onMicClick = {
                    if (isMicEnabled) {
                        lobbyViewModel.sendMicToggle(false)
                    } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        lobbyViewModel.sendMicToggle(true)
                    } else if (!micPermissionAsked) {
                        micPermissionAsked = true
                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                micEnabled = isMicEnabled,
                unreadCount = if (unreadCount > 0) unreadCount else 0
            )

            Spacer(Modifier.height(8.dp))

            // management buttons
            ManagementButtonsGrid(
                onHelpClick = { showHelpDialog = true },
                onExitClick = { showExitConfirmDialog = true },
                onCloseLobbyClick = { showCloseDialog = true },
                isHost = isHost
            )
        }

        // ---- overlays ----
        EmojiBlastOverlay(emojiBlasts, Modifier.matchParentSize())

        AnimatedVisibility(
            visible = floatingMessage != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            floatingMessage?.let { msg ->
                FloatingMessageNotification(
                    message = msg,
                    iconId = userIcons[msg.userId],
                    onClick = { showImmersiveChat = true },
                    onDismiss = { floatingMessage = null }
                )
            }
        }

        AnimatedVisibility(visible = showImmersiveChat, enter = fadeIn(), exit = fadeOut()) {
            ImmersiveChatPanel(
                messages = messages,
                currentUserId = currentUserId,
                displayNames = displayNames,
                messageText = messageText,
                onMessageTextChange = { messageText = it },
                onSendMessage = {
                    if (messageText.isNotBlank()) {
                        lobbyViewModel.sendMessage(messageText.trim())
                        messageText = ""
                    }
                },
                onClose = {
                    showImmersiveChat = false
                    lastSeenMessageCount = messages.size
                },
                onReactionSend = { emoji ->
                    lobbyViewModel.sendMessage(emoji)
                    fireEmojiBlast(emoji)
                },
                onStickerSend = { stickerMsg ->
                    lobbyViewModel.sendMessage(stickerMsg)
                }
            )
        }

        // fullscreen player overlay
        if (isFullscreen) {
            FullscreenPlayerOverlay(
                exoPlayer = exoPlayer,
                currentCueText = currentCueText,
                subtitleSizeSp = subtitleSizeSp,
                subtitleTextColor = subtitleTextColor,
                usersCount = users.size,
                micEnabled = isMicEnabled,
                onToggleMic = {
                    if (isMicEnabled) lobbyViewModel.sendMicToggle(false)
                    else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) lobbyViewModel.sendMicToggle(true)
                    else micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                onOpenChat = { showImmersiveChat = true },
                onExit = { isFullscreen = false }
            )
        }

        if (showOnboardingGuide) {
            LobbyOnboardingGuide(
                steps = movieLobbyGuideSteps(),
                onDismiss = {
                    showOnboardingGuide = false
                    setLobbyGuideShown(context)
                }
            )
        }
    }

    // ---------------- dialogs ----------------
    if (showAliasDialog) {
        AliasWelcomeDialog(
            aliasInput = aliasInput,
            onAliasChange = { aliasInput = it },
            selectedIconId = selectedIconId,
            onIconSelect = { selectedIconId = it },
            onConfirm = {
                val name = aliasInput.trim().ifBlank { "کاربر${Random.nextInt(1000, 9999)}" }
                aliasInput = name
                lobbyViewModel.sendAlias(name, selectedIconId)
                context.getSharedPreferences("lobby_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("lobby_alias_$lobbyCode", name)
                    .putString("lobby_icon_$lobbyCode", selectedIconId)
                    .apply()
                showAliasDialog = false
            },
            onSkip = { showAliasDialog = false }
        )
    }

    if (showPlaybackModeDialog) {
        PlaybackModeDialog(
            currentMode = currentMode,
            onDismiss = { showPlaybackModeDialog = false },
            onLink = {
                lobbyViewModel.sendModeChange("link")
                showPlaybackModeDialog = false
            },
            onRadio = {
                lobbyViewModel.sendRadioMode(RADIO_URL)
                showPlaybackModeDialog = false
            },
            onShared = {
                showPlaybackModeDialog = false
                videoPicker.launch("video/*")
            },
            onWebView = {
                showPlaybackModeDialog = false
                showWebViewUrlDialog = true
            }
        )
    }

    if (showWebViewUrlDialog) {
        AlertDialog(
            onDismissRequest = { showWebViewUrlDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("🌐 پخش WebView / iframe", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = {
                Column {
                    Text("آدرس صفحه وب (iframe/embed) را وارد کنید", fontSize = 13.sp, color = Color(0xFFB9C2D0))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = webViewUrlInput,
                        onValueChange = { webViewUrlInput = it },
                        placeholder = { Text("https://...", fontSize = 13.sp, color = MediumGrayText) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = CyanAccent.copy(alpha = 0.25f)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (webViewUrlInput.isNotBlank()) {
                            lobbyViewModel.sendWebViewMode(webViewUrlInput.trim())
                        }
                        showWebViewUrlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) { Text("ثبت", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showWebViewUrlDialog = false }) { Text("انصراف", color = CyanAccent) }
            }
        )
    }

    showSharedModeDialog?.let { (name, uri) ->
        AlertDialog(
            onDismissRequest = { showSharedModeDialog = null },
            containerColor = Color(0xFF0E1928),
            title = { Text("📂 حالت فایل مشترک", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = {
                Text(
                    "✅ فایل '$name' انتخاب شد. سایرین باید فایل مشابه را انتخاب کنند.\n\n💡 توجه: فایل شما باید duration مشابه با فایل سایر کاربران داشته باشد.",
                    fontSize = 13.sp, color = Color(0xFFB9C2D0)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedVideoUri = uri
                        selectedVideoFileName = name
                        lobbyViewModel.sendSharedFileMode(name)
                        showSharedModeDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                ) { Text("تایید", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showSharedModeDialog = null }) { Text("انصراف", color = CyanAccent) }
            }
        )
    }

    if (showSubtitleDialog) {
        SubtitleDialog(
            currentUrl = subtitleInfo?.url ?: "",
            onDismiss = { showSubtitleDialog = false },
            onPickFile = {
                showSubtitleDialog = false
                subtitlePicker.launch("*/*")
            },
            onRemove = {
                selectedSubtitleUri = null
                lobbyViewModel.sendSubtitle("")
                showSubtitleDialog = false
            }
        )
    }

    if (showVideoSettingsSheet) {
        VideoSettingsSheet(
            exoPlayer = exoPlayer,
            hasSubtitle = !subtitleInfo?.url.isNullOrBlank(),
            subtitleColor = subtitleTextColor,
            subtitleSize = subtitleSizeSp,
            onSubtitleClick = {
                showVideoSettingsSheet = false
                showSubtitleDialog = true
            },
            onSubtitleColorChange = { subtitleTextColor = it },
            onSubtitleSizeChange = { subtitleSizeSp = it },
            onDismiss = { showVideoSettingsSheet = false }
        )
    }

    if (showUsersDialog) {
        UsersDialog(users = users, userIcons = userIcons, onDismiss = { showUsersDialog = false })
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("دعوت دوستان 🎉", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("کد لابی رو با دوستات به اشتراک بذار!", fontSize = 13.sp, color = Color(0xFFB9C2D0))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        lobbyCode,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = YellowAccent,
                        letterSpacing = 4.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Lobby Code", lobbyCode))
                        android.widget.Toast.makeText(context, "✅ کد لابی کپی شد!", android.widget.Toast.LENGTH_SHORT).show()
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

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("خروج از لابی", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = { Text("آیا می‌خواهید از لابی خارج شوید؟", fontSize = 14.sp, color = Color(0xFFB9C2D0)) },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmDialog = false
                    lobbyViewModel.exitLobby()
                    navController.popBackStack()
                }) { Text("بله، خروج", color = RedAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) { Text("ماندن", color = CyanAccent) }
            }
        )
    }

    if (showCloseDialog && isHost) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("بستن لابی", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = { Text("آیا مطمئن هستید که می‌خواهید لابی را ببندید؟\nهمه کاربران از لابی خارج می‌شوند.", fontSize = 14.sp, color = Color(0xFFB9C2D0)) },
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

    if (showHelpDialog) {
        HelpDialog(
            onOpenArticles = {
                showHelpDialog = false
                runCatching {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://bebinim.me/mag/"))
                    context.startActivity(intent)
                }
            },
            onDismiss = { showHelpDialog = false }
        )
    }

    LobbyClosedDialog(lobbyClosed = lobbyClosed, onTimeout = { navController.popBackStack() })
}

// ================================================================
// helpers — file handling
// ================================================================

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
}

private fun copySubtitleToCache(context: Context, uri: Uri, name: String): File? {
    return runCatching {
        val safeName = "subtitle_" + System.currentTimeMillis() + "_" +
            (name.substringAfterLast('/').ifBlank { "sub.vtt" })
        val out = File(context.cacheDir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: return null
        out
    }.getOrNull()
}

private fun buildSubtitleConfigs(
    subtitleInfo: SubtitleInfo?,
    selectedSubtitleUri: Uri?,
    context: Context
): List<MediaItem.SubtitleConfiguration> {
    val uri = selectedSubtitleUri ?: return emptyList()
    val file = File(uri.path ?: "")
    val isSrt = file.name.lowercase().endsWith(".srt")
    val mime = if (isSrt) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT
    return listOf(
        MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLanguage(subtitleInfo?.language ?: "fa")
            .setLabel(subtitleInfo?.label ?: "فارسی")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    )
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

// ================================================================
// player surfaces
// ================================================================

@UnstableApi
@Composable
private fun PlayerSurface(exoPlayer: ExoPlayer, showController: Boolean) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = exoPlayer
                useController = showController
                setShowSubtitleButton(true)
                subtitleView?.visibility = android.view.View.GONE
            }
        },
        update = { view -> view.useController = showController },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun RadioPlayerPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D1F17), Color(0xFF050C1A)))
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val pulse = rememberInfiniteTransition(label = "radio")
        val alpha by pulse.animateFloat(
            initialValue = 0.5f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "radioAlpha"
        )
        Icon(
            painterResource(R.drawable.ic_radio), null,
            tint = GreenAccent.copy(alpha = alpha), modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text("در حال پخش رادیو موزیک", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GreenAccent)
        Text("🎧 برای همه اعضای لابی همزمان پخش می‌شود", fontSize = 11.sp, color = MediumGrayText)
    }
}

@Composable
private fun SharedModePlaceholder(
    sharedFileName: String,
    hasLocalFile: Boolean,
    onPickFile: () -> Unit,
    playerContent: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasLocalFile) {
            playerContent()
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📂 حالت فایل مشترک فعال شد", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WhiteText)
                Spacer(Modifier.height(6.dp))
                Text("📁 فایل درخواستی: ${sharedFileName.ifBlank { "نامشخص" }}", fontSize = 12.sp, color = YellowAccent)
                Spacer(Modifier.height(6.dp))
                Text(
                    "لطفاً فایلی با همین نام (یا مشابه) را از گوشی خود انتخاب کنید تا پخش همزمان با سایرین داشته باشید.",
                    fontSize = 11.sp, color = MediumGrayText, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPickFile,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9333EA))
                ) { Text("انتخاب فایل ویدیو", fontSize = 13.sp) }
                Text(
                    "💡 توجه: فایل شما باید duration مشابه با فایل سایر کاربران داشته باشد.",
                    fontSize = 10.sp, color = MediumGrayText.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun WebViewPlayer(url: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { view ->
            if (view.url != url) view.loadUrl(url)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun Surface2(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) { content() }
}

// ================================================================
// users chips
// ================================================================

@Composable
private fun UsersChipsRow(
    users: List<LobbyUser>,
    userIcons: Map<String, String>,
    micEnabledUsers: Set<String>,
    onUsersClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101B2E))
            .clickable { onUsersClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Star, null, tint = YellowAccent, modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "کاربران آنلاین (${users.size})",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WhiteText
        )
        Spacer(Modifier.width(10.dp))
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(users) { user ->
                UserChipHorizontal(user, userIcons[user.userId], micEnabledUsers.contains(user.userId))
            }
        }
    }
}

@Composable
private fun UserChipHorizontal(user: LobbyUser, iconId: String?, micOn: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Box {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF64748B).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                if (!iconId.isNullOrBlank()) {
                    AsyncImage(
                        model = LOBBY_ICON_BASE_URL + iconId + ".jpg",
                        contentDescription = user.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        user.displayName.take(1),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD)
                    )
                }
            }
            if (user.isHost) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(YellowAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Star, "میزبان", tint = Color(0xFF050C1A), modifier = Modifier.size(9.dp))
                }
            }
            if (micOn) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(GreenAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Mic, "میکروفون روشن", tint = Color.White, modifier = Modifier.size(8.dp))
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            user.displayName,
            fontSize = 9.sp, color = WhiteText.copy(alpha = 0.85f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

// ================================================================
// chat list
// ================================================================

@Composable
private fun ChatMessagesList(
    messages: List<ChatMessage>,
    currentUserId: String,
    userIcons: Map<String, String>,
    modifier: Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (messages.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("هنوز پیامی ارسال نشده!", fontSize = 12.sp, color = MediumGrayText)
                    Text("اولین نفر باشید که پیام می‌فرستد 💬", fontSize = 11.sp, color = MediumGrayText)
                }
            }
        }
        items(messages) { message ->
            MessageItem(message, currentUserId, userIcons[message.userId])
        }
    }
}

@Composable
private fun MessageItem(message: ChatMessage, currentUserId: String, iconId: String?) {
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

    StickerCatalog.drawableFor(message.message)?.let { stickerRes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
        ) {
            Column(horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
                if (!isOwn) {
                    Text(
                        message.username, fontSize = 10.sp,
                        color = Color(0xFF9BA8BC), fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                    )
                }
                Icon(
                    painterResource(stickerRes), "استیکر",
                    modifier = Modifier.size(110.dp), tint = Color.Unspecified
                )
                Text(
                    formatTime(message.timestamp),
                    fontSize = 9.sp, color = WhiteText.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp, start = 4.dp)
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isOwn) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF64748B).copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                if (!iconId.isNullOrBlank()) {
                    AsyncImage(
                        model = LOBBY_ICON_BASE_URL + iconId + ".jpg",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        message.username.take(1),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 270.dp)
        ) {
            if (!isOwn) {
                Text(
                    message.username, fontSize = 10.sp,
                    color = Color(0xFF9BA8BC), fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        if (isOwn) Color(0xFF1E3A5F) else Color(0xFF1E3A2E),
                        RoundedCornerShape(
                            16.dp, 16.dp,
                            if (isOwn) 4.dp else 16.dp,
                            if (isOwn) 16.dp else 4.dp
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(message.message, fontSize = 13.sp, color = WhiteText)
            }
            Text(
                formatTime(message.timestamp),
                fontSize = 9.sp, color = WhiteText.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 2.dp, end = 4.dp, start = 4.dp)
            )
        }
    }
}

// ================================================================
// message input
// ================================================================

@Composable
private fun MessageInputRow(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onStickerClick: () -> Unit,
    onSendClick: () -> Unit,
    isMicEnabled: Boolean,
    onMicClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton2(onStickerClick) { Text("😊", fontSize = 18.sp) }
        Spacer(Modifier.width(6.dp))
        OutlinedTextField(
            value = messageText,
            onValueChange = onMessageChange,
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
        Spacer(Modifier.width(6.dp))
        IconButton2(onMicClick) {
            Icon(
                if (isMicEnabled) Icons.Filled.Mic else Icons.Filled.MicOff,
                contentDescription = if (isMicEnabled) "خاموش کردن میکروفون" else "روشن کردن میکروفون",
                tint = if (isMicEnabled) GreenAccent else RedAccent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    if (messageText.isNotBlank()) ChatButtonGradient
                    else SolidColor(Color(0xFF14243C))
                )
                .clickable { if (messageText.isNotBlank()) onSendClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Send, "ارسال",
                tint = if (messageText.isNotBlank()) Color.White else MediumGrayText,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun IconButton2(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color(0xFF14243C))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}

// ================================================================
// floating message notification
// ================================================================

@Composable
private fun FloatingMessageNotification(
    message: ChatMessage,
    iconId: String?,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE6101B2E))
            .border(1.dp, CyanAccent.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .clickable { onClick(); onDismiss() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF64748B).copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            if (!iconId.isNullOrBlank()) {
                AsyncImage(
                    model = LOBBY_ICON_BASE_URL + iconId + ".jpg",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(message.username.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(message.username, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
            Text(
                message.message, fontSize = 12.sp, color = WhiteText,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ================================================================
// fullscreen player overlay
// ================================================================

@Composable
private fun FullscreenPlayerOverlay(
    exoPlayer: ExoPlayer,
    currentCueText: String,
    subtitleSizeSp: Float,
    subtitleTextColor: Color,
    usersCount: Int,
    micEnabled: Boolean,
    onToggleMic: () -> Unit,
    onOpenChat: () -> Unit,
    onExit: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        PlayerSurface(exoPlayer, showController = true)

        if (currentCueText.isNotBlank()) {
            Text(
                currentCueText,
                fontSize = subtitleSizeSp.sp,
                color = subtitleTextColor,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // exit fullscreen (top-start)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .clickable { onExit() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, "خروج از حالت تمام صفحه", tint = Color.White, modifier = Modifier.size(20.dp))
        }

        // users count (top-end)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text("کاربران آنلاین ($usersCount)", fontSize = 11.sp, color = Color.White)
        }

        // floating mic + chat (bottom-start)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FloatingMicButton(micEnabled = micEnabled, onClick = onToggleMic)
            com.app.bebinim.ui.components.FloatingChatButton(
                onClick = onOpenChat, visible = true
            )
        }
    }
}

// ================================================================
// welcome dialog with lobby icon picker (foxy/shipy/tems/meymo)
// ================================================================

@Composable
private fun AliasWelcomeDialog(
    aliasInput: String,
    onAliasChange: (String) -> Unit,
    selectedIconId: String,
    onIconSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        containerColor = Color(0xFF0E1928),
        shape = RoundedCornerShape(20.dp),
        title = { Text("خوش آمدید! 👋", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
        text = {
            Column {
                Text("برای شروع، نام نمایشی خود را وارد کنید", fontSize = 13.sp, color = Color(0xFFB9C2D0))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = aliasInput,
                    onValueChange = onAliasChange,
                    placeholder = { Text("مثلاً: علی", fontSize = 13.sp, color = MediumGrayText) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = CyanAccent.copy(alpha = 0.25f)
                    )
                )
                Spacer(Modifier.height(14.dp))
                Text("آیکون لابی‌ات را انتخاب کن", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WhiteText)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LOBBY_ICONS.forEach { def ->
                        val selected = selectedIconId == def.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    2.dp,
                                    if (selected) def.placeholderColor else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { onIconSelect(if (selected) "" else def.id) }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(def.placeholderColor.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = LOBBY_ICON_BASE_URL + def.id + ".jpg",
                                    contentDescription = def.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                def.label, fontSize = 10.sp,
                                color = if (selected) def.placeholderColor else Color(0xFFB9C2D0)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) { Text("ثبت", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("رد کردن", color = Color(0xFF718096)) }
        }
    )
}

// ================================================================
// playback mode dialog (PlaybackModeOption list)
// ================================================================

@Composable
private fun PlaybackModeDialog(
    currentMode: String,
    onDismiss: () -> Unit,
    onLink: () -> Unit,
    onRadio: () -> Unit,
    onShared: () -> Unit,
    onWebView: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1928),
        shape = RoundedCornerShape(20.dp),
        title = { Text("انتخاب حالت پخش", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaybackModeOption(
                    title = "لینک", subtitle = "پخش با لینک مستقیم ویدیو",
                    accent = Color(0xFF06B6D4),
                    icon = { Icon(painterResource(R.drawable.ic_link), null, tint = Color(0xFF06B6D4), modifier = Modifier.size(22.dp)) },
                    selected = currentMode == "link" || currentMode == "archive",
                    onClick = onLink
                )
                PlaybackModeOption(
                    title = "رادیو", subtitle = "گوش دادن به رادیو موزیک",
                    accent = Color(0xFF22C55E),
                    icon = { Icon(painterResource(R.drawable.ic_radio), null, tint = Color(0xFF22C55E), modifier = Modifier.size(22.dp)) },
                    selected = currentMode == "radio",
                    onClick = onRadio
                )
                PlaybackModeOption(
                    title = "فایل ویدیو", subtitle = "انتخاب فایل از گوشی — همه اعضا همزمان پخش می‌کنند",
                    accent = Color(0xFF9333EA),
                    icon = { Icon(painterResource(R.drawable.ic_movie), null, tint = Color(0xFF9333EA), modifier = Modifier.size(22.dp)) },
                    selected = currentMode == "shared",
                    onClick = onShared
                )
                PlaybackModeOption(
                    title = "وب‌ویو", subtitle = "پخش WebView / iframe از لینک وب",
                    accent = Color(0xFF4A9EFF),
                    icon = { Icon(Icons.Filled.Language, null, tint = Color(0xFF4A9EFF), modifier = Modifier.size(22.dp)) },
                    selected = currentMode == "webview",
                    onClick = onWebView
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("بستن", color = CyanAccent) }
        }
    )
}

@Composable
private fun PlaybackModeOption(
    title: String,
    subtitle: String,
    accent: Color,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Brush.verticalGradient(listOf(accent.copy(alpha = 0.15f), accent.copy(alpha = 0.08f)))
                else SolidColor(Color(0xFF16213E))
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) accent else WhiteText)
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF9CA3AF))
        }
    }
}

// ================================================================
// subtitle dialog
// ================================================================

@Composable
private fun SubtitleDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1928),
        shape = RoundedCornerShape(20.dp),
        title = { Text("زیرنویس", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
        text = {
            Column {
                Text(
                    if (currentUrl.isNotBlank()) "زیرنویس فعال است" else "زیرنویسی انتخاب نشده",
                    fontSize = 13.sp,
                    color = if (currentUrl.isNotBlank()) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "فایل زیرنویس از گوشی خودتان انتخاب می‌شود؛ فقط VTT و SRT پشتیبانی می‌شوند.",
                    fontSize = 11.sp, color = Color(0xFF9CA3AF)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onPickFile,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) { Text("انتخاب فایل", color = Color.White) }
        },
        dismissButton = {
            Row {
                if (currentUrl.isNotBlank()) {
                    TextButton(onClick = onRemove) { Text("حذف زیرنویس", color = RedAccent) }
                }
                TextButton(onClick = onDismiss) { Text("بستن", color = CyanAccent) }
            }
        }
    )
}

// ================================================================
// video settings bottom sheet
// ================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSettingsSheet(
    exoPlayer: ExoPlayer,
    hasSubtitle: Boolean,
    subtitleColor: Color,
    subtitleSize: Float,
    onSubtitleClick: () -> Unit,
    onSubtitleColorChange: (Color) -> Unit,
    onSubtitleSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101B2E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تنظیمات", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "بستن", tint = WhiteText, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            // --- subtitle section ---
            Text("زیرنویس", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WhiteText)
            Spacer(Modifier.height(4.dp))
            Text(
                if (hasSubtitle) "زیرنویس فعال است" else "زیرنویسی انتخاب نشده",
                fontSize = 12.sp,
                color = if (hasSubtitle) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF16213E))
                    .clickable { onSubtitleClick() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("انتخاب فایل زیرنویس (VTT / SRT)", fontSize = 13.sp, color = CyanAccent)
            }
            Spacer(Modifier.height(16.dp))

            // --- subtitle color ---
            Text("رنگ زیرنویس", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WhiteText)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Color.White, Color(0xFFFFA500),
                    Color(0xFF4ADE80), Color(0xFF93C5FD), Color(0xFFF59E0B)
                ).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                if (subtitleColor == c) 3.dp else 1.dp,
                                if (subtitleColor == c) CyanAccent else Color.White.copy(alpha = 0.3f),
                                CircleShape
                            )
                            .clickable { onSubtitleColorChange(c) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // --- subtitle size ---
            Text("اندازه زیرنویس: ${subtitleSize.toInt()}sp", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WhiteText)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF16213E))
                        .clickable { onSubtitleSizeChange((subtitleSize - 2f).coerceAtLeast(12f)) },
                    contentAlignment = Alignment.Center
                ) { Text("−", fontSize = 18.sp, color = WhiteText) }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF16213E))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text("پیش‌نمایش زیرنویس", fontSize = subtitleSize.sp, color = subtitleColor) }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF16213E))
                        .clickable { onSubtitleSizeChange((subtitleSize + 2f).coerceAtMost(32f)) },
                    contentAlignment = Alignment.Center
                ) { Text("+", fontSize = 18.sp, color = WhiteText) }
            }
        }
    }
}

// ================================================================
// users dialog
// ================================================================

@Composable
private fun UsersDialog(
    users: List<LobbyUser>,
    userIcons: Map<String, String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1928),
        shape = RoundedCornerShape(20.dp),
        title = { Text("کاربران آنلاین (${users.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
        text = {
            Column {
                if (users.isEmpty()) Text("در حال بارگذاری...", fontSize = 13.sp, color = MediumGrayText)
                users.forEach { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF64748B).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val iconId = userIcons[user.userId]
                            if (!iconId.isNullOrBlank()) {
                                AsyncImage(
                                    model = LOBBY_ICON_BASE_URL + iconId + ".jpg",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(user.displayName.take(1), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(user.displayName, fontSize = 14.sp, color = WhiteText, modifier = Modifier.weight(1f))
                        if (user.isHost) {
                            Icon(Icons.Filled.Star, "میزبان", tint = YellowAccent, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("میزبان", fontSize = 11.sp, color = YellowAccent)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن", color = CyanAccent) }
        }
    )
}

// ================================================================
// help dialog
// ================================================================

@Composable
private fun HelpDialog(
    onOpenArticles: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0E1928),
        shape = RoundedCornerShape(20.dp),
        title = { Text("راهنما", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("چطور لابی رو با دوستاتون استفاده کنید:", fontSize = 13.sp, color = Color(0xFFB9C2D0))
                Text("۱. کد لابی رو با دکمه دعوت برای دوستاتون بفرستید", fontSize = 12.sp, color = WhiteText.copy(alpha = 0.85f))
                Text("۲. حالت پخش رو انتخاب کنید: لینک، رادیو، فایل مشترک یا وب‌ویو", fontSize = 12.sp, color = WhiteText.copy(alpha = 0.85f))
                Text("۳. با چت و استیکر حرف بزنید و میکروفون رو برای صحبت صوتی روشن کنید", fontSize = 12.sp, color = WhiteText.copy(alpha = 0.85f))
                Text("۴. همه پخش‌ها همزمان بین اعضا همگام‌سازی می‌شوند", fontSize = 12.sp, color = WhiteText.copy(alpha = 0.85f))
                Spacer(Modifier.height(6.dp))
                Text(
                    "مقالات بیشتر در مجله ببینیم",
                    fontSize = 12.sp, color = CyanAccent,
                    modifier = Modifier.clickable { onOpenArticles() }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("فهمیدم", color = CyanAccent) }
        }
    )
}
