package com.bmsjk.core.protocol

import com.bmsjk.core.session.BmsSessionEngine
import com.bmsjk.core.session.ChartMetric
import com.bmsjk.core.session.ChartWindow
import com.bmsjk.core.session.InMemoryPasscodeStore
import com.bmsjk.core.session.PasscodeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JkProtocolTest {

    // Real JK02_24S captures from syssi/esphome-jk-bms (components/jk_bms_ble/jk_bms_ble.cpp)
    private val jk02_24sDeviceInfoHex =
        "55AAEB90039F4A4B2D4232413234533135500000000031302E585700000031302E303700000040AF010006000000" +
            "4A4B2D42324132345331355000000000313233340000000000000000000000003232303430370000323032313630" +
            "32303936003030303000496E70757420557365726461746100003132333435360000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000065"

    private val jk02_24sSettingsHex =
        "55AAEB90014F58020000540B0000800C0000CC100000681000000A00000000000000000000000000000000000000" +
            "F00A0000A86100001E0000003C000000F04902002C0100003C0000003C000000D0070000BC02000058020000BC02" +
            "00005802000038FFFFFF9CFFFFFF84030000BC0200000D00000001000000010000000100000088130000DC050000" +
            "E40C0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000040"

    private val jk02_24sCellInfoHex =
        "55AAEB90028CFF0C010D010DFF0C010D010DFF0C010D010D010D010DFF0C010D010D010D010D0000000000000000" +
            "0000000000000000FFFF0000000D000000009D0196018C0187018401840183018401850181018301860182018201" +
            "830185010000000000000000000000000000000000000000000003D000000000000000000000BE00BF00D2000000" +
            "000000548E0B0100683C0100000000003D04000064007904CA0310000101AA060000000000000000000000000700" +
            "01000000D50200000000AED63B400000000058AAFDFF0000000100020000ECE64F00000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000CD"

    // Real JK02_32S Device Info (0x03) from syssi/esphome-jk-bms
    private val jk02_32sDeviceInfoHex =
        "55AAEB9003C94A4B5F5042324131365331355000000031342E584100000031342E323000000054E601009C000000" +
            "4A4B5F50423241313653313550000000313233340000000000000000000000003233313131380000333039323537" +
            "32313334003030303000496E707574205573657264617461000031323334353700000000000000000000496E7075" +
            "742055736572646174610000FEFFFFFFAFE9010200000000901F00000000C0D8E7FE1F0000010000000000000000" +
            "0104CF030000000000000000000000000000DF07000000000000000000000000000001CF03000000000000000000" +
            "00000000000B00010000000000000000090000000B0000000000000000000000805100000A500100000000000000" +
            "0000000000000000000000FE9FE9FE03000000000000001B"

    // Real JK04 Cell Info (0x02) & Device Info (0x03) from syssi/esphome-jk-bms
    private val jk04DeviceInfoHex =
        "55AAEB9003E74A4B2D42324131365300000000000000332E300000000000332E332E30000000108E320213000000" +
            "424D5300000000000000000000000000313233340000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
            "0000000000000000000000000000000000000000000000A9"

    private val jk04CellInfoHex =
        "55AAEB90024BC06156401FAA5640FF915640FF9156401FAA5640FF915640FF915640FF9156401FAA5640FF915640" +
            "FF915640FF915640FF9156401FAA5640E0795640E079564000000000000000000000000000000000000000000000" +
            "000000000000000000007C1D233E1BEB083E56CE143E4D9B153EE0DBCD3D7233CD3D9488013E5E1EEA3DE517CD3D" +
            "E3BBD73DF544D23DBE7C013E27B6003EDAB5FC3D6B51F83DA293F33D000000000000000000000000000000000000" +
            "000000000000000000000000000000000000000000000395564000BE903B00000000FFFF00000100000100000000" +
            "0000000000000066A0D24A4000000000000000000000000000000000000000000000000101000000000000000000" +
            "0000000000000000000053961C0000000000004822400013"

    @Test
    fun readOnlyGuardOnlyAllowsQueryCommands0x96And0x97() {
        val codes = JkFrameCodec.ReadCommand.entries.map { it.code }.toSet()
        assertEquals(setOf(0x96, 0x97), codes)

        val devCmd = JkFrameCodec.buildReadCommand(JkFrameCodec.ReadCommand.DEVICE_INFO, 1)
        assertEquals(20, devCmd.size)
        assertEquals(0x97, devCmd[4].toInt() and 0xFF)
        assertEquals(JkFrameCodec.crc8(devCmd, 19), devCmd[19])
    }

    @Test
    fun chunkReassemblyAndCrcValidationWorkAcrossMtuSplits() {
        val raw = JkFrameCodec.parseHexString(jk02_24sCellInfoHex)
        val assembler = FrameAssembler()

        // Feed out-of-sync tail first -> ignored
        assertEquals(FrameAssembleResult.IgnoredOutOfSync, assembler.appendChunk(byteArrayOf(1, 2, 3)))

        // Split 300-byte frame into 20-byte BLE MTU chunks
        var assembled: ByteArray? = null
        for (chunk in raw.toList().chunked(20)) {
            when (val res = assembler.appendChunk(chunk.toByteArray())) {
                is FrameAssembleResult.Assembled -> assembled = res.frame
                FrameAssembleResult.Incomplete -> Unit
                else -> error("Unexpected assembler state: $res")
            }
        }
        assertNotNull(assembled)
        assertTrue(assembled!!.contentEquals(raw))

        // Corrupt 1 byte -> must fail CRC
        val corrupted = raw.copyOf()
        corrupted[10] = (corrupted[10].toInt() xor 0xFF).toByte()
        var crcErr: FrameAssembleResult? = null
        for (chunk in corrupted.toList().chunked(32)) {
            val res = assembler.appendChunk(chunk.toByteArray())
            if (res is FrameAssembleResult.CrcError) crcErr = res
        }
        assertTrue(crcErr is FrameAssembleResult.CrcError)
    }

    @Test
    fun autoDetectIdentifiesAllThreeProtocolVariantsCorrectly() {
        val decoder = JkFrameDecoder(ProtocolMode.AUTO)

        val d24 = decoder.decode(JkFrameCodec.parseHexString(jk02_24sDeviceInfoHex)) as DecodedFrame.DeviceInfoFrame
        assertEquals("JK-B2A24S15P", d24.deviceInfo.vendorModel)
        assertEquals("10.XW", d24.deviceInfo.hardwareVersion)
        assertEquals("1234", d24.deviceInfo.devicePasscode)
        assertEquals("20220407", d24.deviceInfo.manufacturingDate)
        assertEquals("2021602096", d24.deviceInfo.serialNumber)
        assertEquals(ProtocolVariant.JK02_24S, decoder.detectedVariant)

        decoder.resetAutoDetection()
        val d32 = decoder.decode(JkFrameCodec.parseHexString(jk02_32sDeviceInfoHex)) as DecodedFrame.DeviceInfoFrame
        assertEquals("JK_PB2A16S15P", d32.deviceInfo.vendorModel)
        assertEquals("14.XA", d32.deviceInfo.hardwareVersion)
        assertEquals(ProtocolVariant.JK02_32S, decoder.detectedVariant)

        decoder.resetAutoDetection()
        val d04 = decoder.decode(JkFrameCodec.parseHexString(jk04DeviceInfoHex)) as DecodedFrame.DeviceInfoFrame
        assertEquals("JK-B2A16S", d04.deviceInfo.vendorModel)
        assertEquals("3.0", d04.deviceInfo.hardwareVersion)
        assertEquals(ProtocolVariant.JK04, decoder.detectedVariant)
    }

    @Test
    fun decodesRealJk02_24sCellInfoAndSettingsAccurately() {
        val decoder = JkFrameDecoder(ProtocolMode.AUTO)
        decoder.decode(JkFrameCodec.parseHexString(jk02_24sDeviceInfoHex))

        val settings = (decoder.decode(JkFrameCodec.parseHexString(jk02_24sSettingsHex)) as DecodedFrame.SettingsFrame).settings
        assertEquals(ProtocolVariant.JK02_24S, settings.protocol)
        assertEquals(13, settings.cellCount)
        assertEquals(5.0f, settings.nominalCapacityAh!!, 0.01f)
        assertEquals(2.900f, settings.cellUvpV!!, 0.001f)
        assertEquals(4.300f, settings.cellOvpV!!, 0.001f)
        assertEquals(25.0f, settings.maxChargeCurrentA!!, 0.01f)
        assertEquals(150.0f, settings.maxDischargeCurrentA!!, 0.01f)
        assertEquals(-20.0f, settings.chargeUtpC!!, 0.1f)
        assertTrue(settings.chargeSwitchOn == true)

        val cellInfo = (decoder.decode(JkFrameCodec.parseHexString(jk02_24sCellInfoHex)) as DecodedFrame.CellInfoFrame).telemetry
        assertEquals(ProtocolVariant.JK02_24S, cellInfo.protocol)
        assertEquals(16, cellInfo.enabledCellCount)
        assertEquals(3.327f, cellInfo.cells[0].voltageV, 0.001f)
        assertEquals(3.329f, cellInfo.cells[1].voltageV, 0.001f)
        assertEquals(53.251f, cellInfo.totalVoltageV, 0.001f)
        assertEquals(84, cellInfo.socPercent)
        assertEquals(100, cellInfo.sohPercent)
        assertEquals(19.0f, cellInfo.temp1C!!, 0.1f)
        assertEquals(19.1f, cellInfo.temp2C!!, 0.1f)
        assertEquals(21.0f, cellInfo.mosfetTempC!!, 0.1f)
        assertEquals(68.494f, cellInfo.remainingCapacityAh!!, 0.001f)
        assertEquals(81.000f, cellInfo.nominalCapacityAh!!, 0.001f)
        assertTrue(cellInfo.chargingMosEnabled == true)
        assertTrue(cellInfo.dischargingMosEnabled == true)
    }

    @Test
    fun decodesRealJk04CellInfoAccurately() {
        val decoder = JkFrameDecoder(ProtocolMode.AUTO)
        decoder.decode(JkFrameCodec.parseHexString(jk04DeviceInfoHex))
        val t = (decoder.decode(JkFrameCodec.parseHexString(jk04CellInfoHex)) as DecodedFrame.CellInfoFrame).telemetry
        assertEquals(ProtocolVariant.JK04, t.protocol)
        assertEquals(16, t.enabledCellCount)
        assertTrue(t.cells[0].voltageV in 3.30f..3.40f)
        assertEquals(1873491L, t.totalRuntimeSeconds)
    }

    @Test
    fun decodesConstructed24CellJk02_32sFrameAndAutoDetectsWithoutDeviceInfo() {
        val frame = ByteArray(JkFrameCodec.FRAME_SIZE)
        frame[0] = 0x55
        frame[1] = 0xAA.toByte()
        frame[2] = 0xEB.toByte()
        frame[3] = 0x90.toByte()
        frame[4] = 0x02

        // 24 cells at 3.350V (0x0D16)
        for (i in 0 until 24) {
            frame[6 + i * 2] = 0x16
            frame[6 + i * 2 + 1] = 0x0D
        }
        // Enabled cells mask (24 cells = 0x00FFFFFF) at offset 70..73
        frame[70] = 0xFF.toByte()
        frame[71] = 0xFF.toByte()
        frame[72] = 0xFF.toByte()
        frame[73] = 0x00

        // MOS temp at 144..145 = 35.0C (350 = 0x015E)
        frame[144] = 0x5E
        frame[145] = 0x01

        // Pack voltage at 150..153 = 80.400V (80400 = 0x00013A10)
        frame[150] = 0x10
        frame[151] = 0x3A
        frame[152] = 0x01
        frame[153] = 0x00

        // Current at 158..161 = -12.500A (-12500 = 0xFFFFCF2C)
        frame[158] = 0x2C
        frame[159] = 0xCF.toByte()
        frame[160] = 0xFF.toByte()
        frame[161] = 0xFF.toByte()

        // SOC at 173 = 76%
        frame[173] = 76

        // Charge status at 280 = 2 (Float)
        frame[280] = 2

        frame[299] = JkFrameCodec.crc8(frame, 299)

        val decoder = JkFrameDecoder(ProtocolMode.AUTO)
        val telemetry = (decoder.decode(frame) as DecodedFrame.CellInfoFrame).telemetry
        assertEquals(ProtocolVariant.JK02_32S, telemetry.protocol)
        assertEquals(24, telemetry.enabledCellCount)
        assertEquals(80.400f, telemetry.totalVoltageV, 0.001f)
        assertEquals(-12.500f, telemetry.currentA!!, 0.001f)
        assertEquals(-1005.0f, telemetry.powerW!!, 0.5f)
        assertEquals(35.0f, telemetry.mosfetTempC!!, 0.1f)
        assertEquals(76, telemetry.socPercent)
        assertEquals(ChargeStatus.FLOAT, telemetry.chargeStatus)
    }

    @Test
    fun passcodeRequiredOnFirstConnectAndAutoUnlockedAfterward() {
        val store = InMemoryPasscodeStore()
        val mac = "C8:47:8C:11:22:33"
        val devInfo = JkFrameCodec.parseHexString(jk02_24sDeviceInfoHex)
        val cellInfo = JkFrameCodec.parseHexString(jk02_24sCellInfoHex)

        // First connection: requires passcode before telemetry is accepted
        val firstSession = BmsSessionEngine(passcodeStore = store)
        firstSession.startSession(mac)
        firstSession.onValidatedFrame(devInfo)
        assertTrue(firstSession.snapshot.value.passcodeStatus is PasscodeStatus.Required)
        firstSession.onValidatedFrame(cellInfo)
        assertNull(firstSession.snapshot.value.telemetry)

        // Wrong passcode
        assertTrue(firstSession.submitPasscode("0000") is PasscodeStatus.Mismatch)
        // Correct passcode ("1234")
        val unlocked = firstSession.submitPasscode("1234")
        assertTrue(unlocked is PasscodeStatus.Unlocked && !unlocked.autoUnlocked)

        firstSession.onValidatedFrame(cellInfo, timestampMs = 1_000L)
        assertNotNull(firstSession.snapshot.value.telemetry)
        assertEquals(1, firstSession.queryChart(ChartWindow.FULL_SESSION, nowMs = 1_000L).size)
        val stats = firstSession.queryMetricStats(ChartMetric.PACK_VOLTAGE, ChartWindow.FULL_SESSION, nowMs = 1_000L)
        assertEquals(53.251f, stats.current!!, 0.001f)

        // Second connection to same MAC: automatically unlocked without prompt
        val secondSession = BmsSessionEngine(passcodeStore = store)
        secondSession.startSession(mac)
        secondSession.onValidatedFrame(devInfo)
        val auto = secondSession.snapshot.value.passcodeStatus
        assertTrue(auto is PasscodeStatus.Unlocked && auto.autoUnlocked)
        secondSession.onValidatedFrame(cellInfo, timestampMs = 2_000L)
        assertNotNull(secondSession.snapshot.value.telemetry)
    }
}
