package com.bmsjk.core.protocol

import kotlin.math.abs

internal data class CellSummary(
    val cells: List<CellReading>,
    val enabledCount: Int,
    val minV: Float,
    val maxV: Float,
    val minCellIndex: Int,
    val maxCellIndex: Int,
    val avgV: Float,
    val deltaMv: Float,
    val sumV: Float
)

internal object CellSummaryCalculator {
    fun compute(cells: List<CellReading>): CellSummary {
        var enabledCount = 0
        var sumV = 0f
        var minV = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var minIdx = 0
        var maxIdx = 0

        for (cell in cells) {
            if (cell.enabled) {
                enabledCount++
                sumV += cell.voltageV
                if (cell.voltageV < minV) {
                    minV = cell.voltageV
                    minIdx = cell.index
                }
                if (cell.voltageV > maxV) {
                    maxV = cell.voltageV
                    maxIdx = cell.index
                }
            }
        }

        if (enabledCount == 0) {
            return CellSummary(
                cells = cells,
                enabledCount = 0,
                minV = 0f,
                maxV = 0f,
                minCellIndex = 0,
                maxCellIndex = 0,
                avgV = 0f,
                deltaMv = 0f,
                sumV = 0f
            )
        }

        val avgV = sumV / enabledCount
        val deltaMv = abs(maxV - minV) * 1000f
        return CellSummary(
            cells = cells,
            enabledCount = enabledCount,
            minV = minV,
            maxV = maxV,
            minCellIndex = minIdx,
            maxCellIndex = maxIdx,
            avgV = avgV,
            deltaMv = deltaMv,
            sumV = sumV
        )
    }
}
