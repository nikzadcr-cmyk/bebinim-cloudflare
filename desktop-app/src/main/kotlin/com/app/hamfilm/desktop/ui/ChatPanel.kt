package com.app.hamfilm.desktop.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import com.app.hamfilm.desktop.ChatMessage
import com.app.hamfilm.desktop.QUICK_EMOJIS
import com.app.hamfilm.desktop.Res
import com.app.hamfilm.desktop.StickerCatalog
import com.app.hamfilm.desktop.YellowAccent
import com.app.hamfilm.desktop.BorderGray
import com.app.hamfilm.desktop.ChipDark
import com.app.hamfilm.desktop.DarkCardBackground
import com.app.hamfilm.desktop.DarkNavyBackground
import com.app.hamfilm.desktop.LightGrayText
import com.app.hamfilm.desktop.MediumGrayText
import com.app.hamfilm.desktop.RedAccent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Chat panel — desktop port of ImmersiveChatPanel:
 * messages, quick emoji bar (tap = send), sticker picker, input row.
 * Docked to the RIGHT side of the window (round-6 layout decision).
 */
@Composable
fun ChatPanel(
    messages: List<ChatMessage>,
    userIcons: Map<String, String>,
    myUserId: String,
    onClose: () -> Unit,
    onSend: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showStickers by remember { mutableStateOf(false) }
    var stickerGroup by remember { mutableStateOf(StickerCatalog.groups.firstOrNull()?.id ?: "pishi") }
    val listState = rememberLazyListState()

    // auto-scroll to newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        Modifier
            .width(340.dp)
            .fillMaxSize()
            .background(DarkCardBackground)
            .border(1.dp, BorderGray, RoundedCornerShape(0.dp))
    ) {
        // ---- header ----
        Row(
            Modifier.fillMaxWidth().background(DarkNavyBackground).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("گفتگو", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LightGrayText)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showStickers = !showStickers }) {
                Icon(
                    Icons.Filled.Mood, contentDescription = "استیکر",
                    tint = if (showStickers) YellowAccent else MediumGrayText
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "بستن", tint = MediumGrayText)
            }
        }

        // ---- sticker picker ----
        if (showStickers) {
            Column(Modifier.background(DarkNavyBackground).heightIn(max = 300.dp)) {
                LazyRow(
                    Modifier.padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(StickerCatalog.groups) { g ->
                        Text(
                            g.label,
                            fontSize = 12.sp,
                            color = if (g.id == stickerGroup) YellowAccent else MediumGrayText,
                            fontWeight = if (g.id == stickerGroup) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (g.id == stickerGroup) ChipDark else Color.Transparent)
                                .clickable { stickerGroup = g.id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                val group = StickerCatalog.groups.firstOrNull { it.id == stickerGroup }
                LazyColumn(
                    Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group?.stickers?.chunked(4)?.forEach { rowStickers ->
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowStickers.forEach { name ->
                                    Res.sticker(name)?.let { bmp ->
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = name,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    onSend(StickerCatalog.STICKER_PREFIX + name)
                                                    showStickers = false
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- messages ----
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 10.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                MessageItem(msg, userIcons, myUserId)
            }
        }

        // ---- quick emoji bar (tap sends immediately) ----
        Row(
            Modifier
                .fillMaxWidth()
                .background(DarkNavyBackground)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QUICK_EMOJIS.forEach { emoji ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSend(emoji) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(emoji, fontSize = 20.sp)
                }
            }
        }

        // ---- input row ----
        Row(
            Modifier.fillMaxWidth().background(DarkNavyBackground).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("پیام...", fontSize = 13.sp, color = MediumGrayText) },
                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                singleLine = true,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LightGrayText,
                    unfocusedTextColor = LightGrayText,
                    cursorColor = YellowAccent,
                    focusedBorderColor = YellowAccent,
                    unfocusedBorderColor = BorderGray
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(YellowAccent)
                    .clickable {
                        val text = input.trim()
                        if (text.isNotEmpty()) {
                            onSend(text)
                            input = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Send, contentDescription = "ارسال",
                    tint = Color(0xFF10131A)
                )
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

@Composable
private fun MessageItem(
    msg: ChatMessage,
    userIcons: Map<String, String>,
    myUserId: String
) {
    if (msg.isSystemMessage) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                msg.message,
                fontSize = 11.sp,
                color = MediumGrayText,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ChipDark)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        return
    }

    val mine = msg.userId == myUserId
    val iconId = userIcons[msg.userId]

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        if (!mine) {
            AvatarOrRemote(iconId, msg.userId, 26.dp)
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Text(
                msg.username,
                fontSize = 11.sp,
                color = if (mine) YellowAccent else MediumGrayText,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            val stickerFile = StickerCatalog.fileNameFor(msg.message)
            if (stickerFile != null) {
                Res.sticker(stickerFile)?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = stickerFile,
                        modifier = Modifier
                            .widthIn(max = 130.dp)
                            .size(width = 120.dp, height = 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            } else {
                Text(
                    msg.message,
                    fontSize = 13.sp,
                    color = if (mine) Color(0xFF141B26) else LightGrayText,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (mine) YellowAccent else ChipDark)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(1.dp))
            Text(timeFmt.format(Instant.ofEpochMilli(msg.timestamp)), fontSize = 9.sp, color = MediumGrayText)
        }
        if (mine) {
            Spacer(Modifier.width(6.dp))
            AvatarOrRemote(userIcons[msg.userId], msg.userId, 26.dp)
        }
    }
}

/** avatar with remote fallback for legacy ids */
@Composable
fun AvatarOrRemote(iconId: String?, userId: String, size: androidx.compose.ui.unit.Dp) {
    val local = Res.avatar(iconId)
    if (local != null) {
        Image(
            bitmap = local,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape)
        )
    } else {
        var remote by remember(userId) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(iconId) {
            if (!iconId.isNullOrBlank()) {
                remote = Res.remoteImage(com.app.hamfilm.desktop.LOBBY_ICON_BASE_URL + iconId + ".jpg")
            }
        }
        Box(
            Modifier.size(size).clip(CircleShape).background(ChipDark),
            contentAlignment = Alignment.Center
        ) {
            val bmp = remote
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
            } else {
                Text(
                    iconId?.take(1)?.uppercase() ?: "?",
                    color = RedAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
