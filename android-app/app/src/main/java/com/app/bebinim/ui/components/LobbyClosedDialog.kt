package com.app.bebinim.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.app.bebinim.ui.theme.LightGrayText
import com.app.bebinim.ui.theme.YellowAccent

@Composable
fun LobbyClosedDialog(lobbyClosed: Boolean, onTimeout: () -> Unit) {
    if (!lobbyClosed) return
    Dialog(onDismissRequest = { /* non-cancellable */ }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = com.app.bebinim.ui.theme.SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("لابی بسته شد", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = YellowAccent)
                Text(
                    "این لابی توسط سازنده بسته شده است.\nدر حال انتقال به صفحه اصلی...",
                    fontSize = 14.sp,
                    color = LightGrayText
                )
            }
        }
    }
    // countdown auto-navigation
    androidx.compose.runtime.LaunchedEffect(lobbyClosed) {
        var count = 4
        while (count > 0) {
            kotlinx.coroutines.delay(1000)
            count--
        }
        onTimeout()
    }
}
