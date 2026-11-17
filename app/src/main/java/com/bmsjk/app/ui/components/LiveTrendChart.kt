package com.bmsjk.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.core.session.ChartMetric
import com.bmsjk.core.session.TrendSample

@Composable
fun LiveTrendChartCanvas(
    samples: List<TrendSample>,
    metric: ChartMetric,
    lineColor: Color,
    height: Dp = 84.dp,
    emptyText: String = ""
) {
    val values = samples.mapNotNull { sample ->
        when (metric) {
            ChartMetric.PACK_VOLTAGE -> sample.totalVoltageV
            ChartMetric.CURRENT -> sample.currentA
            ChartMetric.POWER -> sample.powerW
            ChartMetric.DELTA_MV -> sample.deltaCellMv
            ChartMetric.MAX_TEMPERATURE -> sample.maxTempC
        }
    }

    if (values.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emptyText, color = OledTokens.Muted, fontSize = 11.sp)
        }
        return
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val padY = 6.dp.toPx()
        val usableH = (h - padY * 2f).coerceAtLeast(4f)

        // Dashed midpoint baseline
        drawLine(
            color = Color(0x3894A3B8),
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
        )

        var minVal = Float.MAX_VALUE
        var maxVal = -Float.MAX_VALUE
        for (v in values) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }
        val span = (maxVal - minVal).let { if (it < 0.05f) 1f else it }

        val linePath = Path()
        val fillPath = Path()
        var lastX = 0f
        var lastY = h / 2f

        val count = values.size
        for (i in values.indices) {
            val x = if (count == 1) w else (i.toFloat() / (count - 1)) * w
            val norm = ((values[i] - minVal) / span).coerceIn(0f, 1f)
            val y = if (maxVal - minVal < 0.05f) h / 2f else (h - padY) - norm * usableH
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            lastX = x
            lastY = y
        }
        fillPath.lineTo(lastX, h)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.34f), lineColor.copy(alpha = 0.02f)),
                startY = 0f,
                endY = h
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color = lineColor,
            radius = 3.5.dp.toPx(),
            center = Offset(lastX, lastY)
        )
    }
}
