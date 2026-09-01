package com.app.hamfilm.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp

// Exact palette of the original app (ui/theme/Color.kt)
val DarkNavyBackground = Color(0xFF050C1A)
val DarkCardBackground = Color(0xFF0E1928)
val DarkButtonBackground = Color(0xFF1C2735)
val YellowAccent = Color(0xFFFFC500)
val RedAccent = Color(0xFFFF4B5C)
val BlueAccent = Color(0xFF4A9EFF)
val GreenAccent = Color(0xFF22C55E)
val WhiteText = Color(0xFFFFFFFF)
val LightGrayText = Color(0xFFE0E6F0)
val MediumGrayText = Color(0xFFB0B0B0)
val DarkGrayText = Color(0xFF718096)
val BorderGray = Color(0xFF4A5568)
val SurfaceDark = Color(0xFF101B2E)
val CardDark = Color(0xFF0F1A2E)
val ChipDark = Color(0xFF1A2537)

// ---- polish palette (round: better graphics) ----
val ChipStrokeColor = Color(0xFF26344E)
val CardStrokeColor = Color(0xFF1D2A42)
val YellowLight = Color(0xFFFFDA6A)
val YellowDeep = Color(0xFFF0A500)
val BlueLight = Color(0xFF6FB6FF)
val BlueDeep = Color(0xFF2F7CD6)
val NeonPurple = Color(0xFF8B5CF6)
val SelectionBlue = Color(0x334A9EFF)

val AppBgGradient = Brush.verticalGradient(
    listOf(Color(0xFF081527), DarkNavyBackground, Color(0xFF03080F))
)
val YellowGrad = Brush.horizontalGradient(listOf(YellowLight, YellowAccent, YellowDeep))
val BlueGrad = Brush.horizontalGradient(listOf(BlueLight, BlueAccent, BlueDeep))
val HeaderGrad = Brush.horizontalGradient(listOf(Color(0xFF0A1526), DarkCardBackground, Color(0xFF0A1526)))
val OwnBubbleGrad = Brush.horizontalGradient(listOf(YellowLight, YellowAccent))
val NotifGrad = Brush.horizontalGradient(listOf(Color(0xFF182338), Color(0xFF121C2F)))

val ChipStroke: BorderStroke = BorderStroke(1.dp, ChipStrokeColor)
val CardStroke: BorderStroke = BorderStroke(1.dp, CardStrokeColor)

/**
 * Bundled type stack:
 *  1. Vazirmatn (وزیر) — beautiful Persian/Latin UI font, all weights
 *  2. Noto Color Emoji — makes chat emojis render in COLOR on Linux
 *  3. DejaVu Sans — last-resort fallback for odd symbols
 * All three participate in the glyph fallback chain (emoji hits the color font
 * before DejaVu, so no monochrome outlines).
 */
val HamFilmFontFamily: FontFamily by lazy {
    try {
        FontFamily(
            Font(resource = "hamfilm/fonts/Vazirmatn-Regular.ttf", weight = FontWeight.Normal),
            Font(resource = "hamfilm/fonts/Vazirmatn-Medium.ttf", weight = FontWeight.Medium),
            Font(resource = "hamfilm/fonts/Vazirmatn-SemiBold.ttf", weight = FontWeight.SemiBold),
            Font(resource = "hamfilm/fonts/Vazirmatn-Bold.ttf", weight = FontWeight.Bold),
            // color emoji BEFORE the mono chrome DejaVu so emoji always come out colored
            Font(resource = "hamfilm/fonts/NotoColorEmoji.ttf", weight = FontWeight.Normal),
            Font(resource = "hamfilm/fonts/DejaVuSans.ttf", weight = FontWeight.Normal),
            Font(resource = "hamfilm/fonts/DejaVuSans-Bold.ttf", weight = FontWeight.Bold),
        )
    } catch (_: Exception) {
        try {
            FontFamily(
                Font(resource = "hamfilm/fonts/DejaVuSans.ttf", weight = FontWeight.Normal),
                Font(resource = "hamfilm/fonts/DejaVuSans-Bold.ttf", weight = FontWeight.Bold),
            )
        } catch (_: Exception) {
            FontFamily.Default
        }
    }
}

private val baseTypography = androidx.compose.material3.Typography().let { t ->
    fun style(s: TextStyle) = s.copy(
        fontFamily = HamFilmFontFamily,
        color = LightGrayText
    )
    t.copy(
        displayLarge = style(t.displayLarge),
        displayMedium = style(t.displayMedium),
        displaySmall = style(t.displaySmall),
        headlineLarge = style(t.headlineLarge),
        headlineMedium = style(t.headlineMedium),
        headlineSmall = style(t.headlineSmall),
        titleLarge = style(t.titleLarge),
        titleMedium = style(t.titleMedium),
        titleSmall = style(t.titleSmall),
        bodyLarge = style(t.bodyLarge),
        bodyMedium = style(t.bodyMedium),
        bodySmall = style(t.bodySmall),
        labelLarge = style(t.labelLarge),
        labelMedium = style(t.labelMedium),
        labelSmall = style(t.labelSmall)
    )
}

@Composable
fun HamFilmTheme(content: @Composable () -> Unit) {
    // single dark theme — matches the Android app
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = YellowAccent,
            onPrimary = Color(0xFF10131A),
            secondary = BlueAccent,
            background = DarkNavyBackground,
            onBackground = LightGrayText,
            surface = DarkCardBackground,
            onSurface = LightGrayText,
            surfaceVariant = ChipDark,
            onSurfaceVariant = MediumGrayText,
            error = RedAccent
        ),
        typography = baseTypography,
        content = content
    )
}
