package com.bmsjk.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.ui.theme.monoStyle
import com.bmsjk.core.session.ChartMetric
import com.bmsjk.core.session.TrendSample
import java.util.Locale

data class ChartAxisBounds(
    val min: Float,
    val max: Float
) {
    val span: Float get() = (max - min).coerceAtLeast(1e-4f)
}

object ChartAxisScaler {

    fun downsample(values: List<Float>, maxPoints: Int = 96): List<Float> {
        if (values.size <= maxPoints || maxPoints < 2) return values
        val bodyCount = values.size - 1
        val targetBuckets = maxPoints - 1
        val out = ArrayList<Float>(maxPoints)
        for (b in 0 until targetBuckets) {
            val start = (b * bodyCount) / targetBuckets
            val end = (((b + 1) * bodyCount) / targetBuckets).coerceAtLeast(start + 1)
            var sum = 0.0
            for (i in start until end) {
                sum += values[i]
            }
            out.add((sum / (end - start)).toFloat())
        }
        // Always preserve the exact latest telemetry reading at the right tip.
        out.add(values.last())
        return out
    }

    fun computeBounds(values: List<Float>, metric: ChartMetric): ChartAxisBounds {
        val finite = values.filter { it.isFinite() }
        if (finite.isEmpty()) return ChartAxisBounds(0f, 1f)
        var rawMin = Float.MAX_VALUE
        var rawMax = -Float.MAX_VALUE
        for (v in finite) {
            if (v < rawMin) rawMin = v
            if (v > rawMax) rawMax = v
        }

        if (metric == ChartMetric.DELTA_MV) {
            val minDeltaSpanMv = 20f // 0.020 V minimum scale so 1-2mV ADC jitter does not fill 100% height
            val top = if (rawMax <= minDeltaSpanMv) minDeltaSpanMv else rawMax * 1.1f
            return ChartAxisBounds(0f, top)
        }

        val minSpan = when (metric) {
            ChartMetric.PACK_VOLTAGE -> 2.0f
            ChartMetric.CURRENT -> 5.0f
            ChartMetric.POWER -> 200.0f
            ChartMetric.MAX_TEMPERATURE -> 5.0f
            ChartMetric.DELTA_MV -> 20.0f
        }

        val rawSpan = rawMax - rawMin
        return if (rawSpan < minSpan) {
            val mid = (rawMin + rawMax) / 2f
            ChartAxisBounds(mid - minSpan / 2f, mid + minSpan / 2f)
        } else {
            val pad = rawSpan * 0.08f
            ChartAxisBounds(rawMin - pad, rawMax + pad)
        }
    }

    fun formatBound(value: Float, metric: ChartMetric): String = when (metric) {
        ChartMetric.PACK_VOLTAGE -> String.format(Locale.US, "%.1fV", value)
        ChartMetric.CURRENT -> String.format(Locale.US, "%.1fA", value)
        ChartMetric.POWER -> String.format(Locale.US, "%.0fW", value)
        ChartMetric.DELTA_MV -> String.format(Locale.US, "%.3fV", value / 1000f)
        ChartMetric.MAX_TEMPERATURE -> String.format(Locale.US, "%.1f°C", value)
    }
}

@Composable
fun LiveTrendChartCanvas(
    samples: List<TrendSample>,
    metric: ChartMetric,
    lineColor: Color,
    height: Dp = 84.dp,
    emptyText: String = ""
) {
    val rawValues = samples.mapNotNull { sample ->
        when (metric) {
            ChartMetric.PACK_VOLTAGE -> sample.totalVoltageV
            ChartMetric.CURRENT -> sample.currentA
            ChartMetric.POWER -> sample.powerW
            ChartMetric.DELTA_MV -> sample.deltaCellMv
            ChartMetric.MAX_TEMPERATURE -> sample.maxTempC
        }
    }.filter { it.isFinite() }

    if (rawValues.isEmpty()) {
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

    val plotted = ChartAxisScaler.downsample(rawValues, maxPoints = 96)
    val bounds = ChartAxisScaler.computeBounds(plotted, metric)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padY = 8.dp.toPx()
            val usableH = (h - padY * 2f).coerceAtLeast(4f)

            // Dashed midpoint baseline
            drawLine(
                color = Color(0x3894A3B8),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            )

            val linePath = Path()
            val fillPath = Path()
            var lastX = 0f
            var lastY = h / 2f

            val count = plotted.size
            for (i in plotted.indices) {
                val x = if (count == 1) w - 6.dp.toPx() else (i.toFloat() / (count - 1)) * w
                val rawNorm = (plotted[i] - bounds.min) / bounds.span
                val norm = if (rawNorm.isFinite()) rawNorm.coerceIn(0f, 1f) else 0.5f
                val y = (h - padY) - norm * usableH
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
                    colors = listOf(lineColor.copy(alpha = 0.30f), lineColor.copy(alpha = 0.02f)),
                    startY = 0f,
                    endY = h
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawCircle(
                color = lineColor,
                radius = 3.5.dp.toPx(),
                center = Offset(lastX, lastY)
            )
        }

        Text(
            text = ChartAxisScaler.formatBound(bounds.max, metric),
            style = monoStyle(
                size = 9.sp,
                weight = FontWeight.Medium,
                color = OledTokens.Muted.copy(alpha = 0.72f)
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 2.dp, top = 1.dp)
        )
        Text(
            text = ChartAxisScaler.formatBound(bounds.min, metric),
            style = monoStyle(
                size = 9.sp,
                weight = FontWeight.Medium,
                color = OledTokens.Muted.copy(alpha = 0.72f)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 2.dp, bottom = 1.dp)
        )
    }
}
