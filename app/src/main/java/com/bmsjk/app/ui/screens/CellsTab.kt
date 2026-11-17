package com.bmsjk.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.ui.components.AppIcons
import com.bmsjk.app.ui.components.CardLabelRow
import com.bmsjk.app.ui.components.EmptyTelemetryCard
import com.bmsjk.app.ui.components.OledCard
import com.bmsjk.app.ui.components.StatusPill
import com.bmsjk.app.ui.i18n.UiStrings
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.ui.theme.monoStyle
import com.bmsjk.app.viewmodel.BmsUiState
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
            .ifEmpty { telemetry.cells.take(24) }

        OledCard(modifier = Modifier.fillMaxWidth()) {
            CardLabelRow(
                icon = AppIcons.Delta,
                title = "${strings.cellBars} • ${activeCells.size}S (${telemetry.protocol.name})"
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
                    color = OledTokens.Accent
                )
                SummaryStat(
                    label = strings.delta,
                    value = String.format(Locale.US, "%.1fmV", telemetry.deltaCellVoltageMv),
                    color = OledTokens.Accent
                )
            }
        }

        CellsGridCard(telemetry = telemetry, strings = strings, columns = columns)

        // Wire resistance breakdown card (read-only diagnostic)
        if (activeCells.any { it.resistanceOhm > 0f || it.wireWarning }) {
            OledCard(modifier = Modifier.fillMaxWidth()) {
                CardLabelRow(icon = AppIcons.Grid, title = "Wire Resistance (Ω)")
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in activeCells.chunked(columns.coerceAtLeast(1))) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (cell in row) {
                                StatusPill(
                                    text = String.format(Locale.US, "#%d %.3fΩ", cell.index, cell.resistanceOhm),
                                    textColor = if (cell.wireWarning) OledTokens.DangerRed else OledTokens.Muted,
                                    borderColor = if (cell.wireWarning) OledTokens.DangerRed else OledTokens.CardBorder,
                                    fontSize = 10.sp,
                                    horizontalPadding = 6.dp,
                                    verticalPadding = 3.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(text = label, color = OledTokens.Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(text = value, style = monoStyle(size = 12.sp, weight = FontWeight.Bold, color = color))
    }
}
