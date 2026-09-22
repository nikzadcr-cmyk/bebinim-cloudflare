package com.app.hamfilm.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.hamfilm.desktop.ChipDark
import com.app.hamfilm.desktop.ChipStrokeColor
import com.app.hamfilm.desktop.CardStrokeColor
import com.app.hamfilm.desktop.DarkCardBackground
import com.app.hamfilm.desktop.GreenAccent
import com.app.hamfilm.desktop.HeaderGrad
import com.app.hamfilm.desktop.LightGrayText
import com.app.hamfilm.desktop.MediumGrayText
import com.app.hamfilm.desktop.SelectionBlue
import com.app.hamfilm.desktop.YellowAccent
import com.app.hamfilm.desktop.video.VideoEngine
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** friendly Persian label for a libvlc track description */
private fun humanTrackName(desc: String): String {
    val d = desc.trim()
    if (d.equals("Disable", ignoreCase = true) || d.equals("خاموش", ignoreCase = true)) return "هیچکدام"
    if (d.isBlank()) return "تراک"
    if (d.startsWith("Track ")) {
        val num = d.removePrefix("Track ").substringBefore(' ').trim()
        val rest = d.substringAfter('-', "").trim()
        return buildString {
            append("تراک ").append(num)
            if (rest.isNotBlank()) append(" — ").append(rest)
        }
    }
    return d
}

/**
 * Full video-settings PANEL — port of the Android VideoSettingsSheet:
 *  • تراک صدا (audio tracks, with "هیچکدام")
 *  • زیرنویس (subtitle tracks, disable, load external .srt/.vtt/.ass)
 *  • سرعت پخش (0.5x … 2x)
 *
 * Rendered IN-LAYOUT (between the video and the controls bar), NOT as a separate
 * dialog window — separate dialog windows get stuck BEHIND the main window on
 * KDE/X11 (focus-stealing prevention), which is why nothing appeared before.
 * In-layout = always visible, in windowed AND fullscreen mode.
 */
@Composable
fun VideoSettingsPanel(
    engine: VideoEngine,
    tracksVersion: Int,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DarkCardBackground)
            .border(1.dp, CardStrokeColor)
    ) {
        // ---- panel header ----
        Row(
            Modifier.fillMaxWidth().background(HeaderGrad).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Settings, contentDescription = null,
                tint = YellowAccent, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "تنظیمات پخش",
                color = LightGrayText,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "بستن", tint = MediumGrayText)
            }
        }

        VideoSettingsSections(engine, tracksVersion, onClose)
    }
}

@Composable
private fun VideoSettingsSections(
    engine: VideoEngine,
    tracksVersion: Int,
    onClose: () -> Unit
) {
    // local reload bump — re-reads tracks after the user loads an external subtitle
    var localVersion by remember { mutableStateOf(0) }
    val audio = remember(tracksVersion, localVersion) { engine.audioTracks() }
    val subs = remember(tracksVersion, localVersion) { engine.subtitleTracks() }
    var currentAudio by remember(tracksVersion, localVersion) { mutableStateOf(engine.currentAudioTrackId) }
    var currentSub by remember(tracksVersion, localVersion) { mutableStateOf(engine.currentSubtitleTrackId) }
    var currentRate by remember(tracksVersion) { mutableStateOf(engine.currentRate) }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
                // ---------------- audio ----------------
                SectionHeader(Icons.Filled.Audiotrack, "تراک صدا", com.app.hamfilm.desktop.BlueAccent)
                if (audio.isEmpty()) {
                    SettingsHint("تراک صدایی برای این فیلم پیدا نشد")
                } else {
                    audio.forEach { td ->
                        val selected = td.id() == currentAudio
                        TrackRow(
                            label = humanTrackName(td.description()),
                            selected = selected,
                            tint = com.app.hamfilm.desktop.BlueAccent
                        ) {
                            engine.setAudioTrack(td.id())
                            currentAudio = td.id()
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ---------------- subtitles ----------------
                SectionHeader(Icons.Filled.ClosedCaption, "زیرنویس", YellowAccent)
                if (subs.isEmpty()) {
                    SettingsHint("زیرنویسی داخل فیلم نیست — می‌توانی فایل زیرنویس بارگذاری کنی")
                } else {
                    subs.forEach { td ->
                        val selected = td.id() == currentSub
                        TrackRow(
                            label = humanTrackName(td.description()),
                            selected = selected,
                            tint = YellowAccent
                        ) {
                            engine.setSubtitleTrack(td.id())
                            currentSub = td.id()
                        }
                    }
                }
                TrackRow(
                    label = "بارگذاری فایل زیرنویس (srt / vtt / ass)…",
                    selected = false,
                    tint = GreenAccent
                ) {
                    val chooser = JFileChooser()
                    chooser.fileFilter = FileNameExtensionFilter(
                        "Subtitles (srt/vtt/ass)", "srt", "vtt", "ass", "ssa", "sub"
                    )
                    val r = chooser.showOpenDialog(null)
                    if (r == JFileChooser.APPROVE_OPTION) {
                        engine.addSubtitleFile(chooser.selectedFile.absolutePath)
                        localVersion++
                        currentSub = engine.currentSubtitleTrackId
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ---------------- speed ----------------
                SectionHeader(Icons.Filled.Speed, "سرعت پخش", GreenAccent)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                        val selected = kotlin.math.abs(currentRate - rate) < 0.01f
                        SpeedChip(
                            label = if (rate == 1f) "۱x" else "${rate}x",
                            selected = selected
                        ) {
                            engine.setRate(rate)
                            currentRate = rate
                        }
                    }
                }
            }
    }

@Composable
private fun SectionHeader(icon: ImageVector, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LightGrayText)
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val bg = when {
        selected -> SelectionBlue
        isHovered -> ChipDark
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                1.dp,
                if (selected) tint.copy(alpha = 0.55f) else ChipStrokeColor,
                RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) tint else Color.Transparent)
                .border(
                    1.5.dp,
                    if (selected) tint else MediumGrayText.copy(alpha = 0.6f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check, contentDescription = null,
                    tint = Color(0xFF10131A), modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, color = if (selected) LightGrayText else MediumGrayText)
    }
}

@Composable
private fun SpeedChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> YellowAccent
                    isHovered -> ChipDark
                    else -> ChipDark.copy(alpha = 0.55f)
                }
            )
            .border(
                1.dp,
                if (selected) YellowAccent else ChipStrokeColor,
                RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = hover, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFF10131A) else LightGrayText
        )
    }
}

@Composable
private fun SettingsHint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MediumGrayText,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ChipDark.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
