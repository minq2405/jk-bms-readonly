package com.bmsjk.core.protocol

/**
 * Decodes legacy JK04 frames (hardware <= v3.0, IEEE-754 4-byte floats).
 * Offsets verified against syssi/esphome-jk-bms (components/jk_bms_ble/jk_bms_ble.cpp).
 */
object Jk04Parser {

    fun parseCellInfo(
        data: ByteArray,
        timestampMs: Long = System.currentTimeMillis()
    ): BmsTelemetry {
        val cells = List(24) { i ->
            val voltageV = ByteReader.f32LE(data, 6 + i * 4)
            val resistanceOhm = ByteReader.f32LE(data, 102 + i * 4)
            val validVoltage = voltageV.isFinite() && voltageV > 0f && voltageV < 10f
            CellReading(
                index = i + 1,
                voltageV = if (validVoltage) voltageV else 0f,
                resistanceOhm = if (resistanceOhm.isFinite()) resistanceOhm else 0f,
                enabled = validVoltage
            )
        }

        val summary = CellSummaryCalculator.compute(cells)

        return BmsTelemetry(
            protocol = ProtocolVariant.JK04,
            frameCounter = ByteReader.u8(data, 5),
            cells = summary.cells,
            enabledCellCount = summary.enabledCount,
            minCellVoltageV = summary.minV,
            maxCellVoltageV = summary.maxV,
            minVoltageCellIndex = summary.minCellIndex,
            maxVoltageCellIndex = summary.maxCellIndex,
            avgCellVoltageV = summary.avgV,
            deltaCellVoltageMv = summary.deltaMv,
            totalVoltageV = summary.sumV,
            currentA = null,
            powerW = null,
            mosfetTempC = null,
            temp1C = null,
            temp2C = null,
            temp3C = null,
            temp4C = null,
            temp5C = null,
            socPercent = null,
            sohPercent = null,
            remainingCapacityAh = null,
            nominalCapacityAh = null,
            cycleCount = null,
            totalCycleCapacityAh = null,
            totalRuntimeSeconds = ByteReader.u32LE(data, 286),
            balanceCurrentA = ByteReader.f32LE(data, 222).let { if (it.isFinite()) it else 0f },
            balancingAction = BalancingAction.fromCode(ByteReader.u8(data, 220)),
            chargingMosEnabled = null,
            dischargingMosEnabled = null,
            prechargingActive = null,
            heatingEnabled = null,
            heatingCurrentA = null,
            emergencyCountdownSeconds = null,
            errorsBitmask = 0L,
            activeErrors = emptyList(),
            chargeStatus = null,
            chargeStatusTimeElapsedSeconds = null,
            dryContact1Active = null,
            dryContact2Active = null,
            batteryTypeCode = null,
            timestampMs = timestampMs
        )
    }

    fun parseSettings(data: ByteArray): BmsSettings =
        BmsSettings(
            protocol = ProtocolVariant.JK04,
            frameCounter = ByteReader.u8(data, 5),
            cellCount = ByteReader.u8(data, 34),
            nominalCapacityAh = null,
            startBalanceVoltageV = finiteOrZero(ByteReader.f32LE(data, 98)),
            balanceTriggerVoltageV = finiteOrZero(ByteReader.f32LE(data, 106)),
            maxBalanceCurrentA = finiteOrZero(ByteReader.f32LE(data, 110)),
            chargeSwitchOn = null,
            dischargeSwitchOn = null,
            balancerSwitchOn = ByteReader.u8(data, 114) != 0,
            cellUvpV = null,
            cellUvprV = null,
            cellOvpV = null,
            cellOvprV = null,
            powerOffVoltageV = finiteOrZero(ByteReader.f32LE(data, 38)),
            smartSleepVoltageV = null,
            soc100VoltageV = null,
            soc0VoltageV = null,
            requestChargeVoltageV = null,
            requestFloatVoltageV = null,
            maxChargeCurrentA = null,
            chargeOcpDelayS = null,
            chargeOcpRecoveryS = null,
            maxDischargeCurrentA = null,
            dischargeOcpDelayS = null,
            dischargeOcpRecoveryS = null,
            scpDelayUs = null,
            scpRecoveryS = null,
            chargeOtpC = null,
            chargeOtpRecoveryC = null,
            dischargeOtpC = null,
            dischargeOtpRecoveryC = null,
            chargeUtpC = null,
            chargeUtpRecoveryC = null,
            mosfetOtpC = null,
            mosfetOtpRecoveryC = null,
            dischargeUtpC = null,
            dischargeUtpRecoveryC = null,
            heatingStartTempC = null,
            heatingStopTempC = null,
            deviceAddress = null,
            prechargeTimeS = null,
            smartSleepDelayH = null,
            wireResistancesOhm = emptyList(),
            controlFlags = null,
            jk04VoltageCalibration = finiteOrNull(ByteReader.f32LE(data, 74))
        )

    private fun finiteOrZero(v: Float): Float = if (v.isFinite()) v else 0f

    private fun finiteOrNull(v: Float): Float? = if (v.isFinite()) v else null
}
