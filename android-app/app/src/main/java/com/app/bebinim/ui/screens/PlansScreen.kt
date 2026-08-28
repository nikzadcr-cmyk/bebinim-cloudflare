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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.bebinim.data.api.Plan
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.LightGrayText
import com.app.bebinim.ui.theme.MediumGrayText
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.PlansState

/** Subscription plans — same flow as original: buy redirects to the website. */
@Composable
fun PlansScreen(onBack: () -> Unit) {
    val viewModel: com.app.bebinim.viewmodel.PlanViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    var showPurchaseDialog by remember { mutableStateOf<Plan?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(44.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton2(onClick = onBack)
            Text("پلن‌های اشتراک", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhiteText, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is PlansState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = YellowAccent)
                }
            }
            is PlansState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(s.message, fontSize = 14.sp, color = MediumGrayText)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                    ) { Text("تلاش مجدد", color = Color(0xFF050C1A)) }
                }
            }
            is PlansState.Success -> {
                if (s.plans.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("هیچ پلنی موجود نیست", fontSize = 14.sp, color = LightGrayText)
                    }
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        for (plan in s.plans) {
                            PlanCard(plan = plan, onPurchaseClick = { showPurchaseDialog = plan })
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    showPurchaseDialog?.let { plan ->
        PurchaseDialog(plan = plan, onDismiss = { showPurchaseDialog = null })
    }
}

@Composable
private fun PlanCard(plan: Plan, onPurchaseClick: () -> Unit) {
    val isGold = plan.name.contains("طلایی", ignoreCase = true) || plan.name.contains("gold", ignoreCase = true)
    val accent = if (isGold) YellowAccent else CyanAccent

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(plan.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WhiteText)
            if (plan.description.isNotBlank()) {
                Text(plan.description, fontSize = 12.sp, color = MediumGrayText)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(plan.priceFormatted, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = accent)
                Spacer(Modifier.size(6.dp))
                Text("تومان", fontSize = 14.sp, color = LightGrayText, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("${plan.durationDays} روز", "تا ${plan.users} نفر").forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(accent, RoundedCornerShape(50))
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(feature, fontSize = 13.sp, color = LightGrayText)
                    }
                }
                plan.features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(accent, RoundedCornerShape(50))
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(feature, fontSize = 13.sp, color = LightGrayText)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onPurchaseClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("خرید اشتراک", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF050C1A))
            }
        }
    }
}

@Composable
private fun PurchaseDialog(plan: Plan, onDismiss: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("خرید از سایت", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = WhiteText)
                Spacer(Modifier.height(8.dp))
                Text(
                    "برای خرید اشتراک لطفاً از سایت ما اقدام کنید",
                    fontSize = 15.sp, color = LightGrayText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://app.bebinim.me/plan-upgrade")
                            )
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = YellowAccent)
                ) {
                    Text("برو به سایت", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF050C1A))
                }
                TextButton(onClick = onDismiss) {
                    Text("بستن", fontSize = 14.sp, color = LightGrayText)
                }
            }
        }
    }
}

@Composable
internal fun IconButton2(onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت", tint = WhiteText, modifier = Modifier.size(20.dp))
    }
}
