package com.bmsjk.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.ui.theme.monoStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SocRingCanvas(
    socPercent: Int?,
    totalVoltageV: Float,
    size: Dp = 76.dp
) {
    val soc = (socPercent ?: 0).coerceIn(0, 100)
    val startColor = when {
        soc <= 20 -> OledTokens.DangerRed
        soc <= 40 -> OledTokens.DischargeAmber
        else -> OledTokens.Accent
    }
    val endColor = when {
        soc <= 20 -> OledTokens.DangerRed
        soc <= 40 -> OledTokens.DischargeAmber
        else -> OledTokens.ChargeGreen
    }
    val compact = size < 64.dp

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = (size.toPx() * 0.092f).coerceIn(4f, 11f)
            val inset = strokePx / 2f + 2f
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = OledTokens.TrackMuted,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (soc > 0) {
                drawArc(
                    brush = Brush.linearGradient(listOf(startColor, endColor)),
                    startAngle = -90f,
                    sweepAngle = (soc / 100f) * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = AppIcons.Bolt,
                contentDescription = null,
                tint = startColor,
                modifier = Modifier.size(if (compact) 8.dp else 10.dp)
            )
            Text(
                text = socPercent?.let { "$it%" } ?: "--%",
                style = monoStyle(
                    size = if (compact) 12.sp else 15.sp,
                    weight = FontWeight.Bold,
                    color = OledTokens.Text
                )
            )
            Text(
                text = String.format(Locale.US, "%.1fV", totalVoltageV),
                style = monoStyle(
                    size = if (compact) 8.sp else 9.sp,
                    weight = FontWeight.SemiBold,
                    color = OledTokens.Muted
                )
            )
        }
    }
}

@Composable
fun PowerArcCanvas(
    currentA: Float?,
    powerW: Float?,
    width: Dp = 94.dp,
    height: Dp = 54.dp,
    maxCurrentRefA: Float = 60f
) {
    val cur = currentA ?: 0f
    val pwr = powerW ?: 0f
    val ratio = (abs(cur) / maxCurrentRefA.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val arcColor = when {
        cur > 0.2f -> OledTokens.ChargeGreen
        cur < -0.2f -> OledTokens.DischargeAmber
        else -> OledTokens.Accent
    }
    val compact = height < 48.dp

    Box(
        modifier = Modifier.size(width, height),
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.size(width, height)) {
            val strokePx = if (compact) 6.dp.toPx() else 7.dp.toPx()
            val radius = (this.size.width * 0.39f).coerceAtMost(this.size.height * 0.78f)
            val cx = this.size.width / 2f
            val cy = this.size.height - strokePx * 0.7f
            val topLeft = Offset(cx - radius, cy - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            drawArc(
                color = OledTokens.TrackMuted,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (ratio > 0.01f) {
                drawArc(
                    color = arcColor,
                    startAngle = 180f,
                    sweepAngle = 180f * ratio,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                text = currentA?.let {
                    String.format(Locale.US, "%s%.1fA", if (it >= 0f) "+" else "", it)
                } ?: "--A",
                style = monoStyle(
                    size = if (compact) 11.5.sp else 12.5.sp,
                    weight = FontWeight.Bold,
                    color = arcColor
                )
            )
            Text(
                text = powerW?.let {
                    val w = it.roundToInt()
                    "${if (w >= 0) "+" else ""}${w}W"
                } ?: "--W",
                style = monoStyle(
                    size = if (compact) 8.5.sp else 9.sp,
                    weight = FontWeight.SemiBold,
                    color = OledTokens.Muted
                )
            )
        }
    }
}
