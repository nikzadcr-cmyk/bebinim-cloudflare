package com.app.hamfilm.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.app.hamfilm.desktop.net.HamSocket
import com.app.hamfilm.desktop.ui.LobbyScreen
import com.app.hamfilm.desktop.ui.LoginScreen
import com.app.hamfilm.desktop.ui.LobbiesScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsEnvironment

/**
 * HamFilm (همفیلم) — Linux desktop client.
 * Same backend, same basemsg-* WebSocket protocol, same voice framing as the Android app.
 */
fun main() = application {
    val windowState = rememberWindowState(width = 1280.dp, height = 760.dp)
    var fullscreen by remember { mutableStateOf(false) }
    var vlcAvailable by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        vlcAvailable = withContext(Dispatchers.IO) { VideoEngineProbe.available() }
    }

    Window(
        onCloseRequest = {
            HamSocket.getInstance().disconnect()
            exitApplication()
        },
        state = windowState,
        title = "همفیلم",
        onKeyEvent = { e ->
            // ESC exits fullscreen
            if (e.type == KeyEventType.KeyUp && e.key == Key.Escape && fullscreen) {
                fullscreen = false
                true
            } else false
        }
    ) {
        LaunchedEffect(fullscreen) {
            try {
                val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
                // find this app's main window (single-window app) — LocalWindow is internal
                // in this compose version, so look it up from the AWT window list
                val frame = java.awt.Window.getWindows()
                    .filterIsInstance<java.awt.Frame>()
                    .firstOrNull { it.isVisible }
                if (fullscreen) device.setFullScreenWindow(frame)
                else device.setFullScreenWindow(null)
            } catch (_: Exception) {}
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HamFilmTheme {
                when (vlcAvailable) {
                    null -> {
                        Splash()
                    }
                    false -> {
                        VlcMissingHelp()
                    }
                    true -> {
                        AppRoot(
                            fullscreen = fullscreen,
                            onToggleFullscreen = { fullscreen = !fullscreen }
                        )
                    }
                }
            }
        }
    }
}

private object VideoEngineProbe {
    fun available(): Boolean = try {
        com.app.hamfilm.desktop.video.VideoEngine.isVlcAvailable()
    } catch (_: Throwable) {
        false
    }
}

private enum class AppScreen { LOGIN, LOBBIES, LOBBY }

@Composable
private fun AppRoot(fullscreen: Boolean, onToggleFullscreen: () -> Unit) {
    var screen by remember { mutableStateOf(if (Store.session != null) AppScreen.LOBBIES else AppScreen.LOGIN) }
    var user by remember { mutableStateOf(Store.session) }
    var lobbyToken by remember { mutableStateOf("") }
    var lobbyCode by remember { mutableStateOf("") }
    var lobbyType by remember { mutableStateOf("movie") }

    when (screen) {
        AppScreen.LOGIN -> {
            LoginScreen(onLoggedIn = { token ->
                val claims = Store.decodeJwtClaims(token)
                val u = SessionUser(
                    token = token,
                    userId = claims["real_id"] ?: claims["id"] ?: claims["sub"] ?: "",
                    username = claims["username"] ?: "",
                    name = claims["name"] ?: "",
                    email = claims["email"] ?: ""
                )
                Store.saveSession(u)
                user = u
                screen = AppScreen.LOBBIES
            })
        }

        AppScreen.LOBBIES -> {
            val u = user
            if (u == null) {
                screen = AppScreen.LOGIN
            } else {
                LobbiesScreen(
                    user = u,
                    onEnterLobby = { token, code, type ->
                        lobbyToken = token
                        lobbyCode = code
                        lobbyType = type
                        screen = AppScreen.LOBBY
                    },
                    onLogout = {
                        Store.clearSession()
                        user = null
                        screen = AppScreen.LOGIN
                    }
                )
            }
        }

        AppScreen.LOBBY -> {
            val u = user
            if (u == null) {
                screen = AppScreen.LOGIN
            } else {
                LobbyScreen(
                    user = u,
                    lobbyCode = lobbyCode,
                    fullscreen = fullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onExit = {
                        lobbyToken = ""
                        lobbyCode = ""
                        screen = AppScreen.LOBBIES
                    }
                )
            }
        }
    }
}

@Composable
private fun Splash() {
    Column(
        Modifier.fillMaxSize().background(DarkNavyBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Res.logo?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(20.dp))
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("همفیلم", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = YellowAccent)
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.CircularProgressIndicator(
            color = YellowAccent, modifier = Modifier.size(30.dp), strokeWidth = 3.dp
        )
    }
}

@Composable
private fun VlcMissingHelp() {
    Column(
        Modifier.fillMaxSize().background(DarkNavyBackground).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Res.logo?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp))
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("کتابخانه پخش ویدیو پیدا نشد", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RedAccent)
        Spacer(Modifier.height(10.dp))
        Text(
            "همفیلم برای پخش فیلم از libvlc استفاده می‌کند.\n" +
                    "روی فدورا کافی است VLC را نصب کنی:\n\n" +
                    "sudo dnf install vlc\n" +
                    "(اگر مخزن RPM Fusion را نداری، اول نصبش کن)\n\n" +
                    "سپس برنامه را دوباره باز کن.",
            fontSize = 14.sp,
            color = LightGrayText,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { exitProcessSafely() },
            colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
        ) { Text("بستن", color = Color(0xFF10131A)) }
    }
}

private fun exitProcessSafely() {
    try { kotlin.system.exitProcess(0) } catch (_: Exception) {}
}
