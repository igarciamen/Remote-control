package ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Layered dark palette — deep indigo/slate tones, never pure black.
// Each surface is a clear lightness step above the previous one so the
// eye can tell background / card / elevated element apart at a glance.
val BackgroundDark = Color(0xFF141A2E)       // base window background
val SurfaceDark = Color(0xFF212A47)          // cards
val SurfaceDarkElevated = Color(0xFF2C375C)  // dialogs, hovered/elevated bits

val AccentPrimary = Color(0xFF9B8CFF)
val AccentPrimaryVariant = Color(0xFF7A68E0)
val AccentSecondary = Color(0xFF5EEAD4)      // reserved for the scrollbar only

// Text contrast against SurfaceDark (~212A47):
// TextPrimary ≈ 12.8:1, TextSecondary ≈ 6.6:1 — both pass WCAG AA comfortably.
val TextPrimary = Color(0xFFF6F7FC)
val TextSecondary = Color(0xFFAEB4D6)

val BorderSubtle = Color(0xFF465081)

val StatusSuccess = Color(0xFF5EE6A0)
val StatusWarning = Color(0xFFFFC168)
val StatusError = Color(0xFFFF8790)
val StatusNeutral = Color(0xFF9BA1C4)

// Typography tuned for a calmer, more refined feel: slightly lighter weights,
// gentle letter-spacing on headers, a touch more line height for readability.
private val AppTypography = Typography(
    h6 = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.2.sp,
        color = TextPrimary
    ),
    subtitle1 = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        color = TextPrimary
    ),
    subtitle2 = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.8.sp,
        color = TextSecondary
    ),
    body1 = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    caption = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.1.sp,
        lineHeight = 17.sp,
        color = TextSecondary
    ),
    button = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.4.sp
    )
)

private val AppDarkColors = darkColors(
    primary = AccentPrimary,
    primaryVariant = AccentPrimaryVariant,
    secondary = AccentSecondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color(0xFF102019),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = StatusError
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = AppDarkColors, typography = AppTypography, content = content)
}