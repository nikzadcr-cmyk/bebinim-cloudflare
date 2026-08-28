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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bebinim.ui.theme.DarkGrayText
import com.app.bebinim.ui.theme.LightGrayText
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.MyPlansState
import com.app.bebinim.viewmodel.MyPlansViewModel

@Composable
fun MyPlansScreen(onBack: () -> Unit) {
    val viewModel: MyPlansViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var transferring by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton2(onClick = onBack)
            Text("پلن‌های من", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhiteText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is MyPlansState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = YellowAccent)
                }
            }
            is MyPlansState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(s.message, fontSize = 14.sp, color = LightGrayText)
                }
            }
            is MyPlansState.Success -> {
                val planStatus = s.planStatus
                if (planStatus.hasActivePlan) {
                    ActivePlanContent(planStatus)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(60.dp))
                        Text("شما پلن فعالی ندارید", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WhiteText)
                        Spacer(Modifier.height(10.dp))
                        Text("برای خرید پلن روی دکمه زیر کلیک کنید", fontSize = 15.sp, color = LightGrayText, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("مستقیم به صفحه خرید پلن منتقل می‌شوید", fontSize = 14.sp, color = LightGrayText.copy(alpha = 0.8f))
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                transferring = true
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://app.bebinim.me/plan-upgrade")
                                    )
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                                transferring = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                        ) {
                            if (transferring) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF050C1A))
                            } else {
                                Icon(Icons.Filled.ShoppingCart, null, tint = Color(0xFF050C1A), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (transferring) "در حال انتقال..." else "خرید پلن",
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF050C1A)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivePlanContent(planStatus: com.app.bebinim.data.api.PlanStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
    ) {
        Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("پلن فعال شما", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WhiteText)
            PlanInfoRow(Icons.Filled.ShoppingCart, "نام پلن", planStatus.planName.ifBlank { "نامشخص" }, YellowAccent)
            PlanInfoRow(Icons.Filled.DateRange, "روزهای باقیمانده", "${planStatus.daysRemaining} روز", CyanAccent2)
            PlanInfoRow(Icons.Filled.Person, "تعداد کاربران", "تا ${planStatus.planDetails?.users ?: 0} نفر", GreenAccent2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("وضعیت", fontSize = 14.sp, color = LightGrayText)
                Spacer(Modifier.weight(1f))
                Text(planStatus.message, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = YellowAccent)
            }
        }
    }
}

@Composable
private fun PlanInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, fontSize = 14.sp, color = LightGrayText)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WhiteText)
    }
}

// local aliases to avoid import clutter
private val CyanAccent2 = Color(0xFF4A9EFF)
private val GreenAccent2 = Color(0xFF22C55E)
