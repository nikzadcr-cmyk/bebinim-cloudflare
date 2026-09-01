package com.app.hamfilm.desktop.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.hamfilm.desktop.ActiveLobby
import com.app.hamfilm.desktop.BlueAccent
import com.app.hamfilm.desktop.DarkCardBackground
import com.app.hamfilm.desktop.DarkNavyBackground
import com.app.hamfilm.desktop.GreenAccent
import com.app.hamfilm.desktop.MediumGrayText
import com.app.hamfilm.desktop.Res
import com.app.hamfilm.desktop.RedAccent
import com.app.hamfilm.desktop.SessionUser
import com.app.hamfilm.desktop.YellowAccent
import com.app.hamfilm.desktop.net.Api
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Lobby hub — create / join by code / active public lobbies.
 * Mirrors CreateJoinLobbyScreen + the active-lobbies list of the Android app.
 * Runs the full connect → verify → join flow BEFORE entering the lobby screen.
 */
suspend fun connectWithLobbyToken(token: String): String? {
    val ws = com.app.hamfilm.desktop.net.HamSocket.getInstance()
    ws.disconnect()
    delay(60) // tiny gap so the old socket tear-down settles (same as Android)
    ws.connect(token)
    val connected = kotlinx.coroutines.withTimeoutOrNull(15000) {
        ws.connectionState.first { it is com.app.hamfilm.desktop.ConnectionState.Connected }
        ws.isVerified.first { it }
    } != null
    if (!connected) return "خطا در اتصال به سرور"
    ws.sendLobbyToken(token)
    val joined = kotlinx.coroutines.withTimeoutOrNull(10000) {
        ws.joinSuccess.first { it.isNotBlank() }
    }
    return if (joined == null) "سرور پاسخ نداد. لطفا دوباره تلاش کنید" else null
}
@Composable
fun LobbiesScreen(
    user: SessionUser,
    onEnterLobby: (token: String, code: String, lobbyType: String) -> Unit,
    onLogout: () -> Unit
) {
    var joinCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var lobbies by remember { mutableStateOf<List<ActiveLobby>>(emptyList()) }
    var loadingList by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchLobbies() {
        scope.launch {
            loadingList = true
            Api.activeLobbies(user.token)
                .onSuccess { lobbies = it }
                .onFailure { }
            loadingList = false
        }
    }

    LaunchedEffect(Unit) { fetchLobbies() }
    // periodic refresh, like the Android list
    LaunchedEffect(Unit) {
        while (true) {
            delay(15000)
            fetchLobbies()
        }
    }
    DisposableEffect(Unit) { onDispose { } }

    Column(
        Modifier
            .fillMaxSize()
            .background(DarkNavyBackground)
            .padding(20.dp)
    ) {
        // ---------- header ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Res.logo?.let {
                androidx.compose.foundation.Image(
                    bitmap = it,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("همفیلم", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = YellowAccent)
                Text(
                    user.username.ifBlank { user.email.ifBlank { "کاربر" } },
                    fontSize = 12.sp, color = MediumGrayText
                )
            }
            Text("خروج", color = RedAccent, fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onLogout() }
                    .padding(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // ---------- create / join ----------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // create card
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCardBackground)
                    .clickable(enabled = !busy) {
                        busy = true; error = ""
                        scope.launch {
                            Api.createLobbyToken(user.token)
                                .onSuccess { tk ->
                                    val err = connectWithLobbyToken(tk.token)
                                    if (err == null) onEnterLobby(tk.token, tk.code, tk.lobbyType)
                                    else { error = err; busy = false }
                                }
                                .onFailure { error = it.message ?: "خطا"; busy = false }
                        }
                    }
                    .padding(18.dp)
            ) {
                Column {
                    Text("ساخت لابی جدید", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = YellowAccent)
                    Spacer(Modifier.height(4.dp))
                    Text("یک اتاق بساز و کدش رو برای دوستات بفرست", fontSize = 12.sp, color = MediumGrayText)
                }
            }
            // join card
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCardBackground)
                    .padding(18.dp)
            ) {
                Column {
                    Text("ورود با کد", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BlueAccent)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HamTextField(
                            joinCode,
                            "کد لابی",
                            { joinCode = it.uppercase().take(8) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                busy = true; error = ""
                                scope.launch {
                                    Api.joinLobbyToken(user.token, joinCode)
                                        .onSuccess { tk ->
                                            val err = connectWithLobbyToken(tk.token)
                                            if (err == null) onEnterLobby(tk.token, tk.code, tk.lobbyType)
                                            else { error = err; busy = false }
                                        }
                                        .onFailure { error = it.message ?: "خطا"; busy = false }
                                }
                            },
                            enabled = joinCode.length >= 4 && !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueAccent)
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF10131A))
                            else Text("ورود", color = Color(0xFF10131A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = RedAccent, fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))

        // ---------- active lobbies ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("لابی‌های فعال", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LightGrayTextC)
            Spacer(Modifier.weight(1f))
            Text(
                "به‌روزرسانی",
                color = BlueAccent, fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { fetchLobbies() }
                    .padding(6.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (loadingList && lobbies.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), color = YellowAccent)
            }
        } else if (lobbies.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("فعلاً هیچ لابی فعالی نیست — اولین نفر باش!", color = MediumGrayText, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(lobbies, key = { it.code }) { lobby ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCardBackground)
                            .border(1.dp, com.app.hamfilm.desktop.BorderGray, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape).background(GreenAccent)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "کد: ${lobby.code}",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LightGrayTextC
                            )
                            Text(
                                (if (lobby.lobbyType == "music") "لابی موزیک" else "لابی فیلم") +
                                        " • ${lobby.usersCount} نفر",
                                fontSize = 12.sp, color = MediumGrayText
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                busy = true
                                scope.launch {
                                    Api.joinLobbyToken(user.token, lobby.code)
                                        .onSuccess { tk ->
                                            val err = connectWithLobbyToken(tk.token)
                                            if (err == null) onEnterLobby(tk.token, tk.code, tk.lobbyType)
                                            else { error = err; busy = false }
                                        }
                                        .onFailure { error = it.message ?: "خطا"; busy = false }
                                }
                            },
                            enabled = !busy
                        ) { Text("پیوستن", color = GreenAccent, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

val LightGrayTextC = com.app.hamfilm.desktop.LightGrayText

/** centered placeholder text used in a couple of spots */
@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MediumGrayText, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}
