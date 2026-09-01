package com.app.hamfilm.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

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

/** Bundled DejaVu family — guaranteed Persian/Arabic glyphs on any distro. */
val HamFilmFontFamily: FontFamily by lazy {
    try {
        FontFamily(
            Font(resource = "hamfilm/fonts/DejaVuSans.ttf", weight = FontWeight.Normal),
            Font(resource = "hamfilm/fonts/DejaVuSans-Bold.ttf", weight = FontWeight.Bold),
            Font(resource = "hamfilm/fonts/DejaVuSans.ttf", weight = FontWeight.Medium),
            Font(resource = "hamfilm/fonts/DejaVuSans.ttf", weight = FontWeight.SemiBold),
        )
    } catch (_: Exception) {
        FontFamily.Default
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
