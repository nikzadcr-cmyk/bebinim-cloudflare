package com.app.bebinim.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.app.bebinim.viewmodel.CreateLobbyState
import com.app.bebinim.viewmodel.LobbyViewModel
import kotlinx.coroutines.delay

/**
 * Create/Join lobby — exact layout of the original CreateJoinLobbyScreenNew.
 */
@Composable
fun CreateJoinLobbyScreen(
    lobbyType: String,
    onBack: () -> Unit
) {
    val lobbyViewModel: LobbyViewModel = viewModel()
    val createLobbyState by lobbyViewModel.createLobbyState.collectAsState()
    val joinSuccess by lobbyViewModel.joinSuccess.collectAsState()
    val activeLobbies by lobbyViewModel.activeLobbies.collectAsState()
    val activeLobbiesLoading by lobbyViewModel.activeLobbiesLoading.collectAsState()
    var code by remember { mutableStateOf("") }
    var joiningCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        lobbyViewModel.clearState()
        lobbyViewModel.fetchActiveLobbies()
    }

    // navigate to lobby after successful join/create
    LaunchedEffect(joinSuccess) {
        if (joinSuccess.isNotBlank()) {
            val type = lobbyViewModel.lobbyInfo.value?.lobbyType ?: "movie"
            delay(150)
            LobbyNav.host?.navigateToLobby(joinSuccess, type)
            lobbyViewModel.clearState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(44.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton2(onClick = onBack)
            Spacer(Modifier.weight(1f))
        }

        // hero
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("تماشای گروهی", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("یک لابی بساز یا با کد وارد شو", fontSize = 13.sp, color = Color(0xFFB0B0B0))
        }

        Spacer(Modifier.height(24.dp))

        Text("ساخت لابی جدید", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE0E6F0))

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CreateLobbyCard(
                modifier = Modifier.weight(1f),
                title = "فیلم",
                subtitle = "تماشای فیلم\nو سریال",
                iconRes = com.app.bebinim.R.drawable.ic_movie,
                accent = Color(0xFFFF8A00),
                isLoading = createLobbyState is CreateLobbyState.Loading,
                enabled = createLobbyState !is CreateLobbyState.Loading
            ) {
                lobbyViewModel.createLobby("movie")
            }
            CreateLobbyCard(
                modifier = Modifier.weight(1f),
                title = "موزیک",
                subtitle = "گوش دادن\nهمزمان",
                iconRes = com.app.bebinim.R.drawable.ic_music_note,
                accent = Color(0xFF1DB954),
                isLoading = false,
                enabled = createLobbyState !is CreateLobbyState.Loading
            ) {
                lobbyViewModel.createLobby("music")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("یا", fontSize = 12.sp, color = Color(0xFFB0B0B0))
        Spacer(Modifier.height(20.dp))

        // join with code
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).background(Color(0xFF4A9EFF).copy(alpha = 0.1f), RoundedCornerShape(9.dp))
                            .border(1.dp, Color(0xFF4A9EFF).copy(alpha = 0.25f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Login, null, tint = Color(0xFF4A9EFF), modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("ورود با کد لابی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("کد ۸ کاراکتری لابی را وارد کن", fontSize = 11.sp, color = Color(0xFF718096))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("--------", fontSize = 18.sp, color = Color(0xFF4A5568), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 18.sp,
                        color = Color.White,
                        letterSpacing = 3.sp,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A9EFF),
                        unfocusedBorderColor = Color(0xFF4A9EFF).copy(alpha = 0.25f),
                        focusedContainerColor = Color(0x1A101B2E),
                        unfocusedContainerColor = Color(0x0AFFFFFF)
                    )
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        joiningCode = code
                        lobbyViewModel.joinLobby(code)
                    },
                    enabled = code.length == 8 && createLobbyState !is CreateLobbyState.Loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A9EFF))
                ) {
                    if (createLobbyState is CreateLobbyState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "ورود به لابی", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = if (code.length == 8) Color.White else Color(0xFF718096)
                        )
                    }
                }
            }
        }

        // error banner
        AnimatedVisibility(visible = createLobbyState is CreateLobbyState.Error) {
            (createLobbyState as? CreateLobbyState.Error)?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF4B5C).copy(alpha = 0.1f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF4B5C).copy(alpha = 0.35f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Color(0xFFFF4B5C), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(err.message, fontSize = 13.sp, color = Color(0xFFFF4B5C))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // active lobbies
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("لابی‌های فعال شما", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE0E6F0))
            Spacer(Modifier.weight(1f))
            if (activeLobbiesLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF4A9EFF))
            }
        }
        Spacer(Modifier.height(10.dp))

        if (activeLobbies.isEmpty() && !activeLobbiesLoading) {
            Text(
                "لابی فعالی وجود ندارد — یکی بساز!",
                fontSize = 12.sp,
                color = Color(0xFF718096),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        activeLobbies.forEach { lobby ->
            ActiveLobbyCard(
                lobby = lobby,
                isJoining = joiningCode == lobby.code,
                onClick = {
                    joiningCode = lobby.code
                    lobbyViewModel.joinLobby(lobby.code)
                }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(40.dp))
    }
}

/** navigation bridge — set by AppNavigation */
object LobbyNav {
    var host: LobbyNavHost? = null

    interface LobbyNavHost {
        fun navigateToLobby(code: String, type: String)
    }
}

@Composable
private fun CreateLobbyCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    iconRes: Int,
    accent: Color,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(120.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), color = accent)
            } else {
                Box(
                    Modifier.size(42.dp).background(accent.copy(alpha = 0.15f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(androidx.compose.ui.res.painterResource(iconRes), null, modifier = Modifier.size(22.dp), tint = accent)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 10.sp, color = Color(0xFF718096), lineHeight = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActiveLobbyCard(
    lobby: com.app.bebinim.data.api.ActiveLobby,
    isJoining: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(lobby.code, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (lobby.lobbyType == "music") "لابی موزیک" else "لابی فیلم",
                        fontSize = 12.sp, color = Color(0xFF718096)
                    )
                    if (lobby.is_owner) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "سازنده", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8A00),
                            modifier = Modifier.background(Color(0xFFFF8A00).copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Filled.People, null, tint = Color(0xFF718096), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${lobby.users.size} نفر", fontSize = 12.sp, color = Color(0xFF718096))
                }
            }
            if (isJoining) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF4A9EFF))
            } else {
                Text("پیوستن", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A9EFF))
            }
        }
    }
}
