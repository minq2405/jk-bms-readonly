package com.bmsjk.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import com.bmsjk.app.ble.BleConnectionState
import com.bmsjk.app.ble.BleDeviceItem
import com.bmsjk.app.ui.components.AppIcons
import com.bmsjk.app.ui.components.LiveDot
import com.bmsjk.app.ui.components.ReadOnlyPill
import com.bmsjk.app.ui.components.StatusPill
import com.bmsjk.app.ui.i18n.AppStrings
import com.bmsjk.app.ui.i18n.UiStrings
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.viewmodel.AppTab
import com.bmsjk.app.viewmodel.BmsUiState
import com.bmsjk.core.protocol.ProtocolMode
import com.bmsjk.core.session.ChartMetric
import com.bmsjk.core.session.ChartWindow

@Composable
fun DashboardShell(
    state: BmsUiState,
    hasBlePermissions: Boolean,
    onRequestBlePermissions: () -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onToggleHudCells: () -> Unit,
    onToggleLanguage: () -> Unit,
    onSelectProtocolMode: (ProtocolMode) -> Unit,
    onSelectChartMetric: (ChartMetric) -> Unit,
    onSelectChartWindow: (ChartWindow) -> Unit,
    onToggleChartPaused: () -> Unit,
    onClearChartHistory: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onConnectDevice: (BleDeviceItem) -> Unit,
    onConnectQrPayload: (String) -> Boolean,
    onDisconnect: () -> Unit,
    onSubmitPasscode: (String) -> Unit,
    onForgetPasscode: () -> Unit,
    onKeepLastValues: () -> Unit,
    onDisconnectAndGoToConnect: () -> Unit
) {
    val strings = AppStrings.forLang(state.language)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OledTokens.Bg)
            .safeDrawingPadding()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isHud30 = maxHeight < 270.dp || (maxWidth < 270.dp && maxHeight < 420.dp)
            val isHudNarrow = maxWidth < 270.dp
            val isCompactSplit50 = !isHud30 && maxHeight in 270.dp..440.dp && maxWidth < 560.dp
            val useNavRail = !isHud30 && maxWidth >= 560.dp
            val landscapeBento = maxWidth >= 560.dp
            val twoColumnTopCards = !isCompactSplit50 && maxWidth in 405.dp..559.dp
            val compact320 = maxWidth < 345.dp
            val cellColumns = when {
                maxWidth >= 780.dp -> 6
                compact320 -> 3
                else -> 4
            }

            if (isHud30) {
                Hud30View(
                    state = state,
                    strings = strings,
                    isNarrow = isHudNarrow,
                    onToggleCells = onToggleHudCells,
                    onGoToConnect = { onSelectTab(AppTab.CONNECT) }
                )
                return@BoxWithConstraints
            }

            Column(modifier = Modifier.fillMaxSize()) {
                AppHeaderBar(
                    state = state,
                    strings = strings,
                    compact = isCompactSplit50
                )

                if (useNavRail) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AppNavigationRail(
                            selectedTab = state.selectedTab,
                            activeCellCount = state.activeCellCount,
                            strings = strings,
                            onSelectTab = onSelectTab
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            ActiveTabContent(
                                state = state,
                                strings = strings,
                                cellColumns = cellColumns,
                                compactSplit50 = false,
                                twoColumnTopCards = false,
                                landscapeBento = landscapeBento,
                                hasBlePermissions = hasBlePermissions,
                                onRequestBlePermissions = onRequestBlePermissions,
                                onSelectTab = onSelectTab,
                                onToggleLanguage = onToggleLanguage,
                                onSelectProtocolMode = onSelectProtocolMode,
                                onSelectChartMetric = onSelectChartMetric,
                                onSelectChartWindow = onSelectChartWindow,
                                onToggleChartPaused = onToggleChartPaused,
                                onClearChartHistory = onClearChartHistory,
                                onSearchQueryChange = onSearchQueryChange,
                                onScanClick = onScanClick,
                                onConnectDevice = onConnectDevice,
                                onConnectQrPayload = onConnectQrPayload,
                                onDisconnect = onDisconnect,
                                onSubmitPasscode = onSubmitPasscode,
                                onForgetPasscode = onForgetPasscode
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ActiveTabContent(
                            state = state,
                            strings = strings,
                            cellColumns = cellColumns,
                            compactSplit50 = isCompactSplit50,
                            twoColumnTopCards = twoColumnTopCards,
                            landscapeBento = false,
                            hasBlePermissions = hasBlePermissions,
                            onRequestBlePermissions = onRequestBlePermissions,
                            onSelectTab = onSelectTab,
                            onToggleLanguage = onToggleLanguage,
                            onSelectProtocolMode = onSelectProtocolMode,
                            onSelectChartMetric = onSelectChartMetric,
                            onSelectChartWindow = onSelectChartWindow,
                            onToggleChartPaused = onToggleChartPaused,
                            onClearChartHistory = onClearChartHistory,
                            onSearchQueryChange = onSearchQueryChange,
                            onScanClick = onScanClick,
                            onConnectDevice = onConnectDevice,
                            onConnectQrPayload = onConnectQrPayload,
                            onDisconnect = onDisconnect,
                            onSubmitPasscode = onSubmitPasscode,
                            onForgetPasscode = onForgetPasscode
                        )
                    }

                    AppBottomNavBar(
                        selectedTab = state.selectedTab,
                        activeCellCount = state.activeCellCount,
                        strings = strings,
                        compactSplit50 = isCompactSplit50,
                        compact320 = compact320,
                        onSelectTab = onSelectTab
                    )
                }
            }
        }

        if (state.showDisconnectPrompt) {
            val lostName = when (val c = state.connectionState) {
                is BleConnectionState.ConnectionLost -> c.name
                else -> state.session.deviceInfo?.deviceName ?: "JK BMS"
            }
            ConnectionLostDialog(
                deviceName = lostName,
                strings = strings,
                onKeepLastValues = onKeepLastValues,
                onDisconnectAndGoToConnect = onDisconnectAndGoToConnect
            )
        }
    }
}

@Composable
private fun ConnectionLostDialog(
    deviceName: String,
    strings: UiStrings,
    onKeepLastValues: () -> Unit,
    onDisconnectAndGoToConnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepLastValues,
        containerColor = OledTokens.Card,
        titleContentColor = OledTokens.Text,
        textContentColor = OledTokens.Muted,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Warning,
                    contentDescription = null,
                    tint = OledTokens.DischargeAmber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = strings.connectionLostTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text(
                text = strings.connectionLostDesc(deviceName),
                fontSize = 12.5.sp,
                color = OledTokens.Muted
            )
        },
        dismissButton = {
            OutlinedButton(
                onClick = onKeepLastValues,
                border = BorderStroke(1.dp, OledTokens.CardBorder),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text(
                    text = strings.keepLastValuesBtn,
                    color = OledTokens.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDisconnectAndGoToConnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OledTokens.Accent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(42.dp)
            ) {
                Text(
                    text = strings.disconnectAndGoConnectBtn,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }
        }
    )
}

@Composable
private fun ActiveTabContent(
    state: BmsUiState,
    strings: UiStrings,
    cellColumns: Int,
    compactSplit50: Boolean,
    twoColumnTopCards: Boolean,
    landscapeBento: Boolean,
    hasBlePermissions: Boolean,
    onRequestBlePermissions: () -> Unit,
    onSelectTab: (AppTab) -> Unit,
    onToggleLanguage: () -> Unit,
    onSelectProtocolMode: (ProtocolMode) -> Unit,
    onSelectChartMetric: (ChartMetric) -> Unit,
    onSelectChartWindow: (ChartWindow) -> Unit,
    onToggleChartPaused: () -> Unit,
    onClearChartHistory: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onConnectDevice: (BleDeviceItem) -> Unit,
    onConnectQrPayload: (String) -> Boolean,
    onDisconnect: () -> Unit,
    onSubmitPasscode: (String) -> Unit,
    onForgetPasscode: () -> Unit
) {
    when (state.selectedTab) {
        AppTab.MONITOR -> MonitorTab(
            state = state,
            strings = strings,
            cellColumns = cellColumns,
            compactSplit50 = compactSplit50,
            twoColumnTopCards = twoColumnTopCards,
            landscapeBento = landscapeBento,
            onGoToConnect = { onSelectTab(AppTab.CONNECT) }
        )
        AppTab.CELLS -> CellsTab(
            state = state,
            strings = strings,
            columns = cellColumns,
            onGoToConnect = { onSelectTab(AppTab.CONNECT) }
        )
        AppTab.CHARTS -> ChartsTab(
            state = state,
            strings = strings,
            onSelectMetric = onSelectChartMetric,
            onSelectWindow = onSelectChartWindow,
            onTogglePaused = onToggleChartPaused,
            onClearHistory = onClearChartHistory,
            onGoToConnect = { onSelectTab(AppTab.CONNECT) }
        )
        AppTab.SETTINGS -> SettingsTab(
            state = state,
            strings = strings,
            onToggleLanguage = onToggleLanguage,
            onSelectProtocolMode = onSelectProtocolMode
        )
        AppTab.CONNECT -> ConnectTab(
            state = state,
            strings = strings,
            hasBlePermissions = hasBlePermissions,
            onRequestBlePermissions = onRequestBlePermissions,
            onSearchQueryChange = onSearchQueryChange,
            onScanClick = onScanClick,
            onConnectDevice = onConnectDevice,
            onConnectQrPayload = onConnectQrPayload,
            onDisconnect = onDisconnect,
            onSubmitPasscode = onSubmitPasscode,
            onForgetPasscode = onForgetPasscode
        )
    }
}

@Composable
private fun AppHeaderBar(
    state: BmsUiState,
    strings: UiStrings,
    compact: Boolean
) {
    val activeTitle = state.session.deviceInfo?.deviceName?.takeIf { it.isNotBlank() }
        ?: state.session.deviceInfo?.vendorModel?.takeIf { it.isNotBlank() }
        ?: when (val conn = state.connectionState) {
            is BleConnectionState.Connected -> conn.name
            is BleConnectionState.Connecting -> conn.name
            is BleConnectionState.Discovering -> conn.name
            is BleConnectionState.ConnectionLost -> conn.name
            else -> "JK BMS Monitor"
        }
    val isLive = state.session.isUnlocked && state.session.telemetry != null && !state.isViewingSavedSnapshot

    Surface(color = OledTokens.Card) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 34.dp else 40.dp)
                    .padding(horizontal = 10.dp, vertical = if (compact) 3.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    LiveDot(active = isLive)
                    Text(
                        text = activeTitle,
                        color = OledTokens.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.isViewingSavedSnapshot) {
                        StatusPill(
                            text = strings.savedOfflineBadge,
                            textColor = OledTokens.AmberText,
                            containerColor = OledTokens.AmberSoft,
                            borderColor = OledTokens.AmberBorder,
                            fontSize = 9.5.sp,
                            horizontalPadding = 6.dp,
                            verticalPadding = 2.dp
                        )
                    }
                    ReadOnlyPill(label = strings.readOnly)
                }
            }
            HorizontalDivider(color = OledTokens.CardBorder, thickness = 1.dp)
        }
    }
}

@Composable
private fun AppBottomNavBar(
    selectedTab: AppTab,
    activeCellCount: Int?,
    strings: UiStrings,
    compactSplit50: Boolean,
    compact320: Boolean,
    onSelectTab: (AppTab) -> Unit
) {
    Surface(color = OledTokens.Card) {
        Column {
            HorizontalDivider(color = OledTokens.CardBorder, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (compactSplit50 || compact320) 5.dp else 8.dp,
                        vertical = if (compactSplit50) 2.dp else 4.dp
                    ),
                horizontalArrangement = Arrangement.spacedBy(if (compact320) 3.dp else 6.dp)
            ) {
                for (tab in AppTab.entries) {
                    val active = tab == selectedTab
                    NavItemBox(
                        icon = tabIcon(tab),
                        label = tabLabel(tab, activeCellCount, strings),
                        active = active,
                        fontSizeSp = if (compact320 || compactSplit50) 9.5f else 10.5f,
                        minHeightDp = if (compactSplit50) 44 else 48,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavigationRail(
    selectedTab: AppTab,
    activeCellCount: Int?,
    strings: UiStrings,
    onSelectTab: (AppTab) -> Unit
) {
    Surface(color = OledTokens.Card) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (tab in AppTab.entries) {
                    NavItemBox(
                        icon = tabIcon(tab),
                        label = tabLabel(tab, activeCellCount, strings),
                        active = tab == selectedTab,
                        fontSizeSp = 9.5f,
                        minHeightDp = 48,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
            VerticalDivider(color = OledTokens.CardBorder, thickness = 1.dp)
        }
    }
}

@Composable
private fun NavItemBox(
    icon: ImageVector,
    label: String,
    active: Boolean,
    fontSizeSp: Float,
    minHeightDp: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (active) OledTokens.AccentSoft else Color.Transparent
    val fg = if (active) OledTokens.Accent else OledTokens.Muted

    Column(
        modifier = modifier
            .heightIn(min = minHeightDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = fg,
            fontSize = fontSizeSp.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun tabIcon(tab: AppTab): ImageVector = when (tab) {
    AppTab.MONITOR -> AppIcons.Bolt
    AppTab.CELLS -> AppIcons.Grid
    AppTab.CHARTS -> AppIcons.Chart
    AppTab.SETTINGS -> AppIcons.Lock
    AppTab.CONNECT -> AppIcons.Bluetooth
}

private fun tabLabel(tab: AppTab, activeCellCount: Int?, strings: UiStrings): String = when (tab) {
    AppTab.MONITOR -> strings.monitor
    AppTab.CELLS -> strings.cellsTabLabel(activeCellCount)
    AppTab.CHARTS -> strings.chart
    AppTab.SETTINGS -> strings.settings
    AppTab.CONNECT -> strings.connect
}
