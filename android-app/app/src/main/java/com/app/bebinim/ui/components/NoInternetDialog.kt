package com.app.bebinim.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.LightGrayText
import com.app.bebinim.ui.theme.MediumGrayText

@Composable
fun NoInternetDialog(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.WifiOff,
                contentDescription = "اینترنت قطع",
                tint = CyanAccent,
                modifier = Modifier.size(44.dp)
            )
            Text("اینترنت قطع است", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LightGrayText)
            Text(
                "لطفاً اتصال اینترنت خود را بررسی کنید و دوباره تلاش نمایید",
                fontSize = 14.sp,
                color = MediumGrayText
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text("متوجه شدم", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
