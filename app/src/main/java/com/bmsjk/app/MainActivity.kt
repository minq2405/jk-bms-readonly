package com.bmsjk.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bmsjk.app.ui.screens.DashboardShell
import com.bmsjk.app.ui.theme.JkBmsOledTheme
import com.bmsjk.app.viewmodel.BmsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK)
        )

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val requiredPermissions = remember { requiredBlePermissions() }
            var hasBlePermissions by remember {
                mutableStateOf(hasAllPermissions(requiredPermissions))
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                hasBlePermissions = grants.values.all { it } || hasAllPermissions(requiredPermissions)
                if (hasBlePermissions) {
                    viewModel.triggerInitialScanIfNeeded(true)
                }
            }

            LaunchedEffect(hasBlePermissions) {
                if (hasBlePermissions) {
                    viewModel.triggerInitialScanIfNeeded(true)
                } else {
                    permissionLauncher.launch(requiredPermissions)
                }
            }

            JkBmsOledTheme {
                DashboardShell(
                    state = state,
                    hasBlePermissions = hasBlePermissions,
                    onRequestBlePermissions = { permissionLauncher.launch(requiredPermissions) },
                    onSelectTab = viewModel::selectTab,
                    onToggleHudCells = viewModel::toggleHudCells,
                    onToggleLanguage = viewModel::toggleLanguage,
                    onSelectProtocolMode = viewModel::setProtocolMode,
                    onSelectChartMetric = viewModel::setChartMetric,
                    onSelectChartWindow = viewModel::setChartWindow,
                    onToggleChartPaused = viewModel::toggleChartPaused,
                    onClearChartHistory = viewModel::clearChartHistory,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onScanClick = {
                        if (hasBlePermissions) {
                            viewModel.startOneShotScanIfPermitted(true)
                        } else {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    },
                    onConnectDevice = viewModel::connectToDevice,
                    onConnectQrPayload = viewModel::connectByQrOrMacPayload,
                    onDisconnect = viewModel::disconnect,
                    onSubmitPasscode = viewModel::submitPasscode,
                    onForgetPasscode = viewModel::forgetSavedPasscode,
                    onKeepLastValues = viewModel::keepLastTelemetryAndStay,
                    onDisconnectAndGoToConnect = viewModel::disconnectAndGoToConnect
                )
            }
        }
    }

    private fun requiredBlePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    private fun hasAllPermissions(permissions: Array<String>): Boolean =
        permissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
}
