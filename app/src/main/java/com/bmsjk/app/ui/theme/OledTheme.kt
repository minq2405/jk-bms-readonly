package com.bmsjk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Strictly OLED Dark tokens matching tools/app_preview.html & ui_layout.json.
 */
object OledTokens {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF121212)
    val CardElevated = Color(0xFF181C24)
    val CardBorder = Color(0xFF262D3D)

    val Text = Color(0xFFFFFFFF)
    val Muted = Color(0xFFB8C4D4)

    val Accent = Color(0xFF22D3EE)
    val AccentStrong = Color(0xFF0891B2)
    val AccentSoft = Color(0x2422D3EE)

    val ChargeGreen = Color(0xFF22C55E)
    val ChargeGreenSoft = Color(0x2422C55E)
    val ChargeGreenText = Color(0xFF4ADE80)
    val ChargeGreenBorder = Color(0x6B22C55E)

    val DischargeAmber = Color(0xFFF59E0B)
    val AmberStrong = Color(0xFFD97706)
    val AmberSoft = Color(0x26F59E0B)
    val AmberText = Color(0xFFFBBF24)
    val AmberBorder = Color(0x73F59E0B)

    val DangerRed = Color(0xFFEF4444)
    val DangerSoft = Color(0x26EF4444)

    val TrackMuted = Color(0x3894A3B8)
    val CardRadius = 14.dp
}

private val OledColorScheme = darkColorScheme(
    primary = OledTokens.Accent,
    onPrimary = Color.Black,
    secondary = OledTokens.ChargeGreen,
    onSecondary = Color.Black,
    background = OledTokens.Bg,
    onBackground = OledTokens.Text,
    surface = OledTokens.Card,
    onSurface = OledTokens.Text,
    surfaceVariant = OledTokens.CardElevated,
    onSurfaceVariant = OledTokens.Muted,
    outline = OledTokens.CardBorder,
    error = OledTokens.DangerRed,
    onError = Color.White
)

fun monoStyle(
    size: TextUnit = 13.sp,
    weight: FontWeight = FontWeight.Bold,
    color: Color = OledTokens.Text
): TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontFeatureSettings = "tnum",
    fontSize = size,
    fontWeight = weight,
    color = color
)

@Composable
fun JkBmsOledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OledColorScheme,
        content = content
    )
}
