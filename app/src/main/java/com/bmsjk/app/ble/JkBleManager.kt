package com.bmsjk.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.bmsjk.core.protocol.DecodedFrame
import com.bmsjk.core.protocol.JkFrameCodec
import com.bmsjk.core.session.BmsSessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Battery-conscious Android BLE manager for strictly read-only JK BMS communication.
 * - One-shot BLE scanning (never continuously polls in background).
 * - Preserves active GATT connection while scanning or navigating tabs.
 * - Resolves dual-0xFFE1 characteristics by WRITE and NOTIFY properties.
 * - Sends only 0x97 (Device Info) and 0x96 (Cell/Settings Info) commands.
 */
@SuppressLint("MissingPermission")
class JkBleManager(
    context: Context,
    private val engine: BmsSessionEngine,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BleDeviceItem>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceItem>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanCompletedOnce = MutableStateFlow(false)
    val scanCompletedOnce: StateFlow<Boolean> = _scanCompletedOnce.asStateFlow()

    private val discoveredMap = LinkedHashMap<String, BleDeviceItem>()
    private var scanStopJob: Job? = null
    private var pollJob: Job? = null
    private var activeGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    @Volatile
    private var lastCellRxMs: Long = 0L

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun startOneShotScan(durationMs: Long = 8_000L) {
        val adapter = bluetoothAdapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || scanner == null) {
            _isScanning.value = false
            return
        }

        stopScanInternal()
        discoveredMap.clear()
        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        runCatching {
            scanner.startScan(null, settings, scanCallback)
            scanStopJob = scope.launch {
                delay(durationMs)
                stopScanInternal()
                _scanCompletedOnce.value = true
            }
        }.onFailure {
            _isScanning.value = false
        }
    }

    fun stopScan() {
        stopScanInternal()
        _scanCompletedOnce.value = true
    }

    private fun stopScanInternal() {
        scanStopJob?.cancel()
        scanStopJob = null
        if (_isScanning.value) {
            runCatching {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
        }
        _isScanning.value = false
    }

    fun connect(address: String, displayName: String? = null) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = BleConnectionState.Error("Bluetooth is disabled")
            return
        }

        val normalized = address.trim().uppercase()
        val device = runCatching { adapter.getRemoteDevice(normalized) }.getOrNull()
        if (device == null) {
            _connectionState.value = BleConnectionState.Error("Invalid MAC address: $normalized")
            return
        }

        // Stop scan to keep connection stable, and close any previous GATT first.
        stopScanInternal()
        closeGattInternal()

        val label = displayName?.takeIf { it.isNotBlank() }
            ?: runCatching { device.name }.getOrNull()
            ?: normalized

        engine.startSession(normalized)
        lastCellRxMs = 0L
        _connectionState.value = BleConnectionState.Connecting(normalized, label)

        activeGatt = device.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    fun disconnect() {
        closeGattInternal()
        _connectionState.value = BleConnectionState.Disconnected
    }

    fun triggerImmediateCellRefresh() {
        val gatt = activeGatt ?: return
        val wChar = writeChar ?: return
        if (!engine.snapshot.value.isUnlocked) return
        writeReadCommand(gatt, wChar, engine.nextReadCellInfoFrame())
    }

    private fun closeGattInternal() {
        pollJob?.cancel()
        pollJob = null
        writeChar = null
        notifyChar = null
        val gatt = activeGatt
        activeGatt = null
        if (gatt != null) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val addr = dev.address?.uppercase() ?: return
            val name = result.scanRecord?.deviceName
                ?: runCatching { dev.name }.getOrNull()
                ?: "(Unknown BLE)"
            val uuids = result.scanRecord?.serviceUuids.orEmpty()
            val hasJk = uuids.any { it == ParcelUuid(JkFrameCodec.SERVICE_UUID) } ||
                name.contains("JK", ignoreCase = true)

            discoveredMap[addr] = BleDeviceItem(
                address = addr,
                name = name,
                rssi = result.rssi,
                hasJkService = hasJk
            )

            _scannedDevices.value = discoveredMap.values.sortedWith(
                compareByDescending<BleDeviceItem> { it.hasJkService }
                    .thenByDescending { it.rssi }
            )
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val addr = gatt.device?.address?.uppercase() ?: ""
            val name = runCatching { gatt.device?.name }.getOrNull() ?: addr
            if (status != BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGattInternal()
                _connectionState.value = BleConnectionState.Error("GATT disconnected (status=$status)")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = BleConnectionState.Discovering(addr, name)
                    if (!gatt.requestMtu(247)) {
                        gatt.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    closeGattInternal()
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val addr = gatt.device?.address?.uppercase() ?: ""
            val name = runCatching { gatt.device?.name }.getOrNull() ?: addr
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeGattInternal()
                _connectionState.value = BleConnectionState.Error("Service discovery failed ($status)")
                return
            }

            val service = gatt.getService(JkFrameCodec.SERVICE_UUID)
            if (service == null) {
                closeGattInternal()
                _connectionState.value = BleConnectionState.Error("Service 0xFFE0 not found")
                return
            }

            val candidates = service.characteristics.filter {
                it.uuid == JkFrameCodec.CHARACTERISTIC_UUID
            }

            val wChar = candidates.firstOrNull { char ->
                (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                    (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
            }
            val nChar = candidates.firstOrNull { char ->
                (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                    (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            } ?: wChar

            if (wChar == null || nChar == null) {
                closeGattInternal()
                _connectionState.value = BleConnectionState.Error("0xFFE1 write/notify handles not found")
                return
            }

            writeChar = wChar
            notifyChar = nChar
            gatt.setCharacteristicNotification(nChar, true)

            val cccd = nChar.getDescriptor(JkFrameCodec.CCCD_UUID)
            if (cccd != null) {
                writeDescriptorCompat(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                _connectionState.value = BleConnectionState.Connected(addr, name)
                beginHandshake(gatt, wChar)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val addr = gatt.device?.address?.uppercase() ?: ""
            val name = runCatching { gatt.device?.name }.getOrNull() ?: addr
            val wChar = writeChar ?: return
            _connectionState.value = BleConnectionState.Connected(addr, name)
            beginHandshake(gatt, wChar)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val payload = characteristic.value ?: return
            handleIncomingChunk(gatt, payload)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingChunk(gatt, value)
        }
    }

    private fun handleIncomingChunk(gatt: BluetoothGatt, chunk: ByteArray) {
        val decoded = engine.onNotificationChunk(chunk) ?: return
        when (decoded) {
            is DecodedFrame.DeviceInfoFrame -> {
                if (engine.snapshot.value.isUnlocked) {
                    writeChar?.let { wChar ->
                        writeReadCommand(gatt, wChar, engine.nextReadCellInfoFrame())
                    }
                }
            }
            is DecodedFrame.CellInfoFrame -> {
                lastCellRxMs = System.currentTimeMillis()
            }
            is DecodedFrame.SettingsFrame,
            is DecodedFrame.UnsupportedFrame -> Unit
        }
    }

    private fun beginHandshake(gatt: BluetoothGatt, wChar: BluetoothGattCharacteristic) {
        writeReadCommand(gatt, wChar, engine.nextReadDeviceInfoFrame())
        startPollLoop(gatt, wChar)
    }

    private fun startPollLoop(gatt: BluetoothGatt, wChar: BluetoothGattCharacteristic) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var requestedInitialCells = false
            while (isActive) {
                delay(1_000L)
                val snap = engine.snapshot.value
                if (!snap.isUnlocked) continue
                if (!requestedInitialCells) {
                    requestedInitialCells = true
                    writeReadCommand(gatt, wChar, engine.nextReadCellInfoFrame())
                } else if (System.currentTimeMillis() - lastCellRxMs > 1_500L) {
                    writeReadCommand(gatt, wChar, engine.nextReadCellInfoFrame())
                }
            }
        }
    }

    private fun writeReadCommand(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        command: ByteArray
    ) {
        val writeType =
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, command, writeType)
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = command
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }
}
