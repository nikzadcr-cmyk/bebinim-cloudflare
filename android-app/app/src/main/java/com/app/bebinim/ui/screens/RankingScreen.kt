package com.app.bebinim.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import com.app.bebinim.data.api.LeaderboardEntry
import com.app.bebinim.ui.theme.CyanAccent
import com.app.bebinim.ui.theme.MediumGrayText
import com.app.bebinim.ui.theme.WhiteText
import com.app.bebinim.ui.theme.YellowAccent
import com.app.bebinim.viewmodel.RankingState
import com.app.bebinim.viewmodel.RankingViewModel
import com.app.bebinim.viewmodel.UserProfileState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(onBack: () -> Unit) {
    val viewModel: RankingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val profileState by viewModel.userProfileState.collectAsState()

    var selectedUserId by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF050C1A), Color(0xFF0A0E27), Color(0xFF050C1A)))
            )
    ) {
        Spacer(Modifier.height(44.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton2(onClick = onBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("رنکینگ", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WhiteText)
                Text("لیدربورد برترین کاربران", fontSize = 12.sp, color = WhiteText.copy(alpha = 0.45f))
            }
            Spacer(Modifier.size(44.dp))
        }
        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            is RankingState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = YellowAccent)
                        Spacer(Modifier.height(10.dp))
                        Text("در حال بارگذاری...", fontSize = 13.sp, color = WhiteText.copy(alpha = 0.5f))
                    }
                }
            }
            is RankingState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, fontSize = 14.sp, color = WhiteText.copy(alpha = 0.6f))
                }
            }
            is RankingState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // podium (top 3)
                    item {
                        PodiumRow(entries = s.leaderboard.take(3))
                    }
                    // rest
                    items(s.leaderboard.drop(3)) { entry ->
                        LeaderboardRow(entry) {
                            selectedUserId = entry.userId
                            viewModel.loadUserProfile(entry.userId)
                        }
                    }
                    // my rank
                    s.myRank?.let { myRank ->
                        item {
                            Spacer(Modifier.height(8.dp))
                            MyRankCard(myRank)
                        }
                    }
                    // rank tiers
                    item {
                        Spacer(Modifier.height(8.dp))
                        RankTiersCard(s.allRanks)
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    // user profile bottom sheet (embedded WebView)
    if (selectedUserId != null) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedUserId = null
                viewModel.clearProfile()
            },
            containerColor = Color(0xFF0B0F1E)
        ) {
            when (val p = profileState) {
                is UserProfileState.Loading -> {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        Text("در حال بارگذاری...", fontSize = 13.sp, color = WhiteText.copy(alpha = 0.5f))
                    }
                }
                is UserProfileState.Error -> {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        Text(p.message, fontSize = 13.sp, color = Color(0xFFFF6B81))
                    }
                }
                is UserProfileState.Success -> {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                setBackgroundColor(android.graphics.Color.parseColor("#0B0F1E"))
                                webViewClient = WebViewClient()
                                loadUrl(p.url)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(420.dp)
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun PodiumRow(entries: List<LeaderboardEntry>) {
    val colors = listOf(Color(0xFFFFD700), Color(0xFFC0C0C0), Color(0xFFCD7F32))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // 2nd — 1st — 3rd
        PodiumCard(entries.getOrNull(1), colors[1], Modifier.weight(1f))
        PodiumCard(entries.getOrNull(0), colors[0], Modifier.weight(1f), isFirst = true)
        PodiumCard(entries.getOrNull(2), colors[2], Modifier.weight(1f))
    }
}

@Composable
private fun PodiumCard(entry: LeaderboardEntry?, accent: Color, modifier: Modifier, isFirst: Boolean = false) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (entry != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(if (isFirst) 48.dp else 40.dp).border(2.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            entry.displayName.take(1).uppercase(),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        entry.displayName,
                        fontSize = if (isFirst) 11.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WhiteText,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text("${entry.totalHours}h", fontSize = 10.sp, color = accent)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, onClick: () -> Unit) {
    val rankColor = try {
        if (entry.rankColor.startsWith("#")) Color(android.graphics.Color.parseColor(entry.rankColor)) else CyanAccent
    } catch (_: Exception) {
        CyanAccent
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${entry.position}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WhiteText.copy(alpha = 0.8f), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            Box(
                Modifier.size(36.dp).border(1.dp, rankColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(entry.displayName.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = rankColor)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WhiteText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.rankName, fontSize = 10.sp, color = rankColor)
            }
            Text("${entry.totalHours}h", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = rankColor)
        }
    }
}

@Composable
private fun MyRankCard(myRank: com.app.bebinim.data.api.MyRankResponse) {
    val accent = try {
        if (myRank.rank.color.startsWith("#")) Color(android.graphics.Color.parseColor(myRank.rank.color)) else YellowAccent
    } catch (_: Exception) {
        YellowAccent
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF16213E),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("رنک شما", fontSize = 11.sp, color = WhiteText.copy(alpha = 0.45f))
            Text(myRank.displayName, fontSize = 13.sp, color = WhiteText.copy(alpha = 0.7f))
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${myRank.position ?: "-"}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = YellowAccent)
                Spacer(Modifier.width(8.dp))
                Text("جایگاه", fontSize = 10.sp, color = WhiteText.copy(alpha = 0.4f))
                Spacer(Modifier.weight(1f))
                Text(myRank.rank.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                StatChip("مجموع ساعت", "${myRank.rank.totalHours}", accent)
                Spacer(Modifier.width(8.dp))
                if (!myRank.rank.isMaxRank) {
                    StatChip("تا ${myRank.rank.nextRank}", "${myRank.rank.hoursToNext} ساعت", WhiteText.copy(alpha = 0.6f))
                } else {
                    StatChip("وضعیت", "حداکثر رنک ✓", YellowAccent)
                }
            }
            Spacer(Modifier.height(10.dp))
            val progress by animateFloatAsState(myRank.rank.progress / 100f, label = "rank-progress")
            Box(
                Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).height(6.dp)
                        .background(Brush.horizontalGradient(listOf(accent, YellowAccent)), RoundedCornerShape(50))
                )
            }
            Text("%${myRank.rank.progress} پیشرفت", fontSize = 11.sp, color = WhiteText.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: ", fontSize = 10.sp, color = MediumGrayText)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun RankTiersCard(tiers: List<com.app.bebinim.data.api.RankTier>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1928))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("مراتب رنکینگ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WhiteText)
            tiers.forEach { tier ->
                val color = try {
                    Color(android.graphics.Color.parseColor(tier.color))
                } catch (_: Exception) {
                    Color(0xFF8D99AE)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(tier.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                    }
                    Spacer(Modifier.weight(1f))
                    val range = if (tier.maxHours != null) "${tier.minHours} – ${tier.maxHours} ساعت" else "${tier.minHours}+ ساعت"
                    Text(range, fontSize = 11.sp, color = WhiteText.copy(alpha = 0.45f))
                }
            }
        }
    }
}
