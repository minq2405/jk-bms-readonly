package com.bmsjk.core.protocol

/**
 * Decodes JK02_24S and JK02_32S frames (0x01, 0x02, 0x03).
 * Offsets verified against syssi/esphome-jk-bms (components/jk_bms_ble/jk_bms_ble.cpp).
 */
object Jk02Parser {

    fun parseDeviceInfo(data: ByteArray): DeviceInfo {
        val rawMfg = ByteReader.cString(data, 78, 8)
        val formattedMfg = if (rawMfg.length == 6 && rawMfg.all { it.isDigit() }) {
            "20$rawMfg"
        } else {
            rawMfg
        }

        return DeviceInfo(
            frameCounter = ByteReader.u8(data, 5),
            vendorModel = ByteReader.cString(data, 6, 16),
            hardwareVersion = ByteReader.cString(data, 22, 8),
            softwareVersion = ByteReader.cString(data, 30, 8),
            uptimeSeconds = ByteReader.u32LE(data, 38),
            powerOnCount = ByteReader.u32LE(data, 42),
            deviceName = ByteReader.cString(data, 46, 16),
            devicePasscode = ByteReader.cString(data, 62, 16),
            manufacturingDate = formattedMfg,
            serialNumber = ByteReader.cString(data, 86, 11),
            userData = ByteReader.cString(data, 102, 16),
            setupPasscode = ByteReader.cString(data, 118, 16)
        )
    }

    fun parseCellInfo(
        data: ByteArray,
        variant: ProtocolVariant,
        timestampMs: Long = System.currentTimeMillis()
    ): BmsTelemetry {
        require(variant == ProtocolVariant.JK02_24S || variant == ProtocolVariant.JK02_32S) {
            "Jk02Parser only supports JK02_24S and JK02_32S"
        }
        val is32s = variant == ProtocolVariant.JK02_32S
        val maxCells = if (is32s) 32 else 24
        val cellOffset = if (is32s) 16 else 0
        val tailOffset = cellOffset * 2

        val enabledMask = ByteReader.u32LE(data, 54 + cellOffset)
        val wireWarnMask = ByteReader.u32LE(data, 114 + tailOffset)

        val cells = List(maxCells) { i ->
            val voltageV = ByteReader.u16LE(data, 6 + i * 2) * 0.001f
            val resistanceOhm = ByteReader.u16LE(data, 64 + cellOffset + i * 2) * 0.001f
            val enabled = if (enabledMask != 0L) {
                ((enabledMask ushr i) and 0x1L) == 0x1L
            } else {
                voltageV > 0f
            }
            val wireWarning = ((wireWarnMask ushr i) and 0x1L) == 0x1L
            CellReading(
                index = i + 1,
                voltageV = voltageV,
                resistanceOhm = resistanceOhm,
                enabled = enabled,
                wireWarning = wireWarning
            )
        }

        val summary = CellSummaryCalculator.compute(cells)

        val totalVoltageV = ByteReader.u32LE(data, 118 + tailOffset) * 0.001f
        val currentA = ByteReader.i32LE(data, 126 + tailOffset) * 0.001f
        // Byte 122+tailOffset is unsigned power in BMS frames; signed V * I preserves charge/discharge sign.
        val signedPowerW = totalVoltageV * currentA

        val temp1C = sanitizeTemp(ByteReader.i16LE(data, 130 + tailOffset))
        val temp2C = sanitizeTemp(ByteReader.i16LE(data, 132 + tailOffset))
        val mosfetTempC = if (is32s) {
            sanitizeTemp(ByteReader.i16LE(data, 112 + tailOffset))
        } else {
            sanitizeTemp(ByteReader.i16LE(data, 134 + tailOffset))
        }

        val errorsBitmask = if (is32s) {
            ByteReader.u32LE(data, 134 + tailOffset)
        } else {
            ByteReader.u16LE(data, 136 + tailOffset).toLong()
        }
        val activeErrors = JkErrorFlag.decodeMask(errorsBitmask, if (is32s) 32 else 16)

        return BmsTelemetry(
            protocol = variant,
            frameCounter = ByteReader.u8(data, 5),
            cells = summary.cells,
            enabledCellCount = summary.enabledCount,
            minCellVoltageV = summary.minV,
            maxCellVoltageV = summary.maxV,
            minVoltageCellIndex = summary.minCellIndex,
            maxVoltageCellIndex = summary.maxCellIndex,
            avgCellVoltageV = summary.avgV,
            deltaCellVoltageMv = summary.deltaMv,
            totalVoltageV = totalVoltageV,
            currentA = currentA,
            powerW = signedPowerW,
            mosfetTempC = mosfetTempC,
            temp1C = temp1C,
            temp2C = temp2C,
            temp3C = if (is32s) sanitizeTemp(ByteReader.i16LE(data, 226 + tailOffset)) else null,
            temp4C = if (is32s) sanitizeTemp(ByteReader.i16LE(data, 224 + tailOffset)) else null,
            temp5C = if (is32s) sanitizeTemp(ByteReader.i16LE(data, 222 + tailOffset)) else null,
            socPercent = ByteReader.u8(data, 141 + tailOffset),
            sohPercent = ByteReader.u8(data, 158 + tailOffset),
            remainingCapacityAh = ByteReader.u32LE(data, 142 + tailOffset) * 0.001f,
            nominalCapacityAh = ByteReader.u32LE(data, 146 + tailOffset) * 0.001f,
            cycleCount = ByteReader.u32LE(data, 150 + tailOffset),
            totalCycleCapacityAh = ByteReader.u32LE(data, 154 + tailOffset) * 0.001f,
            totalRuntimeSeconds = ByteReader.u32LE(data, 162 + tailOffset),
            balanceCurrentA = ByteReader.i16LE(data, 138 + tailOffset) * 0.001f,
            balancingAction = BalancingAction.fromCode(ByteReader.u8(data, 140 + tailOffset)),
            chargingMosEnabled = ByteReader.u8(data, 166 + tailOffset) != 0,
            dischargingMosEnabled = ByteReader.u8(data, 167 + tailOffset) != 0,
            prechargingActive = ByteReader.u8(data, 168 + tailOffset) != 0,
            heatingEnabled = ByteReader.u8(data, 183 + tailOffset) != 0,
            heatingCurrentA = ByteReader.i16LE(data, 204 + tailOffset) * 0.001f,
            emergencyCountdownSeconds = ByteReader.u16LE(data, 186 + tailOffset),
            errorsBitmask = errorsBitmask,
            activeErrors = activeErrors,
            chargeStatus = if (is32s) ChargeStatus.fromCode(ByteReader.u8(data, 248 + tailOffset)) else null,
            chargeStatusTimeElapsedSeconds = if (is32s) ByteReader.u16LE(data, 246 + tailOffset) else null,
            dryContact1Active = if (is32s) (ByteReader.u8(data, 249 + tailOffset) and 0x02) != 0 else null,
            dryContact2Active = if (is32s) (ByteReader.u8(data, 249 + tailOffset) and 0x04) != 0 else null,
            batteryTypeCode = if (is32s) ByteReader.u8(data, 243 + tailOffset) else null,
            timestampMs = timestampMs
        )
    }

    fun parseSettings(data: ByteArray, variant: ProtocolVariant): BmsSettings {
        require(variant == ProtocolVariant.JK02_24S || variant == ProtocolVariant.JK02_32S) {
            "Jk02Parser only supports JK02_24S and JK02_32S"
        }
        val is32s = variant == ProtocolVariant.JK02_32S
        val wireCount = if (is32s) 32 else 24
        val wireStart = if (is32s) 142 else 158
        val wireResistances = List(wireCount) { i ->
            ByteReader.u32LE(data, wireStart + i * 4) * 0.001f
        }

        val controlFlags = if (is32s) {
            val b282 = ByteReader.u8(data, 282)
            val b283 = ByteReader.u8(data, 283)
            BmsControlFlags(
                heatingEnabled = (b282 and 0x01) != 0,
                disableTempSensors = (b282 and 0x02) != 0,
                gpsHeartbeat = (b282 and 0x04) != 0,
                portIsRs485 = (b282 and 0x08) != 0,
                displayAlwaysOn = (b282 and 0x10) != 0,
                specialCharger = (b282 and 0x20) != 0,
                smartSleep = (b282 and 0x40) != 0,
                disablePclModule = (b282 and 0x80) != 0,
                timedStoredData = (b283 and 0x01) != 0,
                chargingFloatMode = (b283 and 0x02) != 0,
                emergencyButtonTrigger = (b283 and 0x04) != 0,
                dryContactIntermittent = (b283 and 0x08) != 0,
                dischargeOcp2 = (b283 and 0x10) != 0,
                dischargeOcp3 = (b283 and 0x20) != 0,
                gpsLockedCharging = (b283 and 0x40) != 0,
                gpsLockedDischarging = (b283 and 0x80) != 0
            )
        } else {
            null
        }

        return BmsSettings(
            protocol = variant,
            frameCounter = ByteReader.u8(data, 5),
            cellCount = ByteReader.u8(data, 114),
            nominalCapacityAh = ByteReader.u32LE(data, 130) * 0.001f,
            startBalanceVoltageV = ByteReader.u32LE(data, 138) * 0.001f,
            balanceTriggerVoltageV = ByteReader.u32LE(data, 26) * 0.001f,
            maxBalanceCurrentA = ByteReader.u32LE(data, 78) * 0.001f,
            chargeSwitchOn = ByteReader.u8(data, 118) != 0,
            dischargeSwitchOn = ByteReader.u8(data, 122) != 0,
            balancerSwitchOn = ByteReader.u8(data, 126) != 0,
            cellUvpV = ByteReader.u32LE(data, 10) * 0.001f,
            cellUvprV = ByteReader.u32LE(data, 14) * 0.001f,
            cellOvpV = ByteReader.u32LE(data, 18) * 0.001f,
            cellOvprV = ByteReader.u32LE(data, 22) * 0.001f,
            powerOffVoltageV = ByteReader.u32LE(data, 46) * 0.001f,
            smartSleepVoltageV = if (is32s) ByteReader.u32LE(data, 6) * 0.001f else null,
            soc100VoltageV = if (is32s) ByteReader.u32LE(data, 30) * 0.001f else null,
            soc0VoltageV = if (is32s) ByteReader.u32LE(data, 34) * 0.001f else null,
            requestChargeVoltageV = if (is32s) ByteReader.u32LE(data, 38) * 0.001f else null,
            requestFloatVoltageV = if (is32s) ByteReader.u32LE(data, 42) * 0.001f else null,
            maxChargeCurrentA = ByteReader.u32LE(data, 50) * 0.001f,
            chargeOcpDelayS = ByteReader.u32LE(data, 54),
            chargeOcpRecoveryS = ByteReader.u32LE(data, 58),
            maxDischargeCurrentA = ByteReader.u32LE(data, 62) * 0.001f,
            dischargeOcpDelayS = ByteReader.u32LE(data, 66),
            dischargeOcpRecoveryS = ByteReader.u32LE(data, 70),
            scpDelayUs = ByteReader.u32LE(data, 134),
            scpRecoveryS = ByteReader.u32LE(data, 74),
            chargeOtpC = ByteReader.u32LE(data, 82) * 0.1f,
            chargeOtpRecoveryC = ByteReader.u32LE(data, 86) * 0.1f,
            dischargeOtpC = ByteReader.u32LE(data, 90) * 0.1f,
            dischargeOtpRecoveryC = ByteReader.u32LE(data, 94) * 0.1f,
            chargeUtpC = ByteReader.i32LE(data, 98) * 0.1f,
            chargeUtpRecoveryC = ByteReader.i32LE(data, 102) * 0.1f,
            mosfetOtpC = ByteReader.i32LE(data, 106) * 0.1f,
            mosfetOtpRecoveryC = ByteReader.i32LE(data, 110) * 0.1f,
            dischargeUtpC = if (is32s) ByteReader.i8(data, 296) else null,
            dischargeUtpRecoveryC = if (is32s) ByteReader.i8(data, 297) else null,
            heatingStartTempC = if (is32s) ByteReader.i8(data, 284) else null,
            heatingStopTempC = if (is32s) ByteReader.i8(data, 285) else null,
            deviceAddress = if (is32s) ByteReader.u8(data, 270) else null,
            prechargeTimeS = if (is32s) ByteReader.u8(data, 274) else null,
            smartSleepDelayH = if (is32s) ByteReader.u8(data, 286) else null,
            wireResistancesOhm = wireResistances,
            controlFlags = controlFlags
        )
    }

    /**
     * Disconnected JK BMS NTC probes report <= -100C (typically -200.0C / 0xF830);
     * mask out-of-range probes to null so the UI renders "--".
     */
    private fun sanitizeTemp(rawDeciC: Int): Float? {
        val tempC = rawDeciC * 0.1f
        return if (tempC <= -100f || tempC >= 200f) null else tempC
    }
}
