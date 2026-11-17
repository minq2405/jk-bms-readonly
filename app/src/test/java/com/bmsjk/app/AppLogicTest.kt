package com.bmsjk.app

import com.bmsjk.app.ble.JkBleFilter
import com.bmsjk.app.ble.MacPayloadParser
import com.bmsjk.app.data.AppLanguage
import com.bmsjk.app.ui.i18n.AppStrings
import com.bmsjk.core.protocol.JkFrameCodec
import com.bmsjk.core.session.BmsSessionEngine
import com.bmsjk.core.session.InMemoryPasscodeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AppLogicTest {

    @Test
    fun jkBleFilterAcceptsOnlyJkBmsDevicesAndRejectsUnrelatedPeripherals() {
        assertTrue(JkBleFilter.isJkBmsDevice("JK-BD6A24S6PD", emptyList()))
        assertTrue(JkBleFilter.isJkBmsDevice("JK_PB2A16S15P", emptyList()))
        assertTrue(JkBleFilter.isJkBmsDevice(null, listOf(JkFrameCodec.SERVICE_UUID)))
        assertTrue(JkBleFilter.isJkBmsDevice("CustomName", listOf(JkFrameCodec.SERVICE_UUID)))

        assertFalse(JkBleFilter.isJkBmsDevice(null, emptyList()))
        assertFalse(JkBleFilter.isJkBmsDevice("(Unknown BLE)", emptyList()))
        assertFalse(JkBleFilter.isJkBmsDevice("Mi Band 7", emptyList()))
        assertFalse(JkBleFilter.isJkBmsDevice("AirPods Pro", listOf(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))))
    }

    @Test
    fun macPayloadParserExtractsValidAddressesFromVariousFormats() {
        assertEquals("C8:47:8C:11:22:33", MacPayloadParser.extractMac("c8:47:8c:11:22:33"))
        assertEquals("C8:47:8C:11:22:33", MacPayloadParser.extractMac("jkbms://connect?mac=c8-47-8c-11-22-33"))
        assertEquals("C8:47:8C:11:22:33", MacPayloadParser.extractMac("C8478C112233"))
        assertNull(MacPayloadParser.extractMac("not-a-mac-address"))
    }

    @Test
    fun dynamicCellLabelsAdaptFromNToActualCellCount() {
        val vi = AppStrings.forLang(AppLanguage.VI)
        val en = AppStrings.forLang(AppLanguage.EN)

        assertEquals("N Cell", vi.cellsTabLabel(null))
        assertEquals("N Cell", vi.cellsTabLabel(0))
        assertEquals("16 Cell", vi.cellsTabLabel(16))
        assertEquals("24 Cell", vi.cellsTabLabel(24))
        assertEquals("Điện áp N Cell", vi.cellVoltagesTitle(null))
        assertEquals("Điện áp 16 Cell", vi.cellVoltagesTitle(16))

        assertEquals("N Cells", en.cellsTabLabel(null))
        assertEquals("16 Cells", en.cellsTabLabel(16))
        assertEquals("N-Cell Voltages", en.cellVoltagesTitle(null))
        assertEquals("24-Cell Voltages", en.cellVoltagesTitle(24))
    }

    @Test
    fun garbageDataFromNonJkDeviceDoesNotThrowInSessionEngine() {
        val engine = BmsSessionEngine(passcodeStore = InMemoryPasscodeStore())
        engine.startSession("AA:BB:CC:DD:EE:FF")

        // Random bytes without 55 AA EB 90 header
        assertNull(engine.onNotificationChunk(byteArrayOf(0x12, 0x34, 0x56, 0x78)))

        // Header followed by corrupted 300-byte payload (bad CRC)
        val corrupted = ByteArray(300)
        corrupted[0] = 0x55
        corrupted[1] = 0xAA.toByte()
        corrupted[2] = 0xEB.toByte()
        corrupted[3] = 0x90.toByte()
        corrupted[4] = 0x02
        corrupted[299] = 0xFF.toByte() // wrong CRC
        assertNull(engine.onNotificationChunk(corrupted))
        assertEquals(1L, engine.snapshot.value.crcErrorCount)
    }
}
