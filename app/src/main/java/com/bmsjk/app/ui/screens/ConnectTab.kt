package com.bmsjk.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmsjk.app.ble.BleConnectionState
import com.bmsjk.app.ble.BleDeviceItem
import com.bmsjk.app.ui.components.AppIcons
import com.bmsjk.app.ui.components.LiveDot
import com.bmsjk.app.ui.components.OledCard
import com.bmsjk.app.ui.components.StatusPill
import com.bmsjk.app.ui.i18n.UiStrings
import com.bmsjk.app.ui.theme.OledTokens
import com.bmsjk.app.ui.theme.monoStyle
import com.bmsjk.app.viewmodel.BmsUiState
import com.bmsjk.core.session.PasscodeStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectTab(
    state: BmsUiState,
    strings: UiStrings,
    hasBlePermissions: Boolean,
    onRequestBlePermissions: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onConnectDevice: (BleDeviceItem) -> Unit,
    onConnectQrPayload: (String) -> Boolean,
    onDisconnect: () -> Unit,
    onSubmitPasscode: (String) -> Unit,
    onForgetPasscode: () -> Unit
) {
    val scroll = rememberScrollState()
    var showQrDialog by rememberSaveable { mutableStateOf(false) }
    var qrInput by rememberSaveable { mutableStateOf("") }
    var passcodeInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.session.deviceAddress) {
        passcodeInput = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scroll)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!hasBlePermissions) {
            OledCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = OledTokens.DischargeAmber,
                containerColor = OledTokens.AmberSoft
            ) {
                Text(
                    text = strings.permissionTitle,
                    color = OledTokens.AmberText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = strings.permissionDesc, color = OledTokens.Text, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRequestBlePermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OledTokens.Accent,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(strings.grantPermission, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        state.bannerMessage?.let { msg ->
            OledCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = OledTokens.DischargeAmber
            ) {
                Text(text = msg, color = OledTokens.AmberText, fontSize = 11.5.sp)
            }
        }

        // Search + One-shot JK BMS Scan + QR/MAC
        OledCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(strings.searchHint, color = OledTokens.Muted, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = AppIcons.Search,
                        contentDescription = null,
                        tint = OledTokens.Muted,
                        modifier = Modifier.size(16.dp)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OledTokens.Accent,
                    unfocusedBorderColor = OledTokens.CardBorder,
                    focusedTextColor = OledTokens.Text,
                    unfocusedTextColor = OledTokens.Text,
                    focusedContainerColor = OledTokens.CardElevated,
                    unfocusedContainerColor = OledTokens.CardElevated
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActionPillButton(
                    icon = AppIcons.Refresh,
                    label = if (state.isScanning) strings.scanning else strings.scan,
                    highlighted = true,
                    onClick = onScanClick
                )
                ActionPillButton(
                    icon = AppIcons.QrCode,
                    label = strings.qrButton,
                    highlighted = false,
                    onClick = { showQrDialog = true }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (state.scanCompletedOnce && !state.isScanning) strings.scanIdleHint else strings.scanOnceHint,
                color = OledTokens.Muted,
                fontSize = 10.5.sp
            )
        }

        // Active / Lost / Error connection summary card
        val conn = state.connectionState
        if (conn !is BleConnectionState.Disconnected) {
            val connAddress = when (conn) {
                is BleConnectionState.Connected -> conn.address
                is BleConnectionState.Connecting -> conn.address
                is BleConnectionState.Discovering -> conn.address
                is BleConnectionState.ConnectionLost -> conn.address
                is BleConnectionState.Error -> ""
                BleConnectionState.Disconnected -> ""
            }
            val connName = when (conn) {
                is BleConnectionState.Connected -> conn.name
                is BleConnectionState.Connecting -> conn.name
                is BleConnectionState.Discovering -> conn.name
                is BleConnectionState.ConnectionLost -> "${conn.name} (${conn.reason})"
                is BleConnectionState.Error -> conn.message
                BleConnectionState.Disconnected -> ""
            }
            val isError = conn is BleConnectionState.Error
            val isLost = conn is BleConnectionState.ConnectionLost

            OledCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = when {
                    isError -> OledTokens.DangerRed
                    isLost -> OledTokens.DischargeAmber
                    else -> OledTokens.Accent.copy(alpha = 0.55f)
                },
                containerColor = when {
                    isError -> OledTokens.DangerSoft
                    isLost -> OledTokens.AmberSoft
                    else -> OledTokens.Card
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        LiveDot(active = conn is BleConnectionState.Connected)
                        Column {
                            Text(
                                text = connName,
                                color = OledTokens.Text,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (connAddress.isNotEmpty()) {
                                Text(
                                    text = connAddress,
                                    style = monoStyle(size = 10.5.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = when {
                                isError -> strings.errorBadge
                                isLost -> strings.lostBadge
                                conn is BleConnectionState.Connected -> strings.connectedBadge
                                else -> strings.connectingBadge
                            },
                            textColor = when {
                                isError -> OledTokens.DangerRed
                                isLost -> OledTokens.AmberText
                                else -> OledTokens.Accent
                            },
                            borderColor = when {
                                isError -> OledTokens.DangerRed
                                isLost -> OledTokens.DischargeAmber
                                else -> OledTokens.Accent
                            }
                        )
                        OutlinedButton(
                            onClick = onDisconnect,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, OledTokens.CardBorder),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(strings.disconnect, color = OledTokens.Text, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // First-time Passcode Verification Card
        val passStatus = state.session.passcodeStatus
        if (passStatus is PasscodeStatus.Required || passStatus is PasscodeStatus.Mismatch) {
            OledCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (passStatus is PasscodeStatus.Mismatch) OledTokens.DangerRed else OledTokens.Accent
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Lock,
                        contentDescription = null,
                        tint = OledTokens.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = strings.passcodeTitle,
                        color = OledTokens.Text,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (passStatus is PasscodeStatus.Mismatch) strings.passcodeWrong else strings.passcodeDesc,
                    color = if (passStatus is PasscodeStatus.Mismatch) OledTokens.DangerRed else OledTokens.Muted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = passcodeInput,
                    onValueChange = { passcodeInput = it },
                    placeholder = {
                        Text(
                            text = strings.passcodeHint,
                            color = OledTokens.Muted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    textStyle = monoStyle(size = 16.sp, weight = FontWeight.Bold, color = OledTokens.Text)
                        .copy(textAlign = TextAlign.Center),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onSubmitPasscode(passcodeInput) }
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OledTokens.Accent,
                        unfocusedBorderColor = OledTokens.Accent,
                        focusedContainerColor = OledTokens.CardElevated,
                        unfocusedContainerColor = OledTokens.CardElevated
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { onSubmitPasscode(passcodeInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OledTokens.Accent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(strings.confirmPasscode, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        } else if (state.session.isUnlocked && !state.session.deviceAddress.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onForgetPasscode) {
                    Text(strings.forgetPasscode, color = OledTokens.Muted, fontSize = 11.sp)
                }
            }
        }

        // Discovered JK BMS devices list
        if (state.scannedDevices.isEmpty() && !state.isScanning && state.scanCompletedOnce) {
            OledCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = strings.noBmsFoundHint,
                    color = OledTokens.Muted,
                    fontSize = 11.5.sp
                )
            }
        }

        for (device in state.scannedDevices) {
            OledCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = device.name,
                                color = OledTokens.Text,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (device.hasJkService) {
                                StatusPill(
                                    text = "JK 0xFFE0",
                                    textColor = OledTokens.Accent,
                                    borderColor = OledTokens.Accent,
                                    fontSize = 9.5.sp,
                                    horizontalPadding = 6.dp,
                                    verticalPadding = 2.dp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${device.address} • ${device.rssi} dBm",
                            style = monoStyle(size = 10.5.sp, weight = FontWeight.Medium, color = OledTokens.Muted)
                        )
                    }
                    Button(
                        onClick = { onConnectDevice(device) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OledTokens.Accent,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(strings.connectBtn, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            containerColor = OledTokens.Card,
            titleContentColor = OledTokens.Text,
            textContentColor = OledTokens.Muted,
            title = { Text(strings.qrDialogTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.qrDialogHint, fontSize = 11.5.sp)
                    OutlinedTextField(
                        value = qrInput,
                        onValueChange = { qrInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OledTokens.Accent,
                            unfocusedBorderColor = OledTokens.CardBorder,
                            focusedTextColor = OledTokens.Text,
                            unfocusedTextColor = OledTokens.Text
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onConnectQrPayload(qrInput)) {
                            showQrDialog = false
                            qrInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OledTokens.Accent,
                        contentColor = Color.Black
                    )
                ) {
                    Text(strings.connectBtn, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text(strings.cancel, color = OledTokens.Muted)
                }
            }
        )
    }
}

@Composable
private fun ActionPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (highlighted) OledTokens.AccentSoft else OledTokens.CardElevated,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (highlighted) OledTokens.Accent else OledTokens.CardBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlighted) OledTokens.Accent else OledTokens.Muted,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                color = if (highlighted) OledTokens.Accent else OledTokens.Text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
