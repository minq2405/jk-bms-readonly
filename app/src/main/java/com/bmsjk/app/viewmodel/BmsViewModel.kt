package com.bmsjk.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bmsjk.app.ble.BleConnectionState
import com.bmsjk.app.ble.BleDeviceItem
import com.bmsjk.app.ble.JkBleManager
import com.bmsjk.app.ble.MacPayloadParser
import com.bmsjk.app.data.AppLanguage
import com.bmsjk.app.data.SharedPrefsPasscodeStore
import com.bmsjk.core.protocol.ProtocolMode
import com.bmsjk.core.session.BmsSessionEngine
import com.bmsjk.core.session.BmsSessionSnapshot
import com.bmsjk.core.session.ChartMetric
import com.bmsjk.core.session.ChartWindow
import com.bmsjk.core.session.MetricStats
import com.bmsjk.core.session.PasscodeStatus
import com.bmsjk.core.session.TrendSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class AppTab {
    MONITOR,
    CELLS,
    CHARTS,
    SETTINGS,
    CONNECT
}

data class BmsUiState(
    val session: BmsSessionSnapshot = BmsSessionSnapshot(),
    val connectionState: BleConnectionState = BleConnectionState.Disconnected,
    val scannedDevices: List<BleDeviceItem> = emptyList(),
    val isScanning: Boolean = false,
    val scanCompletedOnce: Boolean = false,
    val searchQuery: String = "",
    val language: AppLanguage = AppLanguage.VI,
    val selectedTab: AppTab = AppTab.CONNECT,
    val hudShowCells: Boolean = false,
    val chartMetric: ChartMetric = ChartMetric.PACK_VOLTAGE,
    val chartWindow: ChartWindow = ChartWindow.FIVE_MINUTES,
    val chartSamples: List<TrendSample> = emptyList(),
    val chartStats: MetricStats = MetricStats(null, null, null, null),
    val bannerMessage: String? = null
) {
    /**
     * Dynamic cell count detected from live telemetry or BMS settings (null when unknown).
     */
    val activeCellCount: Int?
        get() = session.telemetry?.enabledCellCount?.takeIf { it > 0 }
            ?: session.settings?.cellCount?.takeIf { it > 0 }
}

class BmsViewModel(application: Application) : AndroidViewModel(application) {

    private val store = SharedPrefsPasscodeStore(application)
    private val engine = BmsSessionEngine(passcodeStore = store)
    private val bleManager = JkBleManager(application, engine, viewModelScope)

    private val selectedTab = MutableStateFlow(AppTab.CONNECT)
    private val searchQuery = MutableStateFlow("")
    private val language = MutableStateFlow(store.getLanguage())
    private val hudShowCells = MutableStateFlow(false)
    private val chartMetric = MutableStateFlow(ChartMetric.PACK_VOLTAGE)
    private val chartWindow = MutableStateFlow(ChartWindow.FIVE_MINUTES)
    private val bannerMessage = MutableStateFlow<String?>(null)

    private data class NavPrefs(
        val tab: AppTab,
        val query: String,
        val lang: AppLanguage,
        val hudCells: Boolean,
        val metric: ChartMetric,
        val window: ChartWindow,
        val banner: String?
    )

    private val navPrefsFlow = combine(
        combine(selectedTab, searchQuery, language, hudShowCells) { tab, query, lang, hud ->
            Quad(tab, query, lang, hud)
        },
        combine(chartMetric, chartWindow, bannerMessage) { metric, window, banner ->
            Triple(metric, window, banner)
        }
    ) { a, b ->
        NavPrefs(
            tab = a.first,
            query = a.second,
            lang = a.third,
            hudCells = a.fourth,
            metric = b.first,
            window = b.second,
            banner = b.third
        )
    }

    private val bleFlow = combine(
        bleManager.connectionState,
        bleManager.scannedDevices,
        bleManager.isScanning,
        bleManager.scanCompletedOnce
    ) { conn, devices, scanning, doneOnce ->
        Quad(conn, devices, scanning, doneOnce)
    }

    val uiState: StateFlow<BmsUiState> = combine(
        engine.snapshot,
        bleFlow,
        navPrefsFlow
    ) { session, ble, prefs ->
        val nowMs = session.telemetry?.timestampMs ?: System.currentTimeMillis()
        val samples = engine.queryChart(prefs.window, nowMs)
        val stats = engine.queryMetricStats(prefs.metric, prefs.window, nowMs)
        val filtered = filterDevices(ble.second, prefs.query)

        BmsUiState(
            session = session,
            connectionState = ble.first,
            scannedDevices = filtered,
            isScanning = ble.third,
            scanCompletedOnce = ble.fourth,
            searchQuery = prefs.query,
            language = prefs.lang,
            selectedTab = prefs.tab,
            hudShowCells = prefs.hudCells,
            chartMetric = prefs.metric,
            chartWindow = prefs.window,
            chartSamples = samples,
            chartStats = stats,
            bannerMessage = prefs.banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = BmsUiState(language = store.getLanguage())
    )

    fun selectTab(tab: AppTab) {
        selectedTab.value = tab
    }

    fun toggleHudCells() {
        hudShowCells.value = !hudShowCells.value
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun toggleLanguage() {
        val next = if (language.value == AppLanguage.VI) AppLanguage.EN else AppLanguage.VI
        language.value = next
        store.setLanguage(next)
    }

    fun startOneShotScanIfPermitted(hasPermissions: Boolean) {
        if (!hasPermissions) return
        if (!bleManager.isBluetoothEnabled()) {
            bannerMessage.value = if (language.value == AppLanguage.VI) {
                "Vui lòng bật Bluetooth để quét JK BMS"
            } else {
                "Please enable Bluetooth to scan for JK BMS"
            }
            return
        }
        bannerMessage.value = null
        bleManager.startOneShotScan()
    }

    fun triggerInitialScanIfNeeded(hasPermissions: Boolean) {
        if (!hasPermissions) return
        if (!bleManager.scanCompletedOnce.value && !bleManager.isScanning.value) {
            startOneShotScanIfPermitted(true)
        }
    }

    fun connectToDevice(device: BleDeviceItem) {
        bannerMessage.value = null
        bleManager.connect(device.address, device.name)
    }

    fun connectByQrOrMacPayload(rawPayload: String): Boolean {
        val mac = MacPayloadParser.extractMac(rawPayload)
        if (mac == null) {
            bannerMessage.value = if (language.value == AppLanguage.VI) {
                "Không tìm thấy địa chỉ MAC hợp lệ trong mã QR/văn bản"
            } else {
                "No valid BLE MAC address found in QR/text"
            }
            return false
        }
        bannerMessage.value = null
        bleManager.connect(mac, mac)
        return true
    }

    fun disconnect() {
        bleManager.disconnect()
        engine.startSession("")
        bannerMessage.value = null
    }

    fun submitPasscode(code: String) {
        val status = engine.submitPasscode(code)
        if (status is PasscodeStatus.Unlocked) {
            bannerMessage.value = null
            bleManager.triggerImmediateCellRefresh()
            selectedTab.value = AppTab.MONITOR
        }
    }

    fun forgetSavedPasscode() {
        engine.forgetPasscodeForCurrentDevice()
        bannerMessage.value = if (language.value == AppLanguage.VI) {
            "Đã xóa passcode đã lưu cho thiết bị này"
        } else {
            "Cleared saved passcode for this device"
        }
    }

    fun setProtocolMode(mode: ProtocolMode) {
        engine.setProtocolMode(mode)
    }

    fun setChartMetric(metric: ChartMetric) {
        chartMetric.value = metric
    }

    fun setChartWindow(window: ChartWindow) {
        chartWindow.value = window
    }

    fun toggleChartPaused() {
        engine.setChartRecordingPaused(!engine.snapshot.value.chartRecordingPaused)
    }

    fun clearChartHistory() {
        engine.clearChartHistory()
    }

    private fun filterDevices(devices: List<BleDeviceItem>, query: String): List<BleDeviceItem> {
        val q = query.trim()
        if (q.isEmpty()) return devices
        return devices.filter {
            it.name.contains(q, ignoreCase = true) || it.address.contains(q, ignoreCase = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
