package com.app.bebinim.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bebinim.R
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.LightGrayText
import com.app.bebinim.ui.theme.MediumGrayText
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent

/**
 * Home — layout of the original app WITHOUT the "پشتیبانی", "رنکینگ"
 * and "مرکز آموزش" sections (removed by design).
 */
@Composable
fun HomeScreen(
    onNavigateToLobby: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // Logo
        Image(
            painter = painterResource(R.drawable.logo_type),
            contentDescription = "لوگو همفیلم",
            modifier = Modifier.height(72.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "تماشای گروهی فیلم و موزیک",
            fontSize = 13.sp,
            color = MediumGrayText
        )

        Spacer(Modifier.height(28.dp))

        // Main CTA card
        MainActionCard(
            title = "ساخت / ورود به لابی",
            subtitle = "در مرحله بعد نوع لابی را انتخاب کن",
            icon = { Icon(androidx.compose.material.icons.Icons.Filled.GroupAdd, null, tint = WhiteText, modifier = Modifier.size(28.dp)) },
            gradient = Brush.linearGradient(listOf(Color(0xFFFFB300), Color(0xFFFF8A00))),
            glow = Color(0xFFFFB300),
            onClick = onNavigateToLobby
        )

        Spacer(Modifier.height(16.dp))

        // Profile (full width — ranking, education and support sections were removed)
        FullWidthMenuCard(
            title = "حساب کاربری",
            icon = { Icon(androidx.compose.ui.res.painterResource(com.app.bebinim.R.drawable.ic_users), null, tint = WhiteText, modifier = Modifier.size(24.dp)) },
            gradient = Brush.linearGradient(listOf(Color(0xFF1E88E5), Color(0xFF1565C0))),
            onClick = onNavigateToProfile
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MainActionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    gradient: Brush,
    glow: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.background(gradient).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                icon()
                Column {
                    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhiteText)
                    Text(subtitle, fontSize = 13.sp, color = WhiteText.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun FullWidthMenuCard(
    title: String,
    icon: @Composable () -> Unit,
    gradient: Brush,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(Modifier.background(gradient).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                icon()
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WhiteText)
            }
        }
    }
}
