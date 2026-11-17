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
    val chartRecordingPaused: Boolean = false
) {
    val isUnlocked: Boolean
        get() = passcodeStatus is PasscodeStatus.Unlocked
}

/**
 * Pure Kotlin/JVM session engine shared by CLI tests and Android app.
 */
class BmsSessionEngine(
    passcodeStore: PasscodeStore = InMemoryPasscodeStore(),
    initialMode: ProtocolMode = ProtocolMode.AUTO,
    maxChartSamples: Int = 7_200
) {
    private val assembler = FrameAssembler()
    private val decoder = JkFrameDecoder(initialMode)
    private val passcodePolicy = PasscodePolicy(passcodeStore)
    private val seriesBuffer = SeriesBuffer(maxChartSamples)
    private var txCounter = 0

    private val _snapshot = MutableStateFlow(BmsSessionSnapshot(protocolMode = initialMode))
    val snapshot: StateFlow<BmsSessionSnapshot> = _snapshot.asStateFlow()

    fun startSession(deviceAddress: String) {
        assembler.reset()
        decoder.resetAutoDetection()
        txCounter = 0
        _snapshot.value = BmsSessionSnapshot(
            deviceAddress = deviceAddress,
            protocolMode = decoder.mode,
            passcodeStatus = PasscodeStatus.PendingDeviceInfo
        )
    }

    fun setProtocolMode(mode: ProtocolMode) {
        decoder.mode = mode
        _snapshot.value = _snapshot.value.copy(
            protocolMode = mode,
            detectedVariant = decoder.activeVariantOrNull()
        )
    }

    fun setChartRecordingPaused(paused: Boolean) {
        _snapshot.value = _snapshot.value.copy(chartRecordingPaused = paused)
    }

    fun clearChartHistory() {
        seriesBuffer.clear()
    }

    fun queryChart(window: ChartWindow, nowMs: Long = System.currentTimeMillis()): List<TrendSample> =
        seriesBuffer.query(window, nowMs)

    fun queryMetricStats(
        metric: ChartMetric,
        window: ChartWindow,
        nowMs: Long = System.currentTimeMillis()
    ): MetricStats = seriesBuffer.statsFor(metric, window, nowMs)

    fun nextReadDeviceInfoFrame(): ByteArray =
        JkFrameCodec.buildReadCommand(JkFrameCodec.ReadCommand.DEVICE_INFO, txCounter++)

    fun nextReadCellInfoFrame(): ByteArray =
        JkFrameCodec.buildReadCommand(JkFrameCodec.ReadCommand.CELL_INFO, txCounter++)

    fun submitPasscode(enteredPasscode: String): PasscodeStatus {
        val current = _snapshot.value
        val address = current.deviceAddress ?: return PasscodeStatus.PendingDeviceInfo
        val info = current.deviceInfo ?: return PasscodeStatus.PendingDeviceInfo
        val status = passcodePolicy.submitPasscode(address, info, enteredPasscode)
        _snapshot.value = current.copy(passcodeStatus = status)
        return status
    }

    fun forgetPasscodeForCurrentDevice() {
        val address = _snapshot.value.deviceAddress ?: return
        passcodePolicy.forgetDevice(address)
    }

    fun onNotificationChunk(
        chunk: ByteArray,
        timestampMs: Long = System.currentTimeMillis()
    ): DecodedFrame? {
        return when (val assembled = assembler.appendChunk(chunk)) {
            FrameAssembleResult.Incomplete,
            FrameAssembleResult.IgnoredOutOfSync,
            FrameAssembleResult.OverflowReset -> null

            is FrameAssembleResult.CrcError -> {
                _snapshot.value = _snapshot.value.copy(
                    crcErrorCount = _snapshot.value.crcErrorCount + 1
                )
                null
            }

            is FrameAssembleResult.Assembled -> onValidatedFrame(assembled.frame, timestampMs)
        }
    }

    fun onValidatedFrame(
        frame: ByteArray,
        timestampMs: Long = System.currentTimeMillis()
    ): DecodedFrame {
        val decoded = decoder.decode(frame, timestampMs)
        val current = _snapshot.value
        val address = current.deviceAddress ?: "UNKNOWN"

        when (decoded) {
            is DecodedFrame.DeviceInfoFrame -> {
                val status = passcodePolicy.evaluateOnDeviceInfo(address, decoded.deviceInfo)
                _snapshot.value = current.copy(
                    deviceInfo = decoded.deviceInfo,
                    detectedVariant = decoder.activeVariantOrNull(),
                    passcodeStatus = status,
                    validFrameCount = current.validFrameCount + 1
                )
            }

            is DecodedFrame.SettingsFrame -> {
                if (current.isUnlocked) {
                    _snapshot.value = current.copy(
                        settings = decoded.settings,
                        detectedVariant = decoder.activeVariantOrNull(),
                        validFrameCount = current.validFrameCount + 1
                    )
                }
            }

            is DecodedFrame.CellInfoFrame -> {
                if (current.isUnlocked) {
                    if (!current.chartRecordingPaused) {
                        seriesBuffer.append(decoded.telemetry)
                    }
                    _snapshot.value = current.copy(
                        telemetry = decoded.telemetry,
                        detectedVariant = decoder.activeVariantOrNull(),
                        validFrameCount = current.validFrameCount + 1
                    )
                }
            }

            is DecodedFrame.UnsupportedFrame -> {
                _snapshot.value = current.copy(validFrameCount = current.validFrameCount + 1)
            }
        }
        return decoded
    }
}
