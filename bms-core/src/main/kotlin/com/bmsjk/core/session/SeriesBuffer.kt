package com.bmsjk.core.session

import com.bmsjk.core.protocol.BmsTelemetry

enum class ChartWindow(val durationMs: Long?) {
    ONE_MINUTE(60_000L),
    FIVE_MINUTES(300_000L),
    FIFTEEN_MINUTES(900_000L),
    FULL_SESSION(null)
}

enum class ChartMetric {
    PACK_VOLTAGE,
    CURRENT,
    POWER,
    DELTA_MV,
    MAX_TEMPERATURE
}

data class TrendSample(
    val timestampMs: Long,
    val totalVoltageV: Float,
    val currentA: Float?,
    val powerW: Float?,
    val deltaCellMv: Float,
    val minCellV: Float,
    val maxCellV: Float,
    val mosfetTempC: Float?,
    val maxTempC: Float?
)

data class MetricStats(
    val current: Float?,
    val min: Float?,
    val max: Float?,
    val average: Float?
)

/**
 * Bounded in-memory time-series buffer for lightweight Canvas charts.
 */
class SeriesBuffer(
    private val maxSamples: Int = 7_200
) {
    private val samples = ArrayDeque<TrendSample>(minOf(maxSamples, 512))

    fun append(telemetry: BmsTelemetry) {
        val sample = TrendSample(
            timestampMs = telemetry.timestampMs,
            totalVoltageV = telemetry.totalVoltageV,
            currentA = telemetry.currentA,
            powerW = telemetry.powerW,
            deltaCellMv = telemetry.deltaCellVoltageMv,
            minCellV = telemetry.minCellVoltageV,
            maxCellV = telemetry.maxCellVoltageV,
            mosfetTempC = telemetry.mosfetTempC,
            maxTempC = telemetry.maxTemperatureC
        )
        if (samples.size >= maxSamples) {
            samples.removeFirst()
        }
        samples.addLast(sample)
    }

    fun clear() {
        samples.clear()
    }

    fun size(): Int = samples.size

    fun query(window: ChartWindow, nowMs: Long = System.currentTimeMillis()): List<TrendSample> {
        val duration = window.durationMs ?: return samples.toList()
        val cutoff = nowMs - duration
        return samples.filter { it.timestampMs >= cutoff }
    }

    fun statsFor(
        metric: ChartMetric,
        window: ChartWindow,
        nowMs: Long = System.currentTimeMillis()
    ): MetricStats {
        val values = query(window, nowMs).mapNotNull { sample ->
            when (metric) {
                ChartMetric.PACK_VOLTAGE -> sample.totalVoltageV
                ChartMetric.CURRENT -> sample.currentA
                ChartMetric.POWER -> sample.powerW
                ChartMetric.DELTA_MV -> sample.deltaCellMv
                ChartMetric.MAX_TEMPERATURE -> sample.maxTempC
            }
        }
        if (values.isEmpty()) return MetricStats(null, null, null, null)

        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        var sum = 0.0
        for (v in values) {
            if (v < min) min = v
            if (v > max) max = v
            sum += v
        }
        return MetricStats(
            current = values.last(),
            min = min,
            max = max,
            average = (sum / values.size).toFloat()
        )
    }
}
