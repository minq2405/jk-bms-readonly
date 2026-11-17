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
 * - Filters scan results strictly to JK BMS devices ([JkBleFilter]).
 * - One-shot BLE scanning (never continuously polls in background).
 * - Preserves active GATT connection while scanning or navigating tabs.
 * - Guards all GATT / BLE / frame operations against unknown or misbehaving devices.
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
    private val discoveredLock = Any()
    private val connectionLock = Any()
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false
    private var lastWriteDispatchMs = 0L

    private var scanStopJob: Job? = null
    private var pollJob: Job? = null
    private var discoveryTimeoutJob: Job? = null
    private var handshakeTimeoutJob: Job? = null

    @Volatile
    private var activeGatt: BluetoothGatt? = null

    @Volatile
    private var writeChar: BluetoothGattCharacteristic? = null

    @Volatile
    private var notifyChar: BluetoothGattCharacteristic? = null

    @Volatile
    private var lastCellRxMs: Long = 0L

    @Volatile
    private var receivedValidBmsFrame: Boolean = false

    @Volatile
    private var wasConnectedOnce: Boolean = false

    @Volatile
    private var userInitiatedDisconnect: Boolean = false

    fun isBluetoothEnabled(): Boolean =
        runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)

    fun startOneShotScan(durationMs: Long = 8_000L) {
        val adapter = bluetoothAdapter
        val scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull()
        if (adapter == null || !isBluetoothEnabled() || scanner == null) {
            _isScanning.value = false
            return
        }

        stopScanInternal()
        synchronized(discoveredLock) {
            discoveredMap.clear()
        }
        _scannedDevices.value = emptyList()
        _isScanning.value = true

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
            scanStopJob = scope.launch {
                delay(durationMs)
                stopScanInternal()
                _scanCompletedOnce.value = true
            }
        } catch (t: Throwable) {
            _isScanning.value = false
            _connectionState.value = BleConnectionState.Error(
                t.message ?: "Unable to start BLE scan"
            )
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
        if (adapter == null || !isBluetoothEnabled()) {
            _connectionState.value = BleConnectionState.Error("Bluetooth is disabled")
            return
        }

        val normalized = address.trim().uppercase()
        val device = try {
            adapter.getRemoteDevice(normalized)
        } catch (t: Throwable) {
            _connectionState.value = BleConnectionState.Error(
                "Invalid BLE address ($normalized): ${t.message ?: "bad format"}"
            )
            return
        }

        stopScanInternal()
        synchronized(connectionLock) {
            userInitiatedDisconnect = true
            closeGattInternalLocked()

            engine.startSession(normalized)
            lastCellRxMs = 0L
            receivedValidBmsFrame = false
            wasConnectedOnce = false
            userInitiatedDisconnect = false
        }

        val label = displayName?.takeIf { it.isNotBlank() }
            ?: runCatching { device.name }.getOrNull()
            ?: normalized

        _connectionState.value = BleConnectionState.Connecting(normalized, label)

        try {
            synchronized(connectionLock) {
                val gatt = device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
                if (gatt == null) {
                    _connectionState.value = BleConnectionState.Error("Failed to open GATT connection to $label")
                    return
                }
                activeGatt = gatt
            }
            startDiscoveryWatchdog(label)
        } catch (sec: SecurityException) {
            closeGattInternal()
            _connectionState.value = BleConnectionState.Error(
                "Missing Bluetooth permission: ${sec.message ?: "denied"}"
            )
        } catch (t: Throwable) {
            closeGattInternal()
            _connectionState.value = BleConnectionState.Error(
                "Connection failed: ${t.message ?: t::class.java.simpleName}"
            )
        }
    }

    fun disconnect() {
        synchronized(connectionLock) {
            userInitiatedDisconnect = true
            wasConnectedOnce = false
            closeGattInternalLocked()
        }
        _connectionState.value = BleConnectionState.Disconnected
    }

    fun onSessionUnlocked() {
        if (!engine.snapshot.value.isUnlocked) return
        lastCellRxMs = System.currentTimeMillis()
        triggerImmediateCellRefresh()
    }

    fun triggerImmediateCellRefresh() {
        val gatt = activeGatt ?: return
        val wChar = writeChar ?: return
        if (!engine.snapshot.value.isUnlocked) return
        writeReadCommand(gatt, wChar, engine.nextReadCellInfoFrame())
    }

    private fun failConnection(reason: String) {
        synchronized(connectionLock) {
            wasConnectedOnce = false
            closeGattInternalLocked()
        }
        _connectionState.value = BleConnectionState.Error(reason)
    }

    private fun emitConnectionLostOnce(addr: String, name: String, reason: String) {
        synchronized(connectionLock) {
            if (!wasConnectedOnce || userInitiatedDisconnect) return
            wasConnectedOnce = false
            closeGattInternalLocked()
        }
        _connectionState.value = BleConnectionState.ConnectionLost(addr, name, reason)
    }

    private fun closeGattInternal() {
        synchronized(connectionLock) {
            closeGattInternalLocked()
        }
    }

    private fun closeGattInternalLocked() {
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = null
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = null
        pollJob?.cancel()
        pollJob = null
        writeQueue.clear()
        writeInFlight = false
        lastWriteDispatchMs = 0L
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
            try {
                val dev = result.device ?: return
                val addr = dev.address?.uppercase() ?: return
                val rawName = result.scanRecord?.deviceName
                    ?: runCatching { dev.name }.getOrNull()
                val uuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }

                if (!JkBleFilter.isJkBmsDevice(rawName, uuids)) {
                    return
                }

                val hasJkService = uuids.any { it == JkFrameCodec.SERVICE_UUID }
                val displayName = rawName?.takeIf { it.isNotBlank() } ?: "JK BMS ($addr)"

                val sorted = synchronized(discoveredLock) {
                    discoveredMap[addr] = BleDeviceItem(
                        address = addr,
                        name = displayName,
                        rssi = result.rssi,
                        hasJkService = hasJkService
                    )
                    discoveredMap.values.sortedWith(
                        compareByDescending<BleDeviceItem> { it.hasJkService }
                            .thenByDescending { it.rssi }
                    )
                }

                _scannedDevices.value = sorted
            } catch (_: Throwable) {
                // Ignore malformed advertisement packets from nearby BLE devices
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanStopJob?.cancel()
            scanStopJob = null
            _isScanning.value = false
            _connectionState.value = BleConnectionState.Error("BLE scan failed (code=$errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== activeGatt) return
            try {
                val addr = runCatching { gatt.device?.address?.uppercase() }.getOrNull().orEmpty()
                val name = runCatching { gatt.device?.name }.getOrNull() ?: addr

                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (wasConnectedOnce && !userInitiatedDisconnect) {
                        emitConnectionLostOnce(addr, name, "GATT status=$status")
                    } else {
                        failConnection("BLE GATT error (status=$status) on $name")
                    }
                    return
                }

                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionState.value = BleConnectionState.Discovering(addr, name)
                        val mtuRequested = runCatching { gatt.requestMtu(247) }.getOrDefault(false)
                        if (!mtuRequested && !gatt.discoverServices()) {
                            failConnection("Unable to start service discovery on $name")
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (wasConnectedOnce && !userInitiatedDisconnect) {
                            emitConnectionLostOnce(addr, name, "Disconnected")
                        } else {
                            closeGattInternal()
                            _connectionState.value = BleConnectionState.Disconnected
                        }
                    }
                }
            } catch (t: Throwable) {
                failConnection("GATT state error: ${t.message ?: t::class.java.simpleName}")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== activeGatt) return
            try {
                if (!gatt.discoverServices()) {
                    failConnection("Unable to discover BLE services after MTU")
                }
            } catch (t: Throwable) {
                failConnection("Service discovery error: ${t.message ?: t::class.java.simpleName}")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== activeGatt) return
            try {
                val addr = runCatching { gatt.device?.address?.uppercase() }.getOrNull().orEmpty()
                val name = runCatching { gatt.device?.name }.getOrNull() ?: addr
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failConnection("Service discovery failed (status=$status)")
                    return
                }

                val service = gatt.getService(JkFrameCodec.SERVICE_UUID)
                if (service == null) {
                    failConnection("Not a JK BMS device: missing service 0xFFE0")
                    return
                }

                val candidates = service.characteristics.filter {
                    it.uuid == JkFrameCodec.CHARACTERISTIC_UUID
                }
                if (candidates.isEmpty()) {
                    failConnection("Not a JK BMS device: missing characteristic 0xFFE1")
                    return
                }

                val wChar = candidates.firstOrNull { char ->
                    (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ||
                        (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                }
                val nChar = candidates.firstOrNull { char ->
                    (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                }

                if (wChar == null || nChar == null) {
                    failConnection("Incompatible BLE module: missing write/notify on 0xFFE1")
                    return
                }

                writeChar = wChar
                notifyChar = nChar
                if (!gatt.setCharacteristicNotification(nChar, true)) {
                    failConnection("Failed to enable local notifications on 0xFFE1")
                    return
                }

                val cccd = nChar.getDescriptor(JkFrameCodec.CCCD_UUID)
                if (cccd != null) {
                    val enableVal = if ((nChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    }
                    if (!writeDescriptorCompat(gatt, cccd, enableVal)) {
                        failConnection("Failed to write CCCD descriptor on 0xFFE1")
                    }
                } else {
                    discoveryTimeoutJob?.cancel()
                    discoveryTimeoutJob = null
                    _connectionState.value = BleConnectionState.Connected(addr, name)
                    beginHandshake(gatt, wChar)
                }
            } catch (t: Throwable) {
                failConnection("Unsupported device or GATT error: ${t.message ?: t::class.java.simpleName}")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (gatt !== activeGatt) return
            try {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    failConnection("Failed to subscribe JK BMS notifications (status=$status)")
                    return
                }
                val addr = runCatching { gatt.device?.address?.uppercase() }.getOrNull().orEmpty()
                val name = runCatching { gatt.device?.name }.getOrNull() ?: addr
                val wChar = writeChar ?: run {
                    failConnection("Handshake setup failed: missing write characteristic")
                    return
                }
                discoveryTimeoutJob?.cancel()
                discoveryTimeoutJob = null
                _connectionState.value = BleConnectionState.Connected(addr, name)
                beginHandshake(gatt, wChar)
            } catch (t: Throwable) {
                failConnection("Handshake setup failed: ${t.message ?: t::class.java.simpleName}")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (gatt !== activeGatt) return
            synchronized(connectionLock) {
                val next = writeQueue.removeFirstOrNull()
                if (next == null) {
                    writeInFlight = false
                    return
                }
                val wChar = writeChar
                if (wChar == null || !performGattWrite(gatt, wChar, next)) {
                    writeInFlight = false
                } else {
                    lastWriteDispatchMs = System.currentTimeMillis()
                }
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (gatt !== activeGatt) return
            @Suppress("DEPRECATION")
            val payload = characteristic.value ?: return
            handleIncomingChunk(gatt, payload)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (gatt !== activeGatt) return
            handleIncomingChunk(gatt, value)
        }
    }

    private fun handleIncomingChunk(gatt: BluetoothGatt, chunk: ByteArray) {
        if (gatt !== activeGatt) return
        try {
            val decodedFrames = engine.onNotificationChunkAll(chunk)
            if (decodedFrames.isEmpty()) return
            receivedValidBmsFrame = true
            wasConnectedOnce = true
            handshakeTimeoutJob?.cancel()
            for (decoded in decodedFrames) {
                when (decoded) {
                    is DecodedFrame.DeviceInfoFrame -> {
                        if (engine.snapshot.value.isUnlocked) {
                            lastCellRxMs = System.currentTimeMillis()
                            writeChar?.let { wChar ->
                                writeReadCommand(gatt, wChar, engine.nextReadCellInfoFrame())
                            }
                        }
                    }
                    is DecodedFrame.CellInfoFrame -> {
                        if (engine.snapshot.value.isUnlocked) {
                            lastCellRxMs = System.currentTimeMillis()
                        }
                    }
                    is DecodedFrame.SettingsFrame,
                    is DecodedFrame.UnsupportedFrame -> Unit
                }
            }
        } catch (t: Throwable) {
            failConnection("Incompatible telemetry stream: ${t.message ?: t::class.java.simpleName}")
        }
    }

    private fun beginHandshake(gatt: BluetoothGatt, wChar: BluetoothGattCharacteristic) {
        if (!writeReadCommand(gatt, wChar, engine.nextReadDeviceInfoFrame())) {
            failConnection("Failed to send JK BMS read handshake (0x97)")
            return
        }
        startHandshakeWatchdog()
        startPollLoop(gatt, wChar)
    }

    private fun startDiscoveryWatchdog(label: String) {
        discoveryTimeoutJob?.cancel()
        discoveryTimeoutJob = scope.launch {
            delay(12_000L)
            if (activeGatt != null && writeChar == null && !userInitiatedDisconnect) {
                failConnection("BLE discovery timed out on $label")
            }
        }
    }

    private fun startHandshakeWatchdog() {
        handshakeTimeoutJob?.cancel()
        handshakeTimeoutJob = scope.launch {
            delay(7_000L)
            if (!receivedValidBmsFrame && activeGatt != null && !userInitiatedDisconnect) {
                failConnection("No valid JK BMS response received — device is not a compatible JK BMS")
            }
        }
    }

    private fun startPollLoop(gatt: BluetoothGatt, wChar: BluetoothGattCharacteristic) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var requestedInitialCells = false
            while (isActive) {
                delay(1_000L)
                if (gatt !== activeGatt) break
                val snap = engine.snapshot.value
                if (!snap.isUnlocked) {
                    requestedInitialCells = false
                    continue
                }
                val now = System.currentTimeMillis()
                if (lastCellRxMs == 0L) {
                    lastCellRxMs = now
                }
                if (wasConnectedOnce && !userInitiatedDisconnect && now - lastCellRxMs > 10_000L) {
                    val addr = runCatching { gatt.device?.address?.uppercase() }.getOrNull().orEmpty()
                    val name = runCatching { gatt.device?.name }.getOrNull() ?: addr
                    emitConnectionLostOnce(addr, name, "Signal timeout")
                    break
                }
                synchronized(connectionLock) {
                    if (writeInFlight && now - lastWriteDispatchMs > 3_000L) {
                        writeInFlight = false
                    }
                }
                if (!requestedInitialCells) {
                    requestedInitialCells = true
                    writeReadCommand(gatt, wChar, engine.nextReadCellInfoFrame())
                } else if (now - lastCellRxMs > 1_500L) {
                    writeReadCommand(gatt, wChar, engine.nextReadCellInfoFrame())
                }
            }
        }
    }

    private fun writeReadCommand(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        command: ByteArray
    ): Boolean {
        synchronized(connectionLock) {
            if (gatt !== activeGatt || writeChar !== characteristic) return false
            if (writeInFlight) {
                if (writeQueue.size < 4) {
                    writeQueue.addLast(command)
                }
                return true
            }
            val started = performGattWrite(gatt, characteristic, command)
            if (started) {
                writeInFlight = true
                lastWriteDispatchMs = System.currentTimeMillis()
            }
            return started
        }
    }

    private fun performGattWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        command: ByteArray
    ): Boolean {
        return try {
            val writeType =
                if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt.writeCharacteristic(characteristic, command, writeType)
                res == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = writeType
                @Suppress("DEPRECATION")
                characteristic.value = command
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt.writeDescriptor(descriptor, value)
                res == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } catch (_: Throwable) {
            false
        }
    }
}
