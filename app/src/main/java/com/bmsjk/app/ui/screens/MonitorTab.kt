package com.bmsjk.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.data.AppLanguage
import com.bmsjk.app.ui.components.AppIcons
import com.bmsjk.app.ui.components.CardLabelRow
import com.bmsjk.app.ui.components.EmptyTelemetryCard
import com.bmsjk.app.ui.components.LiveDot
import com.bmsjk.app.ui.components.LiveTrendChartCanvas
import com.bmsjk.app.ui.components.OledCard
import com.bmsjk.app.ui.components.PowerArcCanvas
import com.bmsjk.app.ui.components.ReadOnlyPill
import com.bmsjk.app.ui.components.SocRingCanvas
import com.bmsjk.app.ui.components.StatusPill
import com.bmsjk.app.ui.i18n.AppStrings
import com.bmsjk.app.ui.i18n.UiStrings
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.ui.theme.monoStyle
import com.bmsjk.app.viewmodel.BmsUiState
import com.bmsjk.core.protocol.BalancingAction
import com.bmsjk.core.protocol.BmsTelemetry
import com.bmsjk.core.protocol.CellReading
import com.bmsjk.core.session.ChartMetric
import java.util.Locale

@Composable
fun MonitorTab(
    state: BmsUiState,
    strings: UiStrings,
    cellColumns: Int,
    compactSplit50: Boolean,
    twoColumnTopCards: Boolean,
    landscapeBento: Boolean,
    onGoToConnect: () -> Unit
) {
    val telemetry = state.session.telemetry
    if (telemetry == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EmptyTelemetryCard(
                title = strings.notConnectedTitle,
                description = strings.notConnectedDesc,
                actionLabel = strings.goToConnect,
                onAction = onGoToConnect
            )
        }
        return
    }

    val maxChargeRef = state.session.settings?.maxChargeCurrentA ?: 60f

    if (landscapeBento) {
        val leftScroll = rememberScrollState()
        val rightScroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(if (cellColumns >= 6) 0.40f else 0.48f)
                    .fillMaxHeight()
                    .verticalScroll(leftScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ErrorBannerIfAny(telemetry, strings)
                SocCard(telemetry, strings, compact = false, narrowGridCard = false)
                PowerCard(telemetry, maxChargeRef, strings, compact = false, narrowGridCard = false)
                TempsMosCard(telemetry, strings, compact = false)
            }
            Column(
                modifier = Modifier
                    .weight(if (cellColumns >= 6) 0.60f else 0.52f)
                    .fillMaxHeight()
                    .verticalScroll(rightScroll),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniChartCard(state, strings)
                CellsGridCard(telemetry, strings, columns = cellColumns)
            }
        }
        return
    }

    val scroll = rememberScrollState()
    val pad = if (compactSplit50) 6.dp else 8.dp
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(pad),
        verticalArrangement = Arrangement.spacedBy(pad)
    ) {
        ErrorBannerIfAny(telemetry, strings)

        if (twoColumnTopCards && !compactSplit50) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SocCard(
                    telemetry = telemetry,
                    strings = strings,
                    compact = false,
                    narrowGridCard = true,
                    modifier = Modifier.weight(1f)
                )
                PowerCard(
                    telemetry = telemetry,
                    maxCurrentRefA = maxChargeRef,
                    strings = strings,
                    compact = false,
                    narrowGridCard = true,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            SocCard(telemetry, strings, compact = compactSplit50, narrowGridCard = false)
            PowerCard(telemetry, maxChargeRef, strings, compact = compactSplit50, narrowGridCard = false)
        }

        TempsMosCard(telemetry, strings, compact = compactSplit50)

        if (!compactSplit50) {
            MiniChartCard(state, strings)
        }

        CellsGridCard(telemetry, strings, columns = cellColumns)
    }
}

@Composable
private fun ErrorBannerIfAny(telemetry: BmsTelemetry, strings: UiStrings) {
    if (telemetry.activeErrors.isEmpty()) return
    OledCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = OledTokens.DangerRed,
        containerColor = OledTokens.DangerSoft,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = AppIcons.Warning,
                contentDescription = null,
                tint = OledTokens.DangerRed,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "${strings.activeAlarms}: ${telemetry.activeErrors.joinToString(" • ") { AppStrings.errorLabel(it) }}",
                color = OledTokens.Text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocCard(
    telemetry: BmsTelemetry,
    strings: UiStrings,
    compact: Boolean,
    narrowGridCard: Boolean,
    modifier: Modifier = Modifier
) {
    val padding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 6.dp) else PaddingValues(horizontal = 11.dp, vertical = 10.dp)
    val ringSize = when {
        compact -> 54.dp
        narrowGridCard -> 64.dp
        else -> 76.dp
    }

    OledCard(modifier = modifier.fillMaxWidth(), contentPadding = padding) {
        CardLabelRow(icon = AppIcons.Battery, title = strings.socRing)
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (narrowGridCard) 8.dp else 11.dp)
            ) {
                SocRingCanvas(
                    socPercent = telemetry.socPercent,
                    totalVoltageV = telemetry.totalVoltageV,
                    size = ringSize
                )
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.3f", telemetry.totalVoltageV),
                            style = monoStyle(
                                size = when {
                                    narrowGridCard -> 17.sp
                                    compact -> 19.sp
                                    else -> 23.sp
                                },
                                weight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = " V",
                            color = OledTokens.Muted,
                            fontSize = if (narrowGridCard) 11.sp else 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    val rem = telemetry.remainingCapacityAh
                    val nom = telemetry.nominalCapacityAh
                    val capText = if (rem != null && nom != null) {
                        if (narrowGridCard) {
                            String.format(Locale.US, "%.1f / %.0f Ah", rem, nom)
                        } else {
                            String.format(Locale.US, "%s: %.2f / %.0f Ah", strings.remCap, rem, nom)
                        }
                    } else {
                        "${strings.remCap}: --"
                    }
                    Text(
                        text = capText,
                        style = monoStyle(
                            size = if (compact || narrowGridCard) 10.5.sp else 11.5.sp,
                            weight = FontWeight.Medium,
                            color = OledTokens.Muted
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!narrowGridCard) {
                Column(horizontalAlignment = Alignment.End) {
                    StatusPill(
                        text = "${telemetry.enabledCellCount}S",
                        fontSize = 10.sp,
                        horizontalPadding = if (compact) 6.dp else 8.dp,
                        verticalPadding = if (compact) 2.dp else 4.dp
                    )
                    Spacer(modifier = Modifier.height(if (compact) 2.dp else 3.dp))
                    Text(
                        text = String.format(Locale.US, "%s %.3fV", strings.avg, telemetry.avgCellVoltageV),
                        style = monoStyle(
                            size = if (compact) 10.5.sp else 11.5.sp,
                            weight = FontWeight.Medium,
                            color = OledTokens.Muted
                        )
                    )
                }
            }
        }

        if (narrowGridCard) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = "${telemetry.enabledCellCount}S",
                    fontSize = 10.sp,
                    horizontalPadding = 6.dp,
                    verticalPadding = 2.dp
                )
                Text(
                    text = String.format(Locale.US, "%s %.3fV", strings.avg, telemetry.avgCellVoltageV),
                    style = monoStyle(size = 10.5.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                )
            }
        }
    }
}

@Composable
fun PowerCard(
    telemetry: BmsTelemetry,
    maxCurrentRefA: Float,
    strings: UiStrings,
    compact: Boolean,
    narrowGridCard: Boolean,
    modifier: Modifier = Modifier
) {
    val cur = telemetry.currentA ?: 0f
    val modeLabel = when {
        cur > 0.2f -> strings.charging
        cur < -0.2f -> strings.discharging
        else -> strings.standby
    }
    val modeColor = when {
        cur > 0.2f -> OledTokens.ChargeGreen
        cur < -0.2f -> OledTokens.DischargeAmber
        else -> OledTokens.Accent
    }
    val padding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 6.dp) else PaddingValues(horizontal = 11.dp, vertical = 10.dp)

    OledCard(modifier = modifier.fillMaxWidth(), contentPadding = padding) {
        CardLabelRow(
            icon = AppIcons.Gauge,
            title = strings.powerGauge,
            trailing = {
                Text(
                    text = modeLabel,
                    style = monoStyle(size = 10.5.sp, weight = FontWeight.Bold, color = modeColor)
                )
            }
        )
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PowerArcCanvas(
                currentA = telemetry.currentA,
                powerW = telemetry.powerW,
                width = when {
                    compact -> 82.dp
                    narrowGridCard -> 80.dp
                    else -> 94.dp
                },
                height = if (compact || narrowGridCard) 44.dp else 54.dp,
                maxCurrentRefA = maxCurrentRefA
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.US, "Δ %.3f V", telemetry.deltaCellVoltageMv / 1000f),
                    style = monoStyle(
                        size = if (compact || narrowGridCard) 12.5.sp else 14.sp,
                        weight = FontWeight.Bold,
                        color = OledTokens.Accent
                    )
                )
                if (!narrowGridCard) {
                    Spacer(modifier = Modifier.height(if (compact) 1.dp else 3.dp))
                    Text(
                        text = String.format(Locale.US, "Min #%d: %.3fV", telemetry.minVoltageCellIndex, telemetry.minCellVoltageV),
                        style = monoStyle(size = if (compact) 10.5.sp else 11.5.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                    )
                    Text(
                        text = String.format(Locale.US, "Max #%d: %.3fV", telemetry.maxVoltageCellIndex, telemetry.maxCellVoltageV),
                        style = monoStyle(size = if (compact) 10.5.sp else 11.5.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                    )
                }
            }
        }

        if (narrowGridCard) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "Min #%d: %.3fV", telemetry.minVoltageCellIndex, telemetry.minCellVoltageV),
                    style = monoStyle(size = 10.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                )
                Text(
                    text = String.format(Locale.US, "Max #%d: %.3fV", telemetry.maxVoltageCellIndex, telemetry.maxCellVoltageV),
                    style = monoStyle(size = 10.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TempsMosCard(
    telemetry: BmsTelemetry,
    strings: UiStrings,
    compact: Boolean
) {
    val padding = if (compact) PaddingValues(horizontal = 10.dp, vertical = 6.dp) else PaddingValues(horizontal = 11.dp, vertical = 10.dp)
    val pillFont = if (compact) 10.sp else 10.5.sp
    val hPad = if (compact) 6.dp else 8.dp
    val vPad = if (compact) 2.dp else 4.dp

    val chgOn = telemetry.chargingMosEnabled == true
    val dsgOn = telemetry.dischargingMosEnabled == true
    val balActive = telemetry.balancingAction != BalancingAction.OFF

    OledCard(modifier = Modifier.fillMaxWidth(), contentPadding = padding) {
        CardLabelRow(icon = AppIcons.Thermometer, title = strings.tempsMos)
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 7.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            StatusPill(
                text = "${strings.chgMos}: ${if (chgOn) "ON" else "OFF"}",
                textColor = if (chgOn) OledTokens.ChargeGreenText else OledTokens.Text,
                containerColor = if (chgOn) OledTokens.ChargeGreenSoft else OledTokens.CardElevated,
                borderColor = if (chgOn) OledTokens.ChargeGreenBorder else OledTokens.CardBorder,
                fontSize = pillFont,
                horizontalPadding = hPad,
                verticalPadding = vPad
            )
            StatusPill(
                text = "${strings.dsgMos}: ${if (dsgOn) "ON" else "OFF"}",
                textColor = if (dsgOn) OledTokens.ChargeGreenText else OledTokens.Text,
                containerColor = if (dsgOn) OledTokens.ChargeGreenSoft else OledTokens.CardElevated,
                borderColor = if (dsgOn) OledTokens.ChargeGreenBorder else OledTokens.CardBorder,
                fontSize = pillFont,
                horizontalPadding = hPad,
                verticalPadding = vPad
            )
            StatusPill(
                text = "${strings.balMos}: ${if (balActive) String.format(Locale.US, "%.2fA", telemetry.balanceCurrentA) else "OFF"}",
                textColor = if (balActive) OledTokens.AmberText else OledTokens.Text,
                containerColor = if (balActive) OledTokens.AmberSoft else OledTokens.CardElevated,
                borderColor = if (balActive) OledTokens.AmberBorder else OledTokens.CardBorder,
                fontSize = pillFont,
                horizontalPadding = hPad,
                verticalPadding = vPad
            )
            StatusPill(
                text = "MOS ${formatTempOrDash(telemetry.mosfetTempC)}",
                fontSize = pillFont,
                horizontalPadding = hPad,
                verticalPadding = vPad
            )

            // Mask disconnected probes (null / <= -100C) cleanly as "--"
            val probes = listOf(
                "T1" to telemetry.temp1C,
                "T2" to telemetry.temp2C,
                "T3" to telemetry.temp3C,
                "T4" to telemetry.temp4C,
                "T5" to telemetry.temp5C
            )
            val connectedProbes = probes.filter { it.second != null }
            val disconnectedCount = probes.size - connectedProbes.size
            for ((label, temp) in connectedProbes) {
                StatusPill(
                    text = "$label ${formatTempOrDash(temp)}",
                    fontSize = pillFont,
                    horizontalPadding = hPad,
                    verticalPadding = vPad
                )
            }
            if (disconnectedCount > 0) {
                val disconnectedLabels = probes.filter { it.second == null }.map { it.first }
                val summary = if (disconnectedLabels == listOf("T1", "T2", "T3", "T4")) {
                    "T1..4: --"
                } else {
                    "${disconnectedLabels.joinToString(",")}: --"
                }
                StatusPill(
                    text = summary,
                    textColor = OledTokens.Muted,
                    fontSize = pillFont,
                    horizontalPadding = hPad,
                    verticalPadding = vPad
                )
            }
        }
    }
}

@Composable
fun MiniChartCard(
    state: BmsUiState,
    strings: UiStrings
) {
    val metricColor = when (state.chartMetric) {
        ChartMetric.CURRENT -> OledTokens.DischargeAmber
        ChartMetric.POWER -> OledTokens.ChargeGreen
        else -> OledTokens.Accent
    }
    OledCard(modifier = Modifier.fillMaxWidth()) {
        CardLabelRow(
            icon = AppIcons.Chart,
            title = "${strings.miniChart} (${AppStrings.metricLabel(state.chartMetric, state.language)})",
            trailing = {
                Text(
                    text = "1m • 5m • 15m",
                    style = monoStyle(size = 10.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                )
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        LiveTrendChartCanvas(
            samples = state.chartSamples,
            metric = state.chartMetric,
            lineColor = metricColor,
            height = 72.dp,
            emptyText = strings.noChartData
        )
    }
}

@Composable
fun CellsGridCard(
    telemetry: BmsTelemetry,
    strings: UiStrings,
    columns: Int
) {
    val activeCells = telemetry.cells.filter { it.enabled && it.voltageV > 0f }
        .ifEmpty { telemetry.cells }
    val rows = activeCells.chunked(columns.coerceAtLeast(1))

    OledCard(modifier = Modifier.fillMaxWidth()) {
        CardLabelRow(
            icon = AppIcons.Grid,
            title = strings.cellVoltagesTitle(activeCells.size.takeIf { it > 0 }),
            trailing = {
                Text(
                    text = String.format(Locale.US, "Δ %.3f V", telemetry.deltaCellVoltageMv / 1000f),
                    style = monoStyle(size = 10.5.sp, weight = FontWeight.Bold, color = OledTokens.Text)
                )
            }
        )
        Spacer(modifier = Modifier.height(7.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (rowCells in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (cell in rowCells) {
                        val isMax = cell.index == telemetry.maxVoltageCellIndex
                        val isMin = cell.index == telemetry.minVoltageCellIndex
                        CellGridTile(
                            cell = cell,
                            isMax = isMax,
                            isMin = isMin,
                            showBar = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat((columns - rowCells.size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun CellGridTile(
    cell: CellReading,
    isMax: Boolean,
    isMin: Boolean,
    showBar: Boolean,
    modifier: Modifier = Modifier
) {
    val border = when {
        cell.wireWarning || isMax -> OledTokens.DangerRed
        isMin -> OledTokens.AmberStrong
        else -> OledTokens.CardBorder
    }
    val bg = when {
        cell.wireWarning || isMax -> OledTokens.DangerSoft
        isMin -> OledTokens.AmberSoft
        else -> OledTokens.CardElevated
    }
    val barColor = when {
        cell.wireWarning || isMax -> OledTokens.DangerRed
        isMin -> OledTokens.AmberStrong
        else -> OledTokens.Accent
    }
    val pct = ((cell.voltageV - 2.90f) / (3.55f - 2.90f)).coerceIn(0.08f, 1.0f)

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, border)
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
                    text = when {
                        isMax -> "MAX"
                        isMin -> "MIN"
                        else -> ""
                    },
                    style = monoStyle(
                        size = 10.sp,
                        weight = FontWeight.Bold,
                        color = if (isMax) OledTokens.DangerRed else OledTokens.AmberText
                    ),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = String.format(Locale.US, "%.3f", cell.voltageV),
                style = monoStyle(size = 11.5.sp, weight = FontWeight.Bold, color = OledTokens.Text)
            )
            if (showBar) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(OledTokens.TrackMuted)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct)
                            .fillMaxHeight()
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
fun Hud30View(
    state: BmsUiState,
    strings: UiStrings,
    isNarrow: Boolean,
    onToggleCells: () -> Unit,
    onGoToConnect: () -> Unit
) {
    val telemetry = state.session.telemetry
    val activeName = state.session.deviceInfo?.deviceName?.takeIf { it.isNotBlank() }
        ?: state.session.deviceAddress?.takeIf { it.isNotBlank() }
        ?: "JK BMS"
    val isLive = state.session.isUnlocked && telemetry != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OledTokens.Bg)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // HUD Top Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LiveDot(active = isLive)
                Text(
                    text = activeName,
                    color = OledTokens.Text,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ReadOnlyPill(
                    label = if (isNarrow) "RO" else strings.readOnly,
                    compact = isNarrow
                )
                Surface(
                    color = if (state.hudShowCells) OledTokens.AccentSoft else OledTokens.CardElevated,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        1.dp,
                        if (state.hudShowCells) OledTokens.Accent else OledTokens.CardBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onToggleCells() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!state.hudShowCells) {
                            Icon(
                                imageVector = AppIcons.Grid,
                                contentDescription = null,
                                tint = OledTokens.Muted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = when {
                                state.hudShowCells -> "HUD"
                                isNarrow -> strings.cellWord
                                else -> strings.cellsTabLabel(state.activeCellCount)
                            },
                            color = if (state.hudShowCells) OledTokens.Accent else OledTokens.Muted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        if (telemetry == null) {
            EmptyTelemetryCard(
                title = strings.notConnectedTitle,
                description = strings.notConnectedDesc,
                actionLabel = strings.goToConnect,
                onAction = onGoToConnect
            )
            return
        }

        if (state.hudShowCells) {
            val cols = if (isNarrow) 3 else 6
            val activeCells = telemetry.cells.filter { it.enabled && it.voltageV > 0f }
                .ifEmpty { telemetry.cells }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (rowCells in activeCells.chunked(cols)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (cell in rowCells) {
                            CellGridTile(
                                cell = cell,
                                isMax = cell.index == telemetry.maxVoltageCellIndex,
                                isMin = cell.index == telemetry.minVoltageCellIndex,
                                showBar = false,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat((cols - rowCells.size).coerceAtLeast(0)) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            return
        }

        val cur = telemetry.currentA ?: 0f
        val curColor = when {
            cur < -0.2f -> OledTokens.DischargeAmber
            cur > 0.2f -> OledTokens.ChargeGreen
            else -> OledTokens.Text
        }
        val remStr = telemetry.remainingCapacityAh?.let {
            String.format(Locale.US, "%.1f", it)
        } ?: "--"
        val nomStr = telemetry.nominalCapacityAh?.let {
            String.format(Locale.US, "%.0fAh", it)
        } ?: "--Ah"
        val curText = telemetry.currentA?.let {
            String.format(Locale.US, "%s%.1f A", if (it >= 0f) "+" else "", it)
        } ?: "-- A"
        val pwrText = telemetry.powerW?.let {
            val w = it.toInt()
            "${if (w >= 0) "+" else ""}$w W"
        } ?: "-- W"
        val deltaText = String.format(Locale.US, "%.3f V", telemetry.deltaCellVoltageMv / 1000f)
        val maxTempText = telemetry.maxTemperatureC?.let {
            String.format(Locale.US, "%.1f °C", it)
        } ?: "-- °C"

        if (isNarrow) {
            // Preserved 1-column vertical HUD layout for 220x360 narrow split-screen
            OledCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    SocRingCanvas(
                        socPercent = telemetry.socPercent,
                        totalVoltageV = telemetry.totalVoltageV,
                        size = 70.dp
                    )
                    Text(
                        text = "$remStr / $nomStr",
                        style = monoStyle(size = 11.sp, weight = FontWeight.Bold, color = OledTokens.Text)
                    )
                }
            }
            HudMiniBox(AppIcons.Bolt, strings.current, curText, curColor)
            HudMiniBox(AppIcons.Gauge, strings.power, pwrText, OledTokens.Text)
            HudMiniBox(AppIcons.Delta, strings.delta, deltaText, OledTokens.Accent)
            HudMiniBox(AppIcons.Thermometer, "MOS / T5", maxTempText, OledTokens.Text)
        } else {
            // Portrait 70/30 HUD: SOC card left (108dp) + 2x2 KPIs right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OledCard(
                    modifier = Modifier.width(108.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        SocRingCanvas(
                            socPercent = telemetry.socPercent,
                            totalVoltageV = telemetry.totalVoltageV,
                            size = 80.dp
                        )
                        Text(
                            text = "$remStr / $nomStr",
                            style = monoStyle(size = 10.5.sp, weight = FontWeight.Bold, color = OledTokens.Text),
                            maxLines = 1
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudMiniBox(AppIcons.Bolt, strings.current, curText, curColor, Modifier.weight(1f))
                        HudMiniBox(AppIcons.Gauge, strings.power, pwrText, OledTokens.Text, Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudMiniBox(AppIcons.Delta, strings.delta, deltaText, OledTokens.Accent, Modifier.weight(1f))
                        HudMiniBox(AppIcons.Thermometer, "MOS / T5", maxTempText, OledTokens.Text, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HudMiniBox(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    OledCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OledTokens.Muted,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                color = OledTokens.Muted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = monoStyle(size = 14.5.sp, weight = FontWeight.Bold, color = valueColor),
            maxLines = 1
        )
    }
}

private fun formatTempOrDash(tempC: Float?): String =
    if (tempC == null || tempC <= -100f || tempC >= 200f) {
        "--"
    } else {
        String.format(Locale.US, "%.1f°C", tempC)
    }
