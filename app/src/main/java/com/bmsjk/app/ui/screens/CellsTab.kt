package com.bmsjk.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.ui.components.AppIcons
import com.bmsjk.app.ui.components.CardLabelRow
import com.bmsjk.app.ui.components.EmptyTelemetryCard
import com.bmsjk.app.ui.components.OledCard
import com.bmsjk.app.ui.i18n.UiStrings
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.ui.theme.monoStyle
import com.bmsjk.app.viewmodel.BmsUiState
import com.bmsjk.core.protocol.CellReading
import java.util.Locale

@Composable
fun CellsTab(
    state: BmsUiState,
    strings: UiStrings,
    columns: Int,
    onGoToConnect: () -> Unit
) {
    val telemetry = state.session.telemetry
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (telemetry == null) {
            EmptyTelemetryCard(
                title = strings.notConnectedTitle,
                description = strings.notConnectedDesc,
                actionLabel = strings.goToConnect,
                onAction = onGoToConnect
            )
            return
        }

        val activeCells = telemetry.cells.filter { it.enabled && it.voltageV > 0f }
            .ifEmpty { telemetry.cells }

        OledCard(modifier = Modifier.fillMaxWidth()) {
            CardLabelRow(
                icon = AppIcons.Delta,
                title = "${strings.cellVoltagesTitle(activeCells.size)} (${telemetry.protocol.name})"
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStat(
                    label = "Min #${telemetry.minVoltageCellIndex}",
                    value = String.format(Locale.US, "%.3fV", telemetry.minCellVoltageV),
                    color = OledTokens.AmberText
                )
                SummaryStat(
                    label = strings.avg,
                    value = String.format(Locale.US, "%.3fV", telemetry.avgCellVoltageV),
                    color = OledTokens.Text
                )
                SummaryStat(
                    label = "Max #${telemetry.maxVoltageCellIndex}",
                    value = String.format(Locale.US, "%.3fV", telemetry.maxCellVoltageV),
                    color = OledTokens.DangerRed
                )
                SummaryStat(
                    label = strings.delta,
                    value = String.format(Locale.US, "%.3fV", telemetry.deltaCellVoltageMv / 1000f),
                    color = OledTokens.Accent
                )
            }
        }

        CellsGridCard(telemetry = telemetry, strings = strings, columns = columns)

        // Wire resistance breakdown card (matching exact column grid & tile sizing of cell voltages)
        if (activeCells.any { it.resistanceOhm > 0f || it.wireWarning }) {
            val cols = columns.coerceAtLeast(1)
            OledCard(modifier = Modifier.fillMaxWidth()) {
                CardLabelRow(icon = AppIcons.Grid, title = "Wire Resistance (Ω)")
                Spacer(modifier = Modifier.height(7.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in activeCells.chunked(cols)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (cell in row) {
                                WireResistanceTile(
                                    cell = cell,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat((cols - row.size).coerceAtLeast(0)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WireResistanceTile(
    cell: CellReading,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (cell.wireWarning) OledTokens.DangerSoft else OledTokens.CardElevated,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (cell.wireWarning) OledTokens.DangerRed else OledTokens.CardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${cell.index}",
                    style = monoStyle(size = 10.sp, weight = FontWeight.SemiBold, color = OledTokens.Muted),
                    maxLines = 1
                )
                Text(
                    text = if (cell.wireWarning) "WARN" else "",
                    style = monoStyle(size = 10.sp, weight = FontWeight.Bold, color = OledTokens.DangerRed),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = String.format(Locale.US, "%.3fΩ", cell.resistanceOhm),
                style = monoStyle(
                    size = 11.5.sp,
                    weight = FontWeight.Bold,
                    color = if (cell.wireWarning) OledTokens.DangerRed else OledTokens.Text
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    color: Color
) {
    Column {
        Text(text = label, color = OledTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, style = monoStyle(size = 12.sp, weight = FontWeight.Bold, color = color))
    }
}
