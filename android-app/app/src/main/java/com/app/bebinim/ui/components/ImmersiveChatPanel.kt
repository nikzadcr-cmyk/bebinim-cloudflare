package com.app.bebinim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.app.bebinim.R
import com.app.bebinim.data.model.StickerCatalog
import com.app.bebinim.data.websocket.ChatMessage
import kotlinx.coroutines.delay
import kotlin.random.Random

// ---- exact palette of the original ImmersiveChatPanel.kt ----
private val PanelTop = Color(0xFF0F1419)
private val PanelMid = Color(0xFF0A0E14)
private val PanelBottom = Color(0xFF0D1117)
private val PanelSurfaceBg = Color(0xFF161B22)
private val CyanGlow = Color(0xFF06B6D4)
private val CyanDark = Color(0xFF0891B2)
private val InputGradientStart = Color(0xFF0C3B44)
private val InputGradientEnd = Color(0xFF082A30)
private val PurpleAccent = Color(0xFF8B5CF6)
private val GrayIcon = Color(0xFF9CA3AF)
private val DividerGray = Color(0xFF374151)

private val EmojiList = listOf("❤️", "😂", "🔥", "👍", "🎉", "😮", "😭", "👏")

/**
 * Fullscreen immersive chat — original design:
 * landscape → a side window (45% of screen width) docked to the screen edge over
 * a dark scrim, video stays visible on the other half;
 * portrait  → a full-page chat window (like the original, chat covers the screen).
 * Swipe the panel toward the edge (>100dp) or tap the scrim to close.
 */
@Composable
fun ImmersiveChatPanel(
    messages: List<ChatMessage>,
    currentUserId: String,
    userIcons: Map<String, String>,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClose: () -> Unit,
    onReactionSend: (String) -> Unit,
    onStickerSend: (String) -> Unit,
    hasUnreadMessages: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) } // dp
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // original formula: landscape = 45% of screen width (side window over the video),
    // portrait = full-width chat page. Values are already in dp — NEVER run them
    // through Float.toDp() again (that divided by density a second time and the
    // panel ended up ~2.6x too thin: the "چت نازک و خراب" bug).
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isFullPage = !isLandscape
    val panelWidth = if (isLandscape) 0.45f * screenWidthDp else screenWidthDp
    val panelWidthDp = panelWidth.dp
    val panelShape = if (isFullPage) RoundedCornerShape(0.dp)
        else RoundedCornerShape(topStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 24.dp)

    // animated slide-out (spring 0.5/1500 like the original "chat_slide")
    val slideTarget = if (offsetX > 100f) panelWidthDp else 0.dp
    val slideOffset by animateDpAsState(
        targetValue = slideTarget,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 1500f),
        label = "chat_slide"
    )
    LaunchedEffect(offsetX) {
        if (offsetX > 100f) {
            delay(80)
            onClose()
        }
    }

    // animated cyan border glow (0.3 → 0.6, 2000ms — original header_glow)
    val headerGlow by rememberInfiniteTransition(label = "header_glow").animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "header_glow"
    )

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // the original forces RTL inside the panel
    CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Box(modifier = modifier.fillMaxSize().zIndex(10f)) {
            // ---- scrim: gradient toward the panel edge, tap to close ----
            val gradientEndPx = with(density) { (screenWidthDp - panelWidth).dp.toPx() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (isFullPage) SolidColor(PanelMid.copy(alpha = 0.7f))
                        else Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, PanelMid.copy(alpha = 0.4f), PanelMid.copy(alpha = 0.7f)),
                            startX = 0f,
                            endX = gradientEndPx
                        )
                    )
                    .clickable { onClose() }
            )

            // ---- the side panel / full-page chat ----
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = slideOffset)
                    .width(panelWidthDp)
                    .fillMaxHeight()
                    .shadow(
                        24.dp,
                        panelShape,
                        spotColor = CyanGlow.copy(alpha = 0.3f)
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                if (isFullPage) PanelTop.copy(alpha = 0.96f) else PanelTop.copy(alpha = 0.68f),
                                if (isFullPage) PanelMid.copy(alpha = 0.97f) else PanelMid.copy(alpha = 0.72f),
                                if (isFullPage) PanelBottom.copy(alpha = 0.96f) else PanelBottom.copy(alpha = 0.68f)
                            )
                        ),
                        panelShape
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(CyanGlow.copy(alpha = headerGlow), CyanGlow.copy(alpha = 0.2f), CyanDark.copy(alpha = 0.1f))
                        ),
                        panelShape
                    )
                    // bottom inset = max(nav bar, keyboard) — in portrait the input row
                    // must sit ABOVE the system navigation buttons ("تداخل با دکمه‌های پایین گوشی")
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            ) {
                // ---- header: chat icon + titles + close (like the source) ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PanelSurfaceBg.copy(alpha = 0.95f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat, null,
                            tint = Color.White, modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("چت", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "گفتگوی لابی", fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close, "بستن",
                            tint = Color.White, modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // animated divider
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, DividerGray, Color.Transparent)
                            )
                        )
                )

                // ---- messages (swipe toward edge closes, tap hides keyboard) ----
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (offsetX <= 100f) offsetX = 0f
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                // RTL: sliding out = visually toward the left = negative px
                                offsetX = (offsetX - dragAmount / density.density)
                                    .coerceAtLeast(0f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                keyboard?.hide()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("💬", fontSize = 40.sp)
                                Text(
                                    "هنوز پیامی نیست", fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    items(messages) { message ->
                        CompactMessageItem(
                            message = message,
                            currentUserId = currentUserId,
                            iconId = userIcons[message.userId]
                        )
                    }
                }

                // ---- emoji + sticker picker ----
                AnimatedVisibility(visible = showPicker) {
                    EmojiStickerPickerPanel(
                        onEmojiSelected = { emoji -> onReactionSend(emoji) },
                        onStickerSelected = { fileName -> onStickerSend(StickerCatalog.STICKER_PREFIX + fileName) }
                    )
                }

                // ---- input row ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PanelSurfaceBg.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { showPicker = !showPicker },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.EmojiEmotions, "ایموجی و استیکر",
                            tint = if (showPicker) PurpleAccent else GrayIcon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = messageText,
                        onValueChange = onMessageTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        InputGradientStart.copy(alpha = 0.55f),
                                        InputGradientEnd.copy(alpha = 0.55f)
                                    )
                                ),
                                RoundedCornerShape(24.dp)
                            )
                            .border(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                                ),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (messageText.isNotBlank()) onSendMessage() }),
                        cursorBrush = SolidColor(CyanGlow),
                        decorationBox = { inner ->
                            Box {
                                if (messageText.isBlank()) {
                                    Text("پیام...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
                                }
                                inner()
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(4.dp, CircleShape, spotColor = PurpleAccent.copy(alpha = 0.2f))
                            .clip(CircleShape)
                            .background(if (messageText.isNotBlank()) CyanGlow else Color.White.copy(alpha = 0.15f))
                            .clickable {
                                keyboard?.hide()
                                if (messageText.isNotBlank()) onSendMessage()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send, "ارسال",
                            tint = Color.White, modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Small bubble used inside the immersive chat (avatar + name, like the source). */
@Composable
fun CompactMessageItem(message: ChatMessage, currentUserId: String, iconId: String?) {
    val isOwn = message.userId == currentUserId
    StickerCatalog.drawableFor(message.message)?.let { stickerRes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isOwn) Arrangement.Start else Arrangement.End
        ) {
            androidx.compose.material3.Icon(
                painterResource(stickerRes), message.username,
                modifier = Modifier.size(90.dp), tint = Color.Unspecified
            )
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.Start else Arrangement.End
    ) {
        Column(
            horizontalAlignment = if (isOwn) Alignment.Start else Alignment.End,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isOwn) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        com.app.bebinim.ui.screens.LobbyAvatarImage(
                            iconId = iconId,
                            contentDescription = message.username,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        message.username, fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF93C5FD)
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
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
                Text(message.message, fontSize = 13.sp, color = Color.White)
            }
        }
    }
}

/** Emoji tab + sticker tab picker panel (bottom of immersive chat). */
@Composable
fun EmojiStickerPickerPanel(
    onEmojiSelected: (String) -> Unit,
    onStickerSelected: (String) -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(PanelSurfaceBg.copy(alpha = 0.97f))
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            PickerTabButton(Modifier.weight(1f), "ایموجی‌ها", tab == 0) { tab = 0 }
            Spacer(Modifier.width(6.dp))
            PickerTabButton(Modifier.weight(1f), "استیکرها", tab == 1) { tab = 1 }
        }
        if (tab == 0) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(EmojiList) { emoji ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onEmojiSelected(emoji) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StickerCatalog.groups.forEach { group ->
                    item(key = "header_${group.id}") {
                        Text(
                            group.label,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                    items(group.stickers, key = { it.fileName }) { sticker ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onStickerSelected(sticker.fileName) }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                painterResource(sticker.drawableRes),
                                sticker.fileName,
                                modifier = Modifier.size(64.dp),
                                tint = Color.Unspecified
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTabButton(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF22C55E).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) Color(0xFF22C55E).copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = if (selected) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.7f)
        )
    }
}

/**
 * Floating chat toggle button — 48dp circle, black 0.6, like the original
 * fullscreen overlay (LobbyScreen$27$6).
 */
@Composable
fun FloatingChatButton(onClick: () -> Unit, visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = fadeIn(), exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat, "چت",
                tint = Color.White, modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Floating mic toggle — 48dp circle, black 0.6, like the source. */
@Composable
fun FloatingMicButton(
    micEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = fadeIn(), exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(if (micEnabled) R.drawable.ic_microphone else R.drawable.ic_microphone_off),
                "میکروفون",
                tint = if (micEnabled) Color.White else Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Emoji blast overlay — tapped emojis fly up from the bottom with random
 * horizontal offsets and fade away, exactly like the original emojiBlasts.
 */
@Composable
fun EmojiBlastOverlay(emojis: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        emojis.forEach { (emoji, stamp) ->
            androidx.compose.runtime.key(stamp) {
                var started by remember { mutableStateOf(false) }
                val progress by animateFloatAsState(
                    targetValue = if (started) 1f else 0f,
                    animationSpec = tween(1900),
                    label = "blast"
                )
                LaunchedEffect(stamp) {
                    delay(10)
                    started = true
                }
                val density = LocalDensity.current
                val xOffset = remember(stamp) { Random.nextInt(0, 240) }
                val emojiSize = remember(stamp) { 40 + Random.nextInt(0, 16) }
                Text(
                    emoji,
                    fontSize = emojiSize.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(
                            x = with(density) { xOffset.dp.times(-1f) },
                            y = with(density) { (-20).dp.minus(320.dp.times(progress)) }
                        )
                        .graphicsLayer {
                            alpha = (1f - progress * progress).coerceIn(0f, 1f)
                            scaleX = 0.7f + progress * 0.6f
                            scaleY = 0.7f + progress * 0.6f
                        }
                )
            }
        }
    }
}
