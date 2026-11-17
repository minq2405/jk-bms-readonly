package com.bmsjk.core.protocol

/**
 * Protocol variant selection. Note that JK02_24S vs JK02_32S refers to the
 * BMS firmware/hardware frame layout, NOT the physical cell count of the pack.
 */
enum class ProtocolMode {
    AUTO,
    JK02_32S,
    JK02_24S,
    JK04
}

enum class ProtocolVariant {
    JK02_32S,
    JK02_24S,
    JK04
}

enum class BalancingAction(val code: Int) {
    OFF(0),
    CHARGING(1),
    DISCHARGING(2),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): BalancingAction =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

enum class ChargeStatus(val code: Int) {
    BULK(0),
    ABSORPTION(1),
    FLOAT(2),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): ChargeStatus =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

enum class JkErrorFlag(val bit: Int, val key: String) {
    WIRE_RESISTANCE(0, "wire_resistance"),
    MOSFET_OVERTEMPERATURE(1, "mosfet_overtemperature"),
    CELL_COUNT_MISMATCH(2, "cell_count_mismatch"),
    BATTERY_FULLY_CHARGED(4, "battery_fully_charged"),
    PACK_OVERVOLTAGE(5, "pack_overvoltage"),
    CHARGE_OVERCURRENT(6, "charge_overcurrent"),
    CHARGE_SHORT_CIRCUIT(7, "charge_short_circuit"),
    CHARGE_OVERTEMPERATURE(8, "charge_overtemperature"),
    CHARGE_UNDERTEMPERATURE(9, "charge_undertemperature"),
    COPROCESSOR_COMM_ERROR(10, "coprocessor_comm_error"),
    CELL_UNDERVOLTAGE(11, "cell_undervoltage"),
    PACK_UNDERVOLTAGE(12, "pack_undervoltage"),
    DISCHARGE_OVERCURRENT(13, "discharge_overcurrent"),
    DISCHARGE_SHORT_CIRCUIT(14, "discharge_short_circuit"),
    DISCHARGE_OVERTEMPERATURE(15, "discharge_overtemperature"),
    CHARGING_MOSFET_ABNORMAL(16, "charging_mosfet_abnormal"),
    DISCHARGING_MOSFET_ABNORMAL(17, "discharging_mosfet_abnormal"),
    GPS_DISCONNECTED(18, "gps_disconnected"),
    MODIFY_PASSWORD_IN_TIME(19, "modify_password_in_time"),
    DISCHARGE_ON_FAILED(20, "discharge_on_failed"),
    BATTERY_OVERTEMPERATURE(21, "battery_overtemperature"),
    TEMP_SENSOR_ANOMALY(22, "temp_sensor_anomaly"),
    PL_MODULE_ANOMALY(23, "pl_module_anomaly"),
    SCP_RELEASE_FAILED(24, "scp_release_failed"),
    DISCHARGE_OCP_2(25, "discharge_ocp_2"),
    DISCHARGE_OCP_3(26, "discharge_ocp_3"),
    DISCHARGE_UNDERTEMPERATURE_ALARM(27, "discharge_undertemperature_alarm"),
    GPS_REMOTE_LOCK(28, "gps_remote_lock");

    companion object {
        fun decodeMask(mask: Long, bitWidth: Int): List<JkErrorFlag> {
            val active = mutableListOf<JkErrorFlag>()
            for (flag in entries) {
                if (flag.bit < bitWidth && ((mask ushr flag.bit) and 0x1L) == 0x1L) {
                    active.add(flag)
                }
            }
            return active
        }
    }
}

data class CellReading(
    val index: Int,
    val voltageV: Float,
    val resistanceOhm: Float,
    val enabled: Boolean,
    val wireWarning: Boolean = false
)

data class DeviceInfo(
    val frameCounter: Int,
    val vendorModel: String,
    val hardwareVersion: String,
    val softwareVersion: String,
    val uptimeSeconds: Long,
    val powerOnCount: Long,
    val deviceName: String,
    val devicePasscode: String,
    val manufacturingDate: String,
    val serialNumber: String,
    val userData: String,
    val setupPasscode: String
)

data class BmsTelemetry(
    val protocol: ProtocolVariant,
    val frameCounter: Int,
    val cells: List<CellReading>,
    val enabledCellCount: Int,
    val minCellVoltageV: Float,
    val maxCellVoltageV: Float,
    val minVoltageCellIndex: Int,
    val maxVoltageCellIndex: Int,
    val avgCellVoltageV: Float,
    val deltaCellVoltageMv: Float,
    val totalVoltageV: Float,
    val currentA: Float?,
    val powerW: Float?,
    val mosfetTempC: Float?,
    val temp1C: Float?,
    val temp2C: Float?,
    val temp3C: Float?,
    val temp4C: Float?,
    val temp5C: Float?,
    val socPercent: Int?,
    val sohPercent: Int?,
    val remainingCapacityAh: Float?,
    val nominalCapacityAh: Float?,
    val cycleCount: Long?,
    val totalCycleCapacityAh: Float?,
    val totalRuntimeSeconds: Long,
    val balanceCurrentA: Float,
    val balancingAction: BalancingAction,
    val chargingMosEnabled: Boolean?,
    val dischargingMosEnabled: Boolean?,
    val prechargingActive: Boolean?,
    val heatingEnabled: Boolean?,
    val heatingCurrentA: Float?,
    val emergencyCountdownSeconds: Int?,
    val errorsBitmask: Long,
    val activeErrors: List<JkErrorFlag>,
    val chargeStatus: ChargeStatus?,
    val chargeStatusTimeElapsedSeconds: Int?,
    val dryContact1Active: Boolean?,
    val dryContact2Active: Boolean?,
    val batteryTypeCode: Int?,
    val timestampMs: Long
) {
    val maxTemperatureC: Float?
        get() = listOfNotNull(mosfetTempC, temp1C, temp2C, temp3C, temp4C, temp5C)
            .filter { it.isFinite() && it > -100f && it < 200f }
            .maxOrNull()
}

data class BmsControlFlags(
    val heatingEnabled: Boolean,
    val disableTempSensors: Boolean,
    val gpsHeartbeat: Boolean,
    val portIsRs485: Boolean,
    val displayAlwaysOn: Boolean,
    val specialCharger: Boolean,
    val smartSleep: Boolean,
    val disablePclModule: Boolean,
    val timedStoredData: Boolean,
    val chargingFloatMode: Boolean,
    val emergencyButtonTrigger: Boolean,
    val dryContactIntermittent: Boolean,
    val dischargeOcp2: Boolean,
    val dischargeOcp3: Boolean,
    val gpsLockedCharging: Boolean,
    val gpsLockedDischarging: Boolean
)

/**
 * Immutable Read-Only view of BMS configuration decoded from Frame 0x01.
 */
data class BmsSettings(
    val protocol: ProtocolVariant,
    val frameCounter: Int,
    val cellCount: Int,
    val nominalCapacityAh: Float?,
    val startBalanceVoltageV: Float,
    val balanceTriggerVoltageV: Float,
    val maxBalanceCurrentA: Float,
    val chargeSwitchOn: Boolean?,
    val dischargeSwitchOn: Boolean?,
    val balancerSwitchOn: Boolean,
    val cellUvpV: Float?,
    val cellUvprV: Float?,
    val cellOvpV: Float?,
    val cellOvprV: Float?,
    val powerOffVoltageV: Float,
    val smartSleepVoltageV: Float?,
    val soc100VoltageV: Float?,
    val soc0VoltageV: Float?,
    val requestChargeVoltageV: Float?,
    val requestFloatVoltageV: Float?,
    val maxChargeCurrentA: Float?,
    val chargeOcpDelayS: Long?,
    val chargeOcpRecoveryS: Long?,
    val maxDischargeCurrentA: Float?,
    val dischargeOcpDelayS: Long?,
    val dischargeOcpRecoveryS: Long?,
    val scpDelayUs: Long?,
    val scpRecoveryS: Long?,
    val chargeOtpC: Float?,
    val chargeOtpRecoveryC: Float?,
    val dischargeOtpC: Float?,
    val dischargeOtpRecoveryC: Float?,
    val chargeUtpC: Float?,
    val chargeUtpRecoveryC: Float?,
    val mosfetOtpC: Float?,
    val mosfetOtpRecoveryC: Float?,
    val dischargeUtpC: Int?,
    val dischargeUtpRecoveryC: Int?,
    val heatingStartTempC: Int?,
    val heatingStopTempC: Int?,
    val deviceAddress: Int?,
    val prechargeTimeS: Int?,
    val smartSleepDelayH: Int?,
    val wireResistancesOhm: List<Float>,
    val controlFlags: BmsControlFlags?,
    val jk04VoltageCalibration: Float? = null
)

sealed interface DecodedFrame {
    data class SettingsFrame(val settings: BmsSettings) : DecodedFrame
    data class CellInfoFrame(val telemetry: BmsTelemetry) : DecodedFrame
    data class DeviceInfoFrame(val deviceInfo: DeviceInfo) : DecodedFrame
    data class UnsupportedFrame(val frameType: Int, val rawSize: Int) : DecodedFrame
}
