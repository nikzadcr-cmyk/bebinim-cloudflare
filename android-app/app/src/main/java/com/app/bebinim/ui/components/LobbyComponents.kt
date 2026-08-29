package com.app.bebinim.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.app.bebinim.R
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.GreenAccent
import com.app.bebinim.ui.theme.YellowAccent

// ---- exact palette of the original lobby ----
val LinkBoxGradient = Brush.verticalGradient(listOf(Color(0xFF2D3748), Color(0xFF1A202C)))
val ChatButtonGradient = Brush.verticalGradient(listOf(Color(0xFF22D3EE), Color(0xFF0891B2)))
val UsersButtonGradient = Brush.verticalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
val InviteButtonGradient = Brush.verticalGradient(listOf(Color(0xFFFBBF24), Color(0xFFF59E0B)))
val MicButtonGradient = Brush.verticalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
val DisabledButtonGradient = Brush.verticalGradient(listOf(Color(0xFF252A3A), Color(0xFF1E2331)))
val DangerButtonGradient = Brush.verticalGradient(listOf(Color(0xFFEF4444), Color(0xFFDC2626)))
val NormalManagementGradient = Brush.verticalGradient(listOf(Color(0xFF2A2F40), Color(0xFF252A3A)))
val RadioTabColor = Color(0xFF22C55E)
val SharedTabColor = Color(0xFF9333EA)
val LinkTabColor = Color(0xFF06B6D4)

/**
 * Lobby header — exactly like the original:
 * link input bar (link mode) + playback-mode tabs + settings button.
 */
@Composable
fun LobbyHeader(
    videoLink: String,
    onVideoLinkChange: (String) -> Unit,
    onSubmit: () -> Unit,
    currentMode: String,
    onLinkTabClick: () -> Unit,
    onRadioTabClick: () -> Unit,
    onSharedTabClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onModeClick: () -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // --- link input bar (visible in link mode, like the original) ---
        if (currentMode == "link" || currentMode == "archive") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(6.dp, RoundedCornerShape(14.dp), spotColor = CyanAccent.copy(alpha = 0.3f))
                    .background(LinkBoxGradient, RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_link), null,
                    tint = LinkTabColor, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = videoLink,
                    onValueChange = onVideoLinkChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White, fontSize = 13.sp
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit(); keyboard?.hide() }),
                    cursorBrush = Brush.verticalGradient(listOf(LinkTabColor, LinkTabColor)),
                    decorationBox = { inner ->
                        Box {
                            if (videoLink.isBlank()) {
                                Text(
                                    "لینک مستقیم ویدیو (mp4/m3u8)...",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clickable(enabled = videoLink.isNotBlank(), onClick = { onSubmit(); keyboard?.hide() })
                        .background(ChatButtonGradient, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("ثبت", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // --- mode tabs + settings ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeTab(
                modifier = Modifier.weight(1f),
                label = "لینک",
                iconRes = R.drawable.ic_link,
                accent = LinkTabColor,
                selected = currentMode == "link" || currentMode == "archive"
            ) { onLinkTabClick() }
            ModeTab(
                modifier = Modifier.weight(1f),
                label = "رادیو",
                iconRes = R.drawable.ic_radio,
                accent = RadioTabColor,
                selected = currentMode == "radio"
            ) { onRadioTabClick() }
            ModeTab(
                modifier = Modifier.weight(1f),
                label = "فایل مشترک",
                iconRes = R.drawable.ic_movie,
                accent = SharedTabColor,
                selected = currentMode == "shared"
            ) { onSharedTabClick() }
            // settings capsule button
            Box(
                modifier = Modifier
                    .width(85.dp)
                    .height(52.dp)
                    .shadow(4.dp, RoundedCornerShape(26.dp), spotColor = YellowAccent.copy(alpha = 0.4f))
                    .background(NormalManagementGradient, RoundedCornerShape(26.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                    .clickable { onSettingsClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painterResource(R.drawable.ic_settings), "تنظیمات",
                        tint = YellowAccent, modifier = Modifier.size(20.dp)
                    )
                    Text("تنظیمات", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ModeTab(
    modifier: Modifier,
    label: String,
    iconRes: Int,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .background(
                if (selected) Brush.verticalGradient(listOf(accent.copy(alpha = 0.15f), accent.copy(alpha = 0.08f)))
                else DisabledButtonGradient,
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (selected) accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(iconRes), label, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accent else Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * The four main lobby buttons — چت / کاربران / دعوت / میکروفون,
 * chunky gradient buttons exactly like the original ControlButtonsRow.
 */
@Composable
fun ControlButtonsRow(
    onChatClick: () -> Unit,
    onUsersClick: () -> Unit,
    onInviteClick: () -> Unit,
    onMicClick: () -> Unit,
    micEnabled: Boolean,
    unreadCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ControlButtonNew(
            modifier = Modifier.weight(1f), label = "چت", iconRes = R.drawable.ic_chat,
            brush = ChatButtonGradient, accent = LinkTabColor,
            badge = if (unreadCount > 0) "${unreadCount.coerceAtMost(9)}" else null
        ) { onChatClick() }
        ControlButtonNew(
            modifier = Modifier.weight(1f), label = "کاربران", iconRes = R.drawable.ic_users,
            brush = UsersButtonGradient, accent = Color(0xFF8B5CF6),
            badge = null
        ) { onUsersClick() }
        ControlButtonNew(
            modifier = Modifier.weight(1f), label = "دعوت", iconRes = R.drawable.ic_invite,
            brush = InviteButtonGradient, accent = Color(0xFFF59E0B),
            badge = null
        ) { onInviteClick() }
        ControlButtonNew(
            modifier = Modifier.weight(1f), label = "میکروفون",
            iconRes = if (micEnabled) R.drawable.ic_microphone else R.drawable.ic_microphone_off,
            brush = if (micEnabled) MicButtonGradient else DisabledButtonGradient,
            accent = if (micEnabled) Color(0xFF10B981) else Color(0xFF718096),
            badge = null
        ) { onMicClick() }
    }
}

@Composable
fun ControlButtonNew(
    modifier: Modifier,
    label: String,
    iconRes: Int,
    brush: Brush,
    accent: Color,
    badge: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(85.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f), accent.copy(alpha = 0.2f))
                ),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        // subtle radial glow
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(iconRes), label, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(DangerButtonGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

/**
 * Management buttons — راهنما / خروج / بستن لابی (host only, danger).
 */
@Composable
fun ManagementButtonsGrid(
    onHelpClick: () -> Unit,
    onExitClick: () -> Unit,
    onCloseLobbyClick: () -> Unit,
    isHost: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ManagementButton(
            modifier = Modifier.weight(1f), label = "راهنما",
            iconRes = R.drawable.ic_help, accent = Color(0xFF3B82F6)
        ) { onHelpClick() }
        ManagementButton(
            modifier = Modifier.weight(1f), label = "خروج",
            iconRes = R.drawable.ic_exit, accent = Color(0xFF64748B)
        ) { onExitClick() }
        if (isHost) {
            ManagementButton(
                modifier = Modifier.weight(1f), label = "بستن لابی",
                iconRes = R.drawable.ic_delete, accent = Color(0xFFEF4444),
                isDanger = true
            ) { onCloseLobbyClick() }
        }
    }
}

@Composable
fun ManagementButton(
    modifier: Modifier,
    label: String,
    iconRes: Int,
    accent: Color,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(70.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isDanger) DangerButtonGradient else NormalManagementGradient
            )
            .border(
                1.dp,
                if (isDanger) Color.Transparent else accent.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDanger) Color.White.copy(alpha = 0.2f) else accent.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(iconRes), label, tint = if (isDanger) Color.White else accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                label, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                color = if (isDanger) Color.White else Color.White.copy(alpha = 0.85f),
                maxLines = 1
            )
        }
    }
}
