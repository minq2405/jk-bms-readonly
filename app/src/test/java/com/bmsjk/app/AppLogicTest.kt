package com.bmsjk.app

import com.bmsjk.app.ble.JkBleFilter
import com.bmsjk.app.ble.MacPayloadParser
import com.bmsjk.app.data.AppLanguage
import com.bmsjk.app.ui.components.ChartAxisScaler
import com.bmsjk.app.ui.i18n.AppStrings
import com.bmsjk.core.protocol.JkFrameCodec
import com.bmsjk.core.session.BmsSessionEngine
import com.bmsjk.core.session.ChartMetric
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

    @Test
    fun chartAxisScalerPreventsNoiseAmplificationAndDownsamplesDenseSeries() {
        // 0.1C quantization jitter must use minimum 5.0C span instead of 0.1C barcode
        val tempBounds = ChartAxisScaler.computeBounds(listOf(33.9f, 34.0f, 33.9f, 34.0f), ChartMetric.MAX_TEMPERATURE)
        assertEquals(5.0f, tempBounds.span, 0.01f)
        assertTrue(tempBounds.min < 33.9f && tempBounds.max > 34.0f)

        // 1-2mV delta jitter must stay anchored at 0V..0.020V (20mV)
        val deltaBounds = ChartAxisScaler.computeBounds(listOf(2f, 3f, 4f, 3f), ChartMetric.DELTA_MV)
        assertEquals(0f, deltaBounds.min, 0.001f)
        assertEquals(20f, deltaBounds.max, 0.001f)
        assertEquals("0.020V", ChartAxisScaler.formatBound(deltaBounds.max, ChartMetric.DELTA_MV))

        // Downsampling keeps maxPoints and preserves the exact latest live sample at the tip
        val dense = List(300) { i -> 33.9f + (i % 2) * 0.1f } + 34.5f
        val downsampled = ChartAxisScaler.downsample(dense, maxPoints = 96)
        assertEquals(96, downsampled.size)
        assertEquals(34.5f, downsampled.last(), 0.001f)
    }
}
