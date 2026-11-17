package com.bmsjk.app

import com.bmsjk.app.ble.MacPayloadParser
import com.bmsjk.app.demo.DemoBmsSimulator
import com.bmsjk.app.demo.DemoScenario
import com.bmsjk.core.session.BmsSessionEngine
import com.bmsjk.core.session.InMemoryPasscodeStore
import com.bmsjk.core.session.PasscodeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogicTest {

    @Test
    fun macPayloadParserExtractsValidAddressesFromVariousFormats() {
        assertEquals("C8:47:8C:11:22:33", MacPayloadParser.extractMac("c8:47:8c:11:22:33"))
        assertEquals("C8:47:8C:11:22:33", MacPayloadParser.extractMac("jkbms://connect?mac=c8-47-8c-11-22-33"))
        assertEquals("C8:47:8C:11:22:33", MacPayloadParser.extractMac("C8478C112233"))
        assertNull(MacPayloadParser.extractMac("not-a-mac-address"))
    }

    @Test
    fun demoSimulatorSyncsWithSessionEnginePasscodeAndTemperatureMasking() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val engine = BmsSessionEngine(passcodeStore = InMemoryPasscodeStore())
            val simulator = DemoBmsSimulator(engine, scope)
            simulator.start(initialScenario = DemoScenario.IDLE)

            // First connection requires passcode before exposing telemetry
            assertTrue(engine.snapshot.value.passcodeStatus is PasscodeStatus.Required)
            assertNull(engine.snapshot.value.telemetry)

            assertTrue(engine.submitPasscode("1234") is PasscodeStatus.Unlocked)
            simulator.emitSettingsAndCurrentCellsIfUnlocked()

            val telemetry = engine.snapshot.value.telemetry
            assertNotNull(telemetry)
            assertEquals(24, telemetry!!.enabledCellCount)
            assertEquals(87, telemetry.socPercent)
            // Disconnected T1..T4 probes (-200C) are masked to null, while T5 and MOS are preserved
            assertNull(telemetry.temp1C)
            assertNull(telemetry.temp4C)
            assertNotNull(telemetry.temp5C)
            assertNotNull(telemetry.mosfetTempC)
            assertNotNull(engine.snapshot.value.settings)
        } finally {
            scope.cancel()
        }
    }
}
