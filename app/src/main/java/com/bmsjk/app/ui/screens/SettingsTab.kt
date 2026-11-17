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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.data.AppLanguage
import com.bmsjk.app.ui.components.AppIcons
import com.bmsjk.app.ui.components.CardLabelRow
import com.bmsjk.app.ui.components.OledCard
import com.bmsjk.app.ui.i18n.UiStrings
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.ui.theme.monoStyle
import com.bmsjk.app.viewmodel.BmsUiState
import com.bmsjk.core.protocol.ProtocolMode
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsTab(
    state: BmsUiState,
    strings: UiStrings,
    onToggleLanguage: () -> Unit,
    onSelectProtocolMode: (ProtocolMode) -> Unit
) {
    val scroll = rememberScrollState()
    val info = state.session.deviceInfo
    val settings = state.session.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // App Preferences: Language & Protocol Mode
        OledCard(modifier = Modifier.fillMaxWidth()) {
            CardLabelRow(icon = AppIcons.Lock, title = strings.protocolGroup)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = strings.languageLabel, color = OledTokens.Muted, fontSize = 11.5.sp)
                Surface(
                    color = OledTokens.AccentSoft,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, OledTokens.Accent),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onToggleLanguage() }
                ) {
                    Text(
                        text = if (state.language == AppLanguage.VI) "Tiếng Việt" else "English",
                        color = OledTokens.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (mode in ProtocolMode.entries) {
                    val selected = state.session.protocolMode == mode
                    Surface(
                        color = if (selected) OledTokens.AccentSoft else OledTokens.CardElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selected) OledTokens.Accent else OledTokens.CardBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectProtocolMode(mode) }
                    ) {
                        Text(
                            text = mode.name,
                            style = monoStyle(
                                size = 10.5.sp,
                                weight = FontWeight.Bold,
                                color = if (selected) OledTokens.Accent else OledTokens.Muted
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Frame 0x03 Device Info (Read-Only)
        OledCard(modifier = Modifier.fillMaxWidth()) {
            RoGroupHeader(title = strings.deviceInfoGroup, badge = "INFO")
            if (info == null) {
                Text(text = "--", color = OledTokens.Muted, fontSize = 11.sp)
            } else {
                RoRow("Model / HW / SW", "${info.vendorModel} • ${info.hardwareVersion} • ${info.softwareVersion}")
                RoRow("Device Name", info.deviceName)
                RoRow("Serial / Mfg", "${info.serialNumber} • ${info.manufacturingDate}")
                RoRow("Uptime / Power-ons", "${info.uptimeSeconds}s • ${info.powerOnCount}x")
                RoRow(
                    "Protocol",
                    state.session.detectedVariant?.name ?: state.session.protocolMode.name
                )
            }
        }

        // Frame 0x01 Settings (Read-Only)
        OledCard(modifier = Modifier.fillMaxWidth()) {
            RoGroupHeader(title = strings.packBalanceGroup, badge = "READ-ONLY")
            if (settings == null) {
                Text(text = "--", color = OledTokens.Muted, fontSize = 11.sp)
            } else {
                RoRow(
                    "Cell / Capacity",
                    "${settings.cellCount} S • ${settings.nominalCapacityAh?.let { String.format(Locale.US, "%.1f Ah", it) } ?: "--"}"
                )
                RoRow(
                    "Start Bal / Trigger",
                    String.format(Locale.US, "%.3f V • %.3f V", settings.startBalanceVoltageV, settings.balanceTriggerVoltageV)
                )
                RoRow(
                    "Max Balance A",
                    String.format(Locale.US, "%.2f A", settings.maxBalanceCurrentA)
                )
                RoRow(
                    "Chg / Dsg / Bal",
                    "${boolOnOff(settings.chargeSwitchOn)} / ${boolOnOff(settings.dischargeSwitchOn)} / ${if (settings.balancerSwitchOn) "ON" else "OFF"}"
                )
            }
        }

        OledCard(modifier = Modifier.fillMaxWidth()) {
            RoGroupHeader(title = strings.voltageCurrentGroup, badge = "READ-ONLY")
            if (settings == null) {
                Text(text = "--", color = OledTokens.Muted, fontSize = 11.sp)
            } else {
                RoRow(
                    "Cell OVP / OVPR",
                    "${fmtV(settings.cellOvpV)} / ${fmtV(settings.cellOvprV)}"
                )
                RoRow(
                    "Cell UVP / UVPR",
                    "${fmtV(settings.cellUvpV)} / ${fmtV(settings.cellUvprV)}"
                )
                RoRow("Power Off Voltage", String.format(Locale.US, "%.3f V", settings.powerOffVoltageV))
                RoRow(
                    "Max Chg / Max Dsg",
                    "${fmtA(settings.maxChargeCurrentA)} / ${fmtA(settings.maxDischargeCurrentA)}"
                )
                RoRow("SCP Delay", settings.scpDelayUs?.let { "$it us" } ?: "--")
            }
        }

        OledCard(modifier = Modifier.fillMaxWidth()) {
            RoGroupHeader(title = strings.tempProtectGroup, badge = "READ-ONLY")
            if (settings == null) {
                Text(text = "--", color = OledTokens.Muted, fontSize = 11.sp)
            } else {
                RoRow(
                    "Chg / Dsg OTP",
                    "${fmtC(settings.chargeOtpC)} / ${fmtC(settings.dischargeOtpC)}"
                )
                RoRow("MOS OTP", fmtC(settings.mosfetOtpC))
                RoRow("Charge UTP", fmtC(settings.chargeUtpC))
            }
        }
    }
}

@Composable
private fun RoGroupHeader(title: String, badge: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = OledTokens.Accent,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = badge,
            style = monoStyle(size = 10.sp, weight = FontWeight.Bold, color = OledTokens.Accent)
        )
    }
}

@Composable
private fun RoRow(key: String, value: String) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = key,
                color = OledTokens.Muted,
                fontSize = 11.sp,
                modifier = Modifier.weight(0.45f)
            )
            Text(
                text = value,
                style = monoStyle(size = 11.sp, weight = FontWeight.Bold, color = OledTokens.Text),
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.55f)
            )
        }
        HorizontalDivider(color = OledTokens.CardBorder.copy(alpha = 0.55f), thickness = 0.6.dp)
    }
}

private fun boolOnOff(v: Boolean?): String = when (v) {
    true -> "ON"
    false -> "OFF"
    null -> "--"
}

private fun fmtV(v: Float?): String = v?.let { String.format(Locale.US, "%.3f V", it) } ?: "--"
private fun fmtA(v: Float?): String = v?.let { String.format(Locale.US, "%.1f A", it) } ?: "--"
private fun fmtC(v: Float?): String = v?.let { String.format(Locale.US, "%.1f °C", it) } ?: "--"
