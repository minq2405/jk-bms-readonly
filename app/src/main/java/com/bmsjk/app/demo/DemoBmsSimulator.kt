package com.bmsjk.app.demo

import com.bmsjk.core.protocol.JkFrameCodec
import com.bmsjk.core.session.BmsSessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class DemoScenario {
    IDLE,
    RIDING,
    CHARGING
}

/**
 * Offline 24S simulator feeding real 300-byte JK02_32S frames through [BmsSessionEngine]
 * so the UI, passcode policy, charts, and split-screen layouts can be tested without hardware.
 */
class DemoBmsSimulator(
    private val engine: BmsSessionEngine,
    private val scope: CoroutineScope
) {
    private var job: Job? = null
    private var tick = 0
    var scenario: DemoScenario = DemoScenario.IDLE
        private set

    fun start(mac: String = DEMO_MAC, initialScenario: DemoScenario = DemoScenario.IDLE) {
        stop()
        scenario = initialScenario
        tick = 0
        engine.startSession(mac)
        feedFrameChunks(buildDeviceInfoFrame())
        feedFrameChunks(buildSettingsFrame())
        if (engine.snapshot.value.isUnlocked) {
            feedFrameChunks(buildCellFrame(scenario, tick))
        }
        job = scope.launch {
            while (isActive) {
                delay(1_200L)
                if (engine.snapshot.value.isUnlocked) {
                    tick++
                    feedFrameChunks(buildCellFrame(scenario, tick))
                }
            }
        }
    }

    fun setScenario(next: DemoScenario) {
        scenario = next
        if (engine.snapshot.value.isUnlocked) {
            feedFrameChunks(buildCellFrame(scenario, tick))
        }
    }

    fun emitSettingsAndCurrentCellsIfUnlocked() {
        if (engine.snapshot.value.isUnlocked) {
            feedFrameChunks(buildSettingsFrame())
            feedFrameChunks(buildCellFrame(scenario, tick))
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun feedFrameChunks(frame: ByteArray) {
        var offset = 0
        while (offset < frame.size) {
            val end = minOf(offset + 20, frame.size)
            engine.onNotificationChunk(frame.copyOfRange(offset, end))
            offset = end
        }
    }

    private fun buildDeviceInfoFrame(): ByteArray {
        val f = ByteArray(JkFrameCodec.FRAME_SIZE)
        setResponseHeader(f, 0x03)
        writeAscii(f, 6, 16, "JK-BD6A24S6PD")
        writeAscii(f, 22, 8, "20H")
        writeAscii(f, 30, 8, "20.25")
        writeU32LE(f, 38, 128_400L)
        writeU32LE(f, 42, 4L)
        writeAscii(f, 46, 16, "JK-BD6A24S6PD")
        writeAscii(f, 62, 16, "1234")
        writeAscii(f, 78, 8, "260507")
        writeAscii(f, 86, 11, "60126356601")
        writeAscii(f, 102, 16, "88888888")
        writeAscii(f, 118, 16, "123456")
        f[299] = JkFrameCodec.crc8(f, 299)
        return f
    }

    private fun buildSettingsFrame(): ByteArray {
        val f = ByteArray(JkFrameCodec.FRAME_SIZE)
        setResponseHeader(f, 0x01)
        // JK02_32S offsets
        writeU32LE(f, 6, 2_800)    // smartSleepVoltageV
        writeU32LE(f, 10, 2_910)   // cellUvpV
        writeU32LE(f, 14, 2_980)   // cellUvprV
        writeU32LE(f, 18, 3_500)   // cellOvpV
        writeU32LE(f, 22, 3_450)   // cellOvprV
        writeU32LE(f, 26, 3)       // balanceTriggerVoltageV (0.003V)
        writeU32LE(f, 30, 3_480)   // soc100VoltageV
        writeU32LE(f, 34, 2_950)   // soc0VoltageV
        writeU32LE(f, 46, 2_850)   // powerOffVoltageV
        writeU32LE(f, 50, 60_000)  // maxChargeCurrentA (60A)
        writeU32LE(f, 54, 60)      // chargeOcpDelayS
        writeU32LE(f, 58, 60)      // chargeOcpRecoveryS
        writeU32LE(f, 62, 60_000)  // maxDischargeCurrentA (60A)
        writeU32LE(f, 66, 30)      // dischargeOcpDelayS
        writeU32LE(f, 70, 60)      // dischargeOcpRecoveryS
        writeU32LE(f, 74, 30)      // scpRecoveryS
        writeU32LE(f, 78, 600)     // maxBalanceCurrentA (0.60A)
        writeU32LE(f, 82, 700)     // chargeOtpC (70.0C)
        writeU32LE(f, 86, 600)     // chargeOtpRecoveryC
        writeU32LE(f, 90, 700)     // dischargeOtpC (70.0C)
        writeU32LE(f, 94, 600)     // dischargeOtpRecoveryC
        writeI32LE(f, 98, -100)    // chargeUtpC (-10.0C)
        writeI32LE(f, 102, 0)      // chargeUtpRecoveryC
        writeI32LE(f, 106, 800)    // mosfetOtpC (80.0C)
        writeI32LE(f, 110, 700)    // mosfetOtpRecoveryC
        f[114] = 24                // cellCount = 24S
        f[118] = 1                 // chargeSwitchOn
        f[122] = 1                 // dischargeSwitchOn
        f[126] = 1                 // balancerSwitchOn
        writeU32LE(f, 130, 45_000) // nominalCapacityAh (45.0Ah)
        writeU32LE(f, 134, 5)      // scpDelayUs
        writeU32LE(f, 138, 3_000)  // startBalanceVoltageV
        f[299] = JkFrameCodec.crc8(f, 299)
        return f
    }

    private fun buildCellFrame(sc: DemoScenario, step: Int): ByteArray {
        val f = ByteArray(JkFrameCodec.FRAME_SIZE)
        setResponseHeader(f, 0x02)
        // JK02_32S: cellOffset = 16, tailOffset = 32
        val baseCellsMv = when (sc) {
            DemoScenario.IDLE -> intArrayOf(
                3339, 3339, 3339, 3339, 3339, 3336, 3336, 3336,
                3336, 3336, 3336, 3336, 3336, 3336, 3336, 3336,
                3336, 3336, 3336, 3336, 3336, 3336, 3336, 3336
            )
            DemoScenario.RIDING -> intArrayOf(
                3258, 3257, 3259, 3255, 3256, 3251, 3254, 3256,
                3255, 3254, 3257, 3253, 3255, 3256, 3252, 3255,
                3256, 3258, 3255, 3254, 3255, 3256, 3254, 3255
            )
            DemoScenario.CHARGING -> intArrayOf(
                3442, 3438, 3436, 3435, 3437, 3429, 3434, 3435,
                3436, 3435, 3434, 3435, 3436, 3433, 3435, 3434,
                3436, 3435, 3437, 3434, 3435, 3434, 3435, 3435
            )
        }
        val wave = if (sc == DemoScenario.IDLE) 0f else (sin(step * 0.45) * 0.8 + cos(step * 0.2) * 0.3).toFloat()
        var sumMv = 0
        for (i in 0 until 24) {
            val mv = (baseCellsMv[i] - (wave * 2).roundToInt()).coerceIn(2500, 3700)
            sumMv += mv
            writeU16LE(f, 6 + i * 2, mv)
            writeU16LE(f, 80 + i * 2, 380 + (i % 7) * 5) // ~0.380 - 0.410 Ohm
        }
        // Enabled cells mask (24S = 0x00FFFFFF) at 54 + 16 = 70
        writeU32LE(f, 70, 0x00FF_FFFFL)

        // MOS temp at 112 + 32 = 144 (deci-C)
        val mosTempDeci = when (sc) {
            DemoScenario.IDLE -> 344
            DemoScenario.RIDING -> 412
            DemoScenario.CHARGING -> 390
        }
        writeI16LE(f, 144, mosTempDeci)

        // Pack voltage at 118 + 32 = 150 (mV)
        writeU32LE(f, 150, sumMv.toLong())

        // Current at 126 + 32 = 158 (mA signed)
        val currentMa = when (sc) {
            DemoScenario.IDLE -> 0
            DemoScenario.RIDING -> ((-18.5f + wave) * 1000f).roundToInt()
            DemoScenario.CHARGING -> ((15.0f + wave * 0.5f) * 1000f).roundToInt()
        }
        writeI32LE(f, 158, currentMa)

        // Disconnected probes T1..T4 = -200.0C (-2000) to match real JK-BD6A24S6PD
        writeI16LE(f, 162, -2000) // T1 at 130+32
        writeI16LE(f, 164, -2000) // T2 at 132+32
        writeI16LE(f, 258, -2000) // T3 at 226+32
        writeI16LE(f, 256, -2000) // T4 at 224+32
        val t5Deci = when (sc) {
            DemoScenario.IDLE -> 344
            DemoScenario.RIDING -> 378
            DemoScenario.CHARGING -> 361
        }
        writeI16LE(f, 254, t5Deci) // T5 at 222+32

        // Balance current at 138+32 = 170, action at 140+32 = 172
        val balMa = if (sc == DemoScenario.RIDING) 0 else 581
        writeI16LE(f, 170, balMa)
        f[172] = if (sc == DemoScenario.CHARGING) 1 else if (sc == DemoScenario.IDLE) 2 else 0

        // SOC at 141+32 = 173, remAh at 174, nomAh at 178, cycles at 182, SOH at 190
        val soc = when (sc) {
            DemoScenario.IDLE -> 87
            DemoScenario.RIDING -> 74
            DemoScenario.CHARGING -> 92
        }
        f[173] = soc.toByte()
        writeU32LE(f, 174, (45_000L * soc) / 100L)
        writeU32LE(f, 178, 45_000L)
        writeU32LE(f, 182, 42L)
        f[190] = 91 // SOH 91%

        // Runtime at 162+32 = 194, MOS switches at 198, 199
        writeU32LE(f, 194, 342_500L + step)
        f[198] = 1
        f[199] = 1
        // Charge status at 248+32 = 280
        f[280] = if (sc == DemoScenario.CHARGING) 0 else 2

        f[299] = JkFrameCodec.crc8(f, 299)
        return f
    }

    private fun setResponseHeader(f: ByteArray, type: Int) {
        f[0] = 0x55
        f[1] = 0xAA.toByte()
        f[2] = 0xEB.toByte()
        f[3] = 0x90.toByte()
        f[4] = type.toByte()
    }

    private fun writeAscii(f: ByteArray, offset: Int, len: Int, text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        val count = minOf(len, bytes.size)
        for (i in 0 until count) {
            f[offset + i] = bytes[i]
        }
    }

    private fun writeU16LE(f: ByteArray, offset: Int, v: Int) {
        f[offset] = (v and 0xFF).toByte()
        f[offset + 1] = ((v ushr 8) and 0xFF).toByte()
    }

    private fun writeI16LE(f: ByteArray, offset: Int, v: Int) = writeU16LE(f, offset, v)

    private fun writeU32LE(f: ByteArray, offset: Int, v: Long) {
        f[offset] = (v and 0xFF).toByte()
        f[offset + 1] = ((v ushr 8) and 0xFF).toByte()
        f[offset + 2] = ((v ushr 16) and 0xFF).toByte()
        f[offset + 3] = ((v ushr 24) and 0xFF).toByte()
    }

    private fun writeI32LE(f: ByteArray, offset: Int, v: Int) = writeU32LE(f, offset, v.toLong())

    companion object {
        const val DEMO_MAC = "C8:47:8C:DE:MO:24"
    }
}
