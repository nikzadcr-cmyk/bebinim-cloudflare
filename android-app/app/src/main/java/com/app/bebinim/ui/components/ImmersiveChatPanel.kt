package com.app.bebinim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bebinim.R
import com.app.bebinim.data.model.StickerCatalog
import com.app.bebinim.data.websocket.ChatMessage
import kotlinx.coroutines.delay
import kotlin.random.Random

private val PanelBg = Color(0xFF161B22)
private val PanelSurface = Color(0xFF1E2330)
private val EmojiList = listOf("❤️", "😂", "🔥", "👍", "🎉")

/**
 * Fullscreen immersive chat — opens over the whole lobby with
 * compact message bubbles, emoji/sticker picker and blast overlay.
 */
@Composable
fun ImmersiveChatPanel(
    messages: List<ChatMessage>,
    currentUserId: String,
    displayNames: Map<String, String>,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClose: () -> Unit,
    onReactionSend: (String) -> Unit,
    onStickerSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC050C1A))
            .navigationBarsPadding()
            .imePadding()
    ) {
        // header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("گفتگوی لابی", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${messages.size} پیام", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("بستن", fontSize = 11.sp, color = Color.White)
            }
        }

        // messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("💬", fontSize = 40.sp)
                        Text("هنوز پیامی نیست", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    }
                }
            }
            items(messages) { message ->
                CompactMessageItem(message, currentUserId, displayNames[message.userId])
            }
        }

        // emoji + sticker picker
        AnimatedVisibility(visible = showPicker) {
            EmojiStickerPickerPanel(
                onEmojiSelected = { emoji ->
                    onReactionSend(emoji)
                },
                onStickerSelected = { fileName ->
                    onStickerSend(StickerCatalog.STICKER_PREFIX + fileName)
                }
            )
        }

        // input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (showPicker) Color(0xFF22C55E).copy(alpha = 0.25f) else PanelSurface)
                    .clickable { showPicker = !showPicker },
                contentAlignment = Alignment.Center
            ) {
                Text(if (showPicker) "✕" else "😊", fontSize = 19.sp)
            }
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = messageText,
                onValueChange = onMessageTextChange,
                modifier = Modifier
                    .weight(1f)
                    .background(PanelSurface, RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (messageText.isNotBlank()) onSendMessage() }),
                cursorBrush = Brush.verticalGradient(listOf(Color(0xFF22D3EE), Color(0xFF22D3EE))),
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
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (messageText.isNotBlank()) ChatButtonGradient
                        else SolidColor(PanelSurface)
                    )
                    .clickable {
                        keyboard?.hide()
                        if (messageText.isNotBlank()) onSendMessage()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    "ارسال",
                    tint = if (messageText.isNotBlank()) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Small bubble used inside the immersive chat. */
@Composable
fun CompactMessageItem(message: ChatMessage, currentUserId: String, iconId: String?) {
    val isOwn = message.userId == currentUserId
    StickerCatalog.drawableFor(message.message)?.let { stickerRes ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
        ) {
            Column(horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start) {
                if (!isOwn) {
                    Text(
                        message.username, fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                    )
                }
                androidx.compose.material3.Icon(
                    painterResource(stickerRes), message.username,
                    modifier = Modifier.size(110.dp), tint = Color.Unspecified
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isOwn) {
                Text(
                    message.username, fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF93C5FD),
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
            .height(260.dp)
            .background(Color(0xF2161B22))
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
                            .background(PanelSurface)
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
                                .background(PanelSurface)
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
            .background(if (selected) Color(0xFF22C55E).copy(alpha = 0.2f) else PanelSurface)
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

/** Floating chat toggle button (bottom-start, above input). */
@Composable
fun FloatingChatButton(onClick: () -> Unit, visible: Boolean, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "chatPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glow"
    )
    AnimatedVisibility(
        visible = visible, modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .graphicsLayer { alpha = glow }
                .clip(CircleShape)
                .background(ChatButtonGradient)
                .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                painterResource(R.drawable.ic_chat), "چت",
                tint = Color.White, modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** Floating mic toggle (bottom-start). */
@Composable
fun FloatingMicButton(micEnabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = true, modifier = modifier,
        enter = fadeIn(), exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    if (micEnabled) MicButtonGradient else SolidColor(PanelSurface)
                )
                .border(
                    2.dp,
                    if (micEnabled) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                    CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                painterResource(if (micEnabled) R.drawable.ic_microphone else R.drawable.ic_microphone_off),
                "میکروفون",
                tint = if (micEnabled) Color.White else Color(0xFF718096),
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


