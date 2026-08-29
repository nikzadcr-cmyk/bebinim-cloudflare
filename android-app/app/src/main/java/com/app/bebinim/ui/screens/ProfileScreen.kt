package com.app.bebinim.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.DarkGrayText
import com.app.bebinim.ui.theme.GreenAccent
import com.app.bebinim.ui.theme.LightGrayText
import com.app.bebinim.ui.theme.MediumGrayText
import com.app.bebinim.ui.theme.RedAccent
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    val viewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val info = viewModel.userInfo.collectAsState().value

    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = WhiteText, modifier = Modifier.size(20.dp))
            }
            Text("حساب کاربری", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhiteText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(44.dp))
        }

        Spacer(Modifier.height(16.dp))

        // hero
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(CyanAccent.copy(alpha = 0.15f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (info.username.takeIf { it.isNotBlank() } ?: "کاربر").take(1).uppercase(),
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CyanAccent
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(info.username.ifBlank { "کاربر" }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText)
        }

        Spacer(Modifier.height(20.dp))

        SectionLabel("اطلاعات حساب")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(Icons.Filled.Person, "نام کاربری", info.username, CyanAccent)
                InfoRow(Icons.Filled.Badge, "نام", info.name ?: "", YellowAccent)
                InfoRow(Icons.Filled.Email, "ایمیل", info.email ?: "", GreenAccent)
                if (info.username.isBlank() && info.name.isNullOrBlank() && info.email.isNullOrBlank()) {
                    Text("اطلاعات در دسترس نیست", fontSize = 14.sp, color = DarkGrayText)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("وضعیت اشتراک")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.WorkspacePremium, null, tint = YellowAccent.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(10.dp))
                Text("اشتراک فعالی ندارید", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LightGrayText)
                Text(
                    "با خرید اشتراک به لابی‌های نامحدود دسترسی داشته باشید",
                    fontSize = 13.sp, color = MediumGrayText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                ) {
                    Icon(Icons.Filled.ShoppingCart, null, tint = Color(0xFF050C1A), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("خرید اشتراک", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF050C1A))
                }
            }
        }

        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("خروج از حساب کاربری", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RedAccent)
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color(0xFF0E1928),
            title = { Text("خروج از حساب", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WhiteText) },
            text = { Text("آیا می‌خواهید از حساب کاربری خود خارج شوید؟", fontSize = 14.sp, color = MediumGrayText) },
            confirmButton = {
                TextButton(onClick = { showLogoutDialog = false; onLogout() }) {
                    Text("بله، خارج شو", color = RedAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("انصراف", color = CyanAccent)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MediumGrayText,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, fontSize = 14.sp, color = MediumGrayText)
        Spacer(Modifier.weight(1f))
        Text(value.ifBlank { "—" }, fontSize = 14.sp, color = WhiteText, fontWeight = FontWeight.Medium)
    }
}
