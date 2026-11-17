package com.bmsjk.core.session

import com.bmsjk.core.protocol.BmsSettings
import com.bmsjk.core.protocol.BmsTelemetry
import com.bmsjk.core.protocol.DecodedFrame
import com.bmsjk.core.protocol.DeviceInfo
import com.bmsjk.core.protocol.FrameAssembleResult
import com.bmsjk.core.protocol.FrameAssembler
import com.bmsjk.core.protocol.JkFrameCodec
import com.bmsjk.core.protocol.JkFrameDecoder
import com.bmsjk.core.protocol.ProtocolMode
import com.bmsjk.core.protocol.ProtocolVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

data class BmsSessionSnapshot(
    val deviceAddress: String? = null,
    val protocolMode: ProtocolMode = ProtocolMode.AUTO,
    val detectedVariant: ProtocolVariant? = null,
    val passcodeStatus: PasscodeStatus = PasscodeStatus.PendingDeviceInfo,
    val deviceInfo: DeviceInfo? = null,
    val settings: BmsSettings? = null,
    val telemetry: BmsTelemetry? = null,
    val validFrameCount: Long = 0L,
    val crcErrorCount: Long = 0L,
    val chartRecordingPaused: Boolean = false,
    val chartRevision: Long = 0L
) {
    val isUnlocked: Boolean
        get() = passcodeStatus is PasscodeStatus.Unlocked
}

/**
 * Thread-safe pure Kotlin/JVM session engine shared by CLI tests and Android app.
 */
class BmsSessionEngine(
    passcodeStore: PasscodeStore = InMemoryPasscodeStore(),
    initialMode: ProtocolMode = ProtocolMode.AUTO,
    maxChartSamples: Int = 7_200
) {
    private val stateLock = Any()
    private val assembler = FrameAssembler()
    private val decoder = JkFrameDecoder(initialMode)
    private val passcodePolicy = PasscodePolicy(passcodeStore)
    private val seriesBuffer = SeriesBuffer(maxChartSamples)
    private val txCounter = AtomicInteger(0)

    private var pendingSettings: BmsSettings? = null
    private var lastRawDeviceInfoFrame: ByteArray? = null
    private var lastRawSettingsFrame: ByteArray? = null
    private var lastRawCellFrame: ByteArray? = null
    private var lastCellTimestampMs: Long = 0L

    private val _snapshot = MutableStateFlow(BmsSessionSnapshot(protocolMode = initialMode))
    val snapshot: StateFlow<BmsSessionSnapshot> = _snapshot.asStateFlow()

    fun startSession(deviceAddress: String) {
        synchronized(stateLock) {
            assembler.reset()
            decoder.resetAutoDetection()
            seriesBuffer.clear()
            txCounter.set(0)
            pendingSettings = null
            lastRawDeviceInfoFrame = null
            lastRawSettingsFrame = null
            lastRawCellFrame = null
            lastCellTimestampMs = 0L
            val normalizedAddr = deviceAddress.trim().takeIf { it.isNotEmpty() }
            _snapshot.value = BmsSessionSnapshot(
                deviceAddress = normalizedAddr,
                protocolMode = decoder.mode,
                passcodeStatus = PasscodeStatus.PendingDeviceInfo,
                chartRevision = _snapshot.value.chartRevision + 1L
            )
        }
    }

    fun clearSession() {
        synchronized(stateLock) {
            assembler.reset()
            decoder.resetAutoDetection()
            seriesBuffer.clear()
            txCounter.set(0)
            pendingSettings = null
            lastRawDeviceInfoFrame = null
            lastRawSettingsFrame = null
            lastRawCellFrame = null
            lastCellTimestampMs = 0L
            _snapshot.value = BmsSessionSnapshot(
                deviceAddress = null,
                protocolMode = decoder.mode,
                passcodeStatus = PasscodeStatus.PendingDeviceInfo,
                chartRevision = _snapshot.value.chartRevision + 1L
            )
        }
    }

    fun setProtocolMode(mode: ProtocolMode) {
        synchronized(stateLock) {
            decoder.mode = mode
            val current = _snapshot.value
            if (mode == ProtocolMode.AUTO) {
                lastRawDeviceInfoFrame?.let { decoder.decode(it) }
            }
            var updatedSettings = current.settings
            var updatedTelemetry = current.telemetry
            if (current.isUnlocked) {
                lastRawSettingsFrame?.let { raw ->
                    val decoded = decoder.decode(raw) as? DecodedFrame.SettingsFrame
                    if (decoded != null) updatedSettings = decoded.settings
                }
                lastRawCellFrame?.let { raw ->
                    val decoded = decoder.decode(raw, lastCellTimestampMs) as? DecodedFrame.CellInfoFrame
                    if (decoded != null) updatedTelemetry = decoded.telemetry
                }
            }
            _snapshot.value = current.copy(
                protocolMode = mode,
                detectedVariant = decoder.activeVariantOrNull(),
                settings = updatedSettings,
                telemetry = updatedTelemetry
            )
        }
    }

    fun setChartRecordingPaused(paused: Boolean) {
        synchronized(stateLock) {
            _snapshot.value = _snapshot.value.copy(chartRecordingPaused = paused)
        }
    }

    fun clearChartHistory() {
        synchronized(stateLock) {
            seriesBuffer.clear()
            _snapshot.value = _snapshot.value.copy(
                chartRevision = _snapshot.value.chartRevision + 1L
            )
        }
    }

    fun queryChart(window: ChartWindow, nowMs: Long = System.currentTimeMillis()): List<TrendSample> =
        seriesBuffer.query(window, nowMs)

    fun queryMetricStats(
        metric: ChartMetric,
        window: ChartWindow,
        nowMs: Long = System.currentTimeMillis()
    ): MetricStats = seriesBuffer.statsFor(metric, window, nowMs)

    fun nextReadDeviceInfoFrame(): ByteArray =
        JkFrameCodec.buildReadCommand(JkFrameCodec.ReadCommand.DEVICE_INFO, txCounter.getAndIncrement())

    fun nextReadCellInfoFrame(): ByteArray =
        JkFrameCodec.buildReadCommand(JkFrameCodec.ReadCommand.CELL_INFO, txCounter.getAndIncrement())

    fun submitPasscode(enteredPasscode: String): PasscodeStatus {
        synchronized(stateLock) {
            val current = _snapshot.value
            val address = current.deviceAddress ?: return PasscodeStatus.PendingDeviceInfo
            val info = current.deviceInfo ?: return PasscodeStatus.PendingDeviceInfo
            val status = passcodePolicy.submitPasscode(address, info, enteredPasscode)
            val unlockedSettings = if (status is PasscodeStatus.Unlocked) {
                (current.settings ?: pendingSettings).also { pendingSettings = null }
            } else {
                current.settings
            }
            _snapshot.value = current.copy(
                passcodeStatus = status,
                settings = unlockedSettings
            )
            return status
        }
    }

    fun forgetPasscodeForCurrentDevice() {
        synchronized(stateLock) {
            val address = _snapshot.value.deviceAddress ?: return
            passcodePolicy.forgetDevice(address)
        }
    }

    fun onNotificationChunk(
        chunk: ByteArray,
        timestampMs: Long = System.currentTimeMillis()
    ): DecodedFrame? = onNotificationChunkAll(chunk, timestampMs).lastOrNull()

    fun onNotificationChunkAll(
        chunk: ByteArray,
        timestampMs: Long = System.currentTimeMillis()
    ): List<DecodedFrame> {
        synchronized(stateLock) {
            val decodedList = ArrayList<DecodedFrame>(1)
            for (assembled in assembler.appendChunkAll(chunk)) {
                when (assembled) {
                    FrameAssembleResult.Incomplete,
                    FrameAssembleResult.IgnoredOutOfSync,
                    FrameAssembleResult.OverflowReset -> Unit

                    is FrameAssembleResult.CrcError -> {
                        _snapshot.value = _snapshot.value.copy(
                            crcErrorCount = _snapshot.value.crcErrorCount + 1L
                        )
                    }

                    is FrameAssembleResult.Assembled -> {
                        decodedList.add(onValidatedFrame(assembled.frame, timestampMs))
                    }
                }
            }
            return decodedList
        }
    }

    fun onValidatedFrame(
        frame: ByteArray,
        timestampMs: Long = System.currentTimeMillis()
    ): DecodedFrame {
        synchronized(stateLock) {
            val decoded = decoder.decode(frame, timestampMs)
            val current = _snapshot.value
            val address = current.deviceAddress

            when (decoded) {
                is DecodedFrame.DeviceInfoFrame -> {
                    lastRawDeviceInfoFrame = frame.copyOf()
                    val status = if (address != null) {
                        passcodePolicy.evaluateOnDeviceInfo(address, decoded.deviceInfo)
                    } else {
                        PasscodeStatus.PendingDeviceInfo
                    }
                    val unlockedSettings = if (status is PasscodeStatus.Unlocked) {
                        (current.settings ?: pendingSettings).also { pendingSettings = null }
                    } else {
                        current.settings
                    }
                    _snapshot.value = current.copy(
                        deviceInfo = decoded.deviceInfo,
                        detectedVariant = decoder.activeVariantOrNull(),
                        passcodeStatus = status,
                        settings = unlockedSettings,
                        validFrameCount = current.validFrameCount + 1L
                    )
                }

                is DecodedFrame.SettingsFrame -> {
                    lastRawSettingsFrame = frame.copyOf()
                    if (current.isUnlocked) {
                        pendingSettings = null
                        _snapshot.value = current.copy(
                            settings = decoded.settings,
                            detectedVariant = decoder.activeVariantOrNull(),
                            validFrameCount = current.validFrameCount + 1L
                        )
                    } else {
                        pendingSettings = decoded.settings
                    }
                }

                is DecodedFrame.CellInfoFrame -> {
                    lastRawCellFrame = frame.copyOf()
                    lastCellTimestampMs = timestampMs
                    if (current.isUnlocked) {
                        if (!current.chartRecordingPaused) {
                            seriesBuffer.append(decoded.telemetry)
                        }
                        _snapshot.value = current.copy(
                            telemetry = decoded.telemetry,
                            detectedVariant = decoder.activeVariantOrNull(),
                            validFrameCount = current.validFrameCount + 1L,
                            chartRevision = current.chartRevision + 1L
                        )
                    }
                }

                is DecodedFrame.UnsupportedFrame -> {
                    if (current.isUnlocked) {
                        _snapshot.value = current.copy(validFrameCount = current.validFrameCount + 1L)
                    }
                }
            }
            return decoded
        }
    }
}
