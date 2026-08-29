package com.app.bebinim.ui.components

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HighlightArea(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val radius: Int
)

data class GuideStep(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val highlightArea: HighlightArea? = null
)

private const val PREFS_NAME = "lobby_guide_prefs"
private const val KEY_LOBBY_GUIDE_VERSION = "lobby_guide_version_shown"
private const val CURRENT_GUIDE_VERSION = 1

fun hasSeenLobbyGuide(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_LOBBY_GUIDE_VERSION, 0) >= CURRENT_GUIDE_VERSION

fun setLobbyGuideShown(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putInt(KEY_LOBBY_GUIDE_VERSION, CURRENT_GUIDE_VERSION).apply()
}

private val GuideDialogBackground: Brush =
    Brush.verticalGradient(listOf(Color(0xFF1A2440), Color(0xFF10182C)))
private val GuideDialogBorder: Color = Color(0xFF4A9EFF).copy(alpha = 0.35f)

/** The original 8-step lobby guide (radio step replaced with web mode — radio removed by design). */
fun movieLobbyGuideSteps(): List<GuideStep> = listOf(
    GuideStep(
        "welcome", "به لابی خوش آمدید!",
        "اینجا می‌تونید با دوستاتون فیلم و ویدیو تماشا کنید.\nبذار قدم به قدم راهنماییت کنم!",
        Icons.Filled.Movie
    ),
    GuideStep(
        "playback_mode_link", "حالت پخش: لینک مستقیم",
        "با این حالت می‌تونید لینک فیلم یا ویدیو رو وارد کنید.\nلینک مستقیم MP4، MKV و سایر فرمت‌های ویدیویی.",
        Icons.Filled.Link,
        HighlightArea(12, 120, 0, 0, 12)
    ),
    GuideStep(
        "playback_mode_web", "حالت پخش: وب",
        "با این حالت می‌تونید یک صفحه وب یا ویدیوی آپارات رو باز کنید.\nهمه اعضای لابی همزمان همون صفحه رو می‌بینند!",
        Icons.Filled.Language,
        HighlightArea(120, 120, 0, 0, 12)
    ),
    GuideStep(
        "playback_mode_file", "حالت پخش: فایل مشترک",
        "اگر فایل فیلم رو دارید، از این حالت استفاده کنید.\nهمه اعضا باید فایل یکسان رو از گوشی خودشون انتخاب کنن تا همزمان پخش بشه.",
        Icons.Filled.Folder,
        HighlightArea(228, 120, 0, 0, 12)
    ),
    GuideStep(
        "video_player", "پخش‌کننده ویدیو",
        "ویدیو اینجا پخش میشه و همه اعضای لابی همزمان میبینند.\nمیتونید متوقف کنید، پخش کنید و جلو یا عقب ببرید.",
        Icons.Filled.PlayCircle,
        HighlightArea(96, 215, 0, 0, 12)
    ),
    GuideStep(
        "toolbar_top", "دکمه‌های اصلی",
        "چهار دکمه اصلی لابی:\n\nچت: پیام نوشتاری با اعضا\nکاربران: لیست اعضا\nدعوت: اشتراک کد لابی\nمیکروفون: صحبت با اعضا",
        Icons.Filled.GridView,
        HighlightArea(318, 120, 0, 0, 12)
    ),
    GuideStep(
        "toolbar_bottom", "دکمه‌های مدیریت",
        "سه دکمه مدیریتی:\n\nراهنما: آموزش و راهنما\nبستن لابی: فقط سازنده میتونه لابی رو ببنده\nخروج: ترک لابی",
        Icons.Filled.Apps,
        HighlightArea(450, 100, 0, 0, 18)
    ),
    GuideStep(
        "finish", "همه چیز آماده است",
        "حالا میتونید با دوستاتون فیلم ببینید.\nکد لابی رو برای دوستات بفرست تا وارد بشن.\n\nاگه سوالی داشتید از بخش راهنما میتونید با ما در ارتباط باشید.",
        Icons.Filled.Celebration
    )
)

/** Fullscreen spotlight overlay with a tooltip card, like the original. */
@Composable
fun LobbyOnboardingGuide(steps: List<GuideStep>, onDismiss: () -> Unit) {
    var currentStep by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val step = steps.getOrNull(currentStep) ?: return
    val isLastStep = currentStep == steps.lastIndex

    val pulse = rememberInfiniteTransition(label = "guidePulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(enabled = false) { }
    ) {
        // highlight ring (fixed-position approximation of the original HighlightArea)
        step.highlightArea?.let { area ->
            Box(
                modifier = Modifier
                    .padding(top = (area.y / 2).dp, start = 16.dp)
                    .size(64.dp)
                    .border(2.dp, Color(0xFF4A9EFF).copy(alpha = pulseAlpha), RoundedCornerShape(area.radius.dp))
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .fillMaxWidth()
                .background(GuideDialogBackground, RoundedCornerShape(20.dp))
                .border(1.dp, GuideDialogBorder, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4A9EFF).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(step.icon, null, tint = Color(0xFF4A9EFF), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                step.title,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                step.description,
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                steps.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == currentStep) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentStep) Color(0xFF4A9EFF)
                                else Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("رد کردن", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChatButtonGradient)
                        .clickable {
                            if (isLastStep) onDismiss() else currentStep++
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isLastStep) "شروع می‌کنیم!" else "بعدی",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
            }
        }
    }
}
