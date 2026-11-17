package com.bmsjk.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.ui.components.AppIcons
import com.bmsjk.app.ui.components.CardLabelRow
import com.bmsjk.app.ui.components.EmptyTelemetryCard
import com.bmsjk.app.ui.components.LiveTrendChartCanvas
import com.bmsjk.app.ui.components.OledCard
import com.bmsjk.app.ui.i18n.AppStrings
import com.bmsjk.app.ui.i18n.UiStrings
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.ui.theme.monoStyle
import com.bmsjk.app.viewmodel.BmsUiState
import com.bmsjk.core.session.ChartMetric
import com.bmsjk.core.session.ChartWindow
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartsTab(
    state: BmsUiState,
    strings: UiStrings,
    onSelectMetric: (ChartMetric) -> Unit,
    onSelectWindow: (ChartWindow) -> Unit,
    onTogglePaused: () -> Unit,
    onClearHistory: () -> Unit,
    onGoToConnect: () -> Unit
) {
    val scroll = rememberScrollState()
    val metricColor = when (state.chartMetric) {
        ChartMetric.CURRENT -> OledTokens.DischargeAmber
        ChartMetric.POWER -> OledTokens.ChargeGreen
        else -> OledTokens.Accent
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.session.telemetry == null && state.chartSamples.isEmpty()) {
            EmptyTelemetryCard(
                title = strings.notConnectedTitle,
                description = strings.notConnectedDesc,
                actionLabel = strings.goToConnect,
                onAction = onGoToConnect
            )
            return
        }

        // Metric & Window Controls
        OledCard(modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (metric in ChartMetric.entries) {
                    ChartChip(
                        label = AppStrings.metricLabel(metric, state.language),
                        selected = state.chartMetric == metric,
                        onClick = { onSelectMetric(metric) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (window in ChartWindow.entries) {
                        ChartChip(
                            label = AppStrings.windowLabel(window, state.language),
                            selected = state.chartWindow == window,
                            onClick = { onSelectWindow(window) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChartChip(
                        label = if (state.session.chartRecordingPaused) strings.resume else strings.pause,
                        selected = state.session.chartRecordingPaused,
                        onClick = onTogglePaused
                    )
                    ChartChip(
                        label = strings.clear,
                        selected = false,
                        onClick = onClearHistory
                    )
                }
            }
        }

        // Primary Chart Card
        OledCard(modifier = Modifier.fillMaxWidth()) {
            CardLabelRow(
                icon = AppIcons.Chart,
                title = AppStrings.metricLabel(state.chartMetric, state.language),
                trailing = {
                    Text(
                        text = AppStrings.windowLabel(state.chartWindow, state.language),
                        style = monoStyle(size = 10.5.sp, weight = FontWeight.Bold, color = OledTokens.Accent)
                    )
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LiveTrendChartCanvas(
                samples = state.chartSamples,
                metric = state.chartMetric,
                lineColor = metricColor,
                height = 140.dp,
                emptyText = strings.noChartData
            )
            Spacer(modifier = Modifier.height(8.dp))
            val stats = state.chartStats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Min: ${formatMetricValue(stats.min, state.chartMetric)}",
                    style = monoStyle(size = 11.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                )
                Text(
                    text = "${strings.avg}: ${formatMetricValue(stats.average, state.chartMetric)}",
                    style = monoStyle(size = 11.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                )
                Text(
                    text = "Max: ${formatMetricValue(stats.max, state.chartMetric)}",
                    style = monoStyle(size = 11.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                )
            }
        }

        // Secondary Delta mV Chart Card
        if (state.chartMetric != ChartMetric.DELTA_MV) {
            OledCard(modifier = Modifier.fillMaxWidth()) {
                CardLabelRow(
                    icon = AppIcons.Delta,
                    title = AppStrings.metricLabel(ChartMetric.DELTA_MV, state.language)
                )
                Spacer(modifier = Modifier.height(6.dp))
                LiveTrendChartCanvas(
                    samples = state.chartSamples,
                    metric = ChartMetric.DELTA_MV,
                    lineColor = OledTokens.DischargeAmber,
                    height = 95.dp,
                    emptyText = strings.noChartData
                )
            }
        }
    }
}

@Composable
private fun ChartChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) OledTokens.AccentSoft else OledTokens.CardElevated,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) OledTokens.Accent else OledTokens.CardBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (selected) OledTokens.Accent else OledTokens.Muted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

private fun formatMetricValue(value: Float?, metric: ChartMetric): String {
    if (value == null) return "--"
    return when (metric) {
        ChartMetric.PACK_VOLTAGE -> String.format(Locale.US, "%.2fV", value)
        ChartMetric.CURRENT -> String.format(Locale.US, "%.1fA", value)
        ChartMetric.POWER -> String.format(Locale.US, "%.0fW", value)
        ChartMetric.DELTA_MV -> String.format(Locale.US, "%.1fmV", value)
        ChartMetric.MAX_TEMPERATURE -> String.format(Locale.US, "%.1f°C", value)
    }
}
