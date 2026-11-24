package com.bookandpeace.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bookandpeace.app.data.local.ReaderThemeMode

/**
 * Evidence-based Eye-Comfort Palettes:
 * - Zero pure #FFFFFF backgrounds (eliminates harsh blue-spectrum glare)
 * - Zero pure #000000 + #FFFFFF dark mode (prevents astigmatism halation / glowing text edges)
 */
data class ReaderPalette(
    val background: Color,
    val surface: Color,
    val barSurface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val secondaryAccent: Color,
    val outline: Color
)

fun readerPaletteFor(mode: ReaderThemeMode): ReaderPalette = when (mode) {
    // Best overall for prolonged e-book reading (Sepia Parchment #F4ECD8 + Soft Espresso #3B2E26)
    ReaderThemeMode.SEPIA -> ReaderPalette(
        background = Color(0xFFF4ECD8),
        surface = Color(0xFFF7F0DF),
        barSurface = Color(0xFFEBE0C7),
        text = Color(0xFF3B2E26),
        secondaryText = Color(0xFF6F5E51),
        accent = Color(0xFF5D4632),
        accentContainer = Color(0xFFE5D6BA),
        onAccentContainer = Color(0xFF2F2217),
        secondaryAccent = Color(0xFF9C5E26),
        outline = Color(0xFFCBBBA0)
    )

    // Soft Cream E-Ink (#FBF0D9 + Charcoal #2C2824)
    ReaderThemeMode.SOFT_CREAM -> ReaderPalette(
        background = Color(0xFFFBF0D9),
        surface = Color(0xFFFDF5E6),
        barSurface = Color(0xFFF1E4C8),
        text = Color(0xFF2C2824),
        secondaryText = Color(0xFF645D54),
        accent = Color(0xFF2B4C3F),
        accentContainer = Color(0xFFDCE7DC),
        onAccentContainer = Color(0xFF172D24),
        secondaryAccent = Color(0xFFA66425),
        outline = Color(0xFFD2C5AC)
    )

    // Soft Muted Sage Green (#D3E8D4 + Deep Forest #1D3523) — center of visible light spectrum
    ReaderThemeMode.EYE_CARE_GREEN -> ReaderPalette(
        background = Color(0xFFD5EAD6),
        surface = Color(0xFFDFEFE0),
        barSurface = Color(0xFFC5DFC7),
        text = Color(0xFF1E3424),
        secondaryText = Color(0xFF4A6450),
        accent = Color(0xFF234D34),
        accentContainer = Color(0xFFBBD9BF),
        onAccentContainer = Color(0xFF122B1C),
        secondaryAccent = Color(0xFF7F5B28),
        outline = Color(0xFFA5C2A8)
    )

    // Ergonomic Off-Black Charcoal (#1B1D1C + Muted Warm Gray #CEC8BC) — prevents halation blur
    ReaderThemeMode.CHARCOAL_DARK -> ReaderPalette(
        background = Color(0xFF1B1D1C),
        surface = Color(0xFF222523),
        barSurface = Color(0xFF262A28),
        text = Color(0xFFCEC8BC),
        secondaryText = Color(0xFF959086),
        accent = Color(0xFF93BEA9),
        accentContainer = Color(0xFF293C33),
        onAccentContainer = Color(0xFFD5E6DD),
        secondaryAccent = Color(0xFFD6A46E),
        outline = Color(0xFF3E4441)
    )

    // Deep Midnight Slate (#141B22 + Soft Warm Silver #C4CCD4) — gentle night reading
    ReaderThemeMode.MIDNIGHT_NIGHT -> ReaderPalette(
        background = Color(0xFF141B22),
        surface = Color(0xFF1B242D),
        barSurface = Color(0xFF202B36),
        text = Color(0xFFC5CDD5),
        secondaryText = Color(0xFF8B97A3),
        accent = Color(0xFF8DB4D4),
        accentContainer = Color(0xFF233545),
        onAccentContainer = Color(0xFFD3E4F2),
        secondaryAccent = Color(0xFFD4A774),
        outline = Color(0xFF354352)
    )
}

private fun colorSchemeFor(mode: ReaderThemeMode): ColorScheme {
    val p = readerPaletteFor(mode)
    return if (mode.isDark) {
        darkColorScheme(
            primary = p.accent,
            onPrimary = Color(0xFF111815),
            primaryContainer = p.accentContainer,
            onPrimaryContainer = p.onAccentContainer,
            secondary = p.secondaryAccent,
            onSecondary = Color(0xFF281806),
            secondaryContainer = Color(0xFF3D2E1D),
            onSecondaryContainer = Color(0xFFEAD4BA),
            background = p.background,
            onBackground = p.text,
            surface = p.surface,
            onSurface = p.text,
            surfaceVariant = p.barSurface,
            onSurfaceVariant = p.secondaryText,
            outline = p.outline
        )
    } else {
        lightColorScheme(
            primary = p.accent,
            onPrimary = p.surface,
            primaryContainer = p.accentContainer,
            onPrimaryContainer = p.onAccentContainer,
            secondary = p.secondaryAccent,
            onSecondary = p.surface,
            secondaryContainer = Color(0xFFEBDAC2),
            onSecondaryContainer = Color(0xFF3A2510),
            background = p.background,
            onBackground = p.text,
            surface = p.surface,
            onSurface = p.text,
            surfaceVariant = p.barSurface,
            onSurfaceVariant = p.secondaryText,
            outline = p.outline
        )
    }
}

val BookTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 31.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun BookAndPeaceTheme(
    eyeCareMode: ReaderThemeMode = ReaderThemeMode.SEPIA,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = colorSchemeFor(eyeCareMode),
        typography = BookTypography,
        content = content
    )
}

/**
 * Non-interactive warm amber blue-light filter + gentle dimmer overlay
 * applied uniformly across the app so the eye never gets hit by a sudden bright flash.
 */
@Composable
fun EyeComfortOverlay(
    blueLightWarmth: Float,
    screenDimmer: Float
) {
    if (blueLightWarmth > 0.005f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFA93A).copy(alpha = blueLightWarmth.coerceIn(0f, 0.35f) * 0.55f))
        )
    }
    if (screenDimmer > 0.005f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0D0B).copy(alpha = screenDimmer.coerceIn(0f, 0.48f)))
        )
    }
}
