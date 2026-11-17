package com.bmsjk.cli

import com.bmsjk.core.protocol.BmsSettings
import com.bmsjk.core.protocol.BmsTelemetry
import com.bmsjk.core.protocol.DecodedFrame
import com.bmsjk.core.protocol.DeviceInfo
import com.bmsjk.core.protocol.JkFrameCodec
import com.bmsjk.core.protocol.ProtocolMode
import com.bmsjk.core.session.BmsSessionEngine
import com.bmsjk.core.session.PasscodeStatus
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    var mode = ProtocolMode.AUTO
    var filePath: String? = null
    var passcode: String? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--mode" -> {
                val raw = args.getOrNull(i + 1)
                if (raw == null || raw.startsWith("--")) {
                    System.err.println("Missing value for --mode. Allowed: ${ProtocolMode.entries.joinToString()}")
                    exitProcess(1)
                }
                val parsed = runCatching { ProtocolMode.valueOf(raw.uppercase(Locale.US)) }.getOrNull()
                if (parsed == null) {
                    System.err.println("Invalid --mode '$raw'. Allowed: ${ProtocolMode.entries.joinToString()}")
                    exitProcess(1)
                }
                mode = parsed
                i += 2
            }
            "--passcode" -> {
                val raw = args.getOrNull(i + 1)
                if (raw == null || raw.startsWith("--")) {
                    System.err.println("Missing value for --passcode")
                    exitProcess(1)
                }
                passcode = raw
                i += 2
            }
            else -> {
                filePath = args[i]
                i++
            }
        }
    }

    val lines = if (filePath != null) {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            System.err.println("Capture file not found: $filePath")
            exitProcess(1)
        }
        file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    } else {
        println("No capture file supplied; running built-in syssi JK02_24S real sample frames...")
        builtInSampleHexLines()
    }

    val engine = BmsSessionEngine(initialMode = mode)
    engine.startSession(deviceAddress = "CLI:CAPTURE")

    println("=== JK BMS Read-Only Backend CLI (mode=${mode.name}) ===")
    for ((idx, line) in lines.withIndex()) {
        val raw = try {
            JkFrameCodec.parseHexString(line)
        } catch (e: IllegalArgumentException) {
            println("[Line #${idx + 1}] Invalid hex input: ${e.message}")
            continue
        }
        if (raw.isEmpty()) continue

        // Simulate realistic 20-byte BLE MTU notifications
        val chunks = raw.toList().chunked(20).map { it.toByteArray() }
        val decodedFrames = ArrayList<DecodedFrame>()
        for (chunk in chunks) {
            decodedFrames.addAll(engine.onNotificationChunkAll(chunk))
        }

        if (decodedFrames.isEmpty()) {
            println("[Frame #${idx + 1}] Incomplete or CRC error (crcErrors=${engine.snapshot.value.crcErrorCount})")
            continue
        }

        for (decoded in decodedFrames) {
            when (decoded) {
                is DecodedFrame.DeviceInfoFrame -> {
                    printDeviceInfo(decoded.deviceInfo, engine.snapshot.value.detectedVariant?.name)
                    if (engine.snapshot.value.passcodeStatus is PasscodeStatus.Required) {
                        val code = passcode ?: decoded.deviceInfo.devicePasscode
                        val status = engine.submitPasscode(code)
                        println("  Passcode check -> $status")
                    }
                }
                is DecodedFrame.SettingsFrame -> {
                    if (engine.snapshot.value.isUnlocked) {
                        printSettings(decoded.settings)
                    } else {
                        println("[0x01] SETTINGS locked (passcode required/mismatch)")
                    }
                }
                is DecodedFrame.CellInfoFrame -> {
                    if (engine.snapshot.value.isUnlocked) {
                        printTelemetry(decoded.telemetry)
                    } else {
                        println("[0x02] CELL INFO locked (passcode required/mismatch)")
                    }
                }
                is DecodedFrame.UnsupportedFrame -> println("Unsupported frame type=0x${decoded.frameType.toString(16)}")
            }
        }
    }
}

private fun printDeviceInfo(info: DeviceInfo, detected: String?) {
    println("\n--- [0x03] DEVICE INFO (detected=$detected) ---")
    println("  Model/Vendor : ${info.vendorModel}")
    println("  HW / SW      : ${info.hardwareVersion} / ${info.softwareVersion}")
    println("  Device Name  : ${info.deviceName}")
    println("  Serial / Mfg : ${info.serialNumber} / ${info.manufacturingDate}")
    println("  Uptime       : ${info.uptimeSeconds}s (PowerOn: ${info.powerOnCount})")
}

private fun printTelemetry(t: BmsTelemetry) {
    println("\n--- [0x02] CELL INFO (${t.protocol}) ---")
    println(
        String.format(
            Locale.US,
            "  Pack: %.3f V | Current: %s A | Power: %s W | SOC: %s%% | SOH: %s%%",
            t.totalVoltageV,
            t.currentA?.let { String.format(Locale.US, "%.3f", it) } ?: "-",
            t.powerW?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
            t.socPercent?.toString() ?: "-",
            t.sohPercent?.toString() ?: "-"
        )
    )
    println(
        String.format(
            Locale.US,
            "  Cells (%d active): Min=%.3fV(#%d) Avg=%.3fV Max=%.3fV(#%d) Delta=%.1fmV",
            t.enabledCellCount,
            t.minCellVoltageV,
            t.minVoltageCellIndex,
            t.avgCellVoltageV,
            t.maxCellVoltageV,
            t.maxVoltageCellIndex,
            t.deltaCellVoltageMv
        )
    )
    println(
        "  Temps: MOS=${t.mosfetTempC ?: "-"}°C | T1=${t.temp1C ?: "-"}°C | T2=${t.temp2C ?: "-"}°C | T3=${t.temp3C ?: "-"} | T4=${t.temp4C ?: "-"} | T5=${t.temp5C ?: "-"}"
    )
    println(
        "  MOSFETs: Charge=${t.chargingMosEnabled} | Discharge=${t.dischargingMosEnabled} | Precharge=${t.prechargingActive} | Heat=${t.heatingEnabled} | Bal=${t.balancingAction}(${t.balanceCurrentA}A)"
    )
    println("  Errors: ${if (t.activeErrors.isEmpty()) "None" else t.activeErrors.joinToString { it.key }} (0x${t.errorsBitmask.toString(16)})")
    val cellList = t.cells.filter { it.enabled }.joinToString(" | ") { c ->
        String.format(Locale.US, "C%02d:%.3fV(%.3fΩ)", c.index, c.voltageV, c.resistanceOhm)
    }
    println("  Cells: $cellList")
}

private fun printSettings(s: BmsSettings) {
    println("\n--- [0x01] SETTINGS (READ-ONLY, ${s.protocol}) ---")
    println("  Cells=${s.cellCount}S | Nominal=${s.nominalCapacityAh ?: "-"}Ah | BalStart=${s.startBalanceVoltageV}V | BalTrig=${s.balanceTriggerVoltageV}V | MaxBal=${s.maxBalanceCurrentA}A")
    println("  Switches (RO): Charge=${s.chargeSwitchOn} | Discharge=${s.dischargeSwitchOn} | Balancer=${s.balancerSwitchOn}")
    println("  Protections V: OVP=${s.cellOvpV} (R=${s.cellOvprV}) | UVP=${s.cellUvpV} (R=${s.cellUvprV}) | PowerOff=${s.powerOffVoltageV}")
    println("  Protections I: MaxChg=${s.maxChargeCurrentA}A | MaxDsg=${s.maxDischargeCurrentA}A | SCP=${s.scpDelayUs}us")
    println("  Protections T: ChgOTP=${s.chargeOtpC}°C | DsgOTP=${s.dischargeOtpC}°C | ChgUTP=${s.chargeUtpC}°C | MosOTP=${s.mosfetOtpC}°C")
}

private fun builtInSampleHexLines(): List<String> = listOf(
    // JK02_24S Device Info (0x03) from syssi/esphome-jk-bms
    "55AAEB90039F4A4B2D4232413234533135500000000031302E585700000031302E303700000040AF010006000000" +
        "4A4B2D42324132345331355000000000313233340000000000000000000000003232303430370000323032313630" +
        "32303936003030303000496E70757420557365726461746100003132333435360000000000000000000000000000" +
        "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "000000000000000000000000000000000000000000000065",
    // JK02_24S Settings (0x01) from syssi/esphome-jk-bms
    "55AAEB90014F58020000540B0000800C0000CC100000681000000A00000000000000000000000000000000000000" +
        "F00A0000A86100001E0000003C000000F04902002C0100003C0000003C000000D0070000BC02000058020000BC02" +
        "00005802000038FFFFFF9CFFFFFF84030000BC0200000D00000001000000010000000100000088130000DC050000" +
        "E40C0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "000000000000000000000000000000000000000000000040",
    // JK02_24S Cell Info (0x02) from syssi/esphome-jk-bms
    "55AAEB90028CFF0C010D010DFF0C010D010DFF0C010D010D010D010DFF0C010D010D010D010D0000000000000000" +
        "0000000000000000FFFF0000000D000000009D0196018C0187018401840183018401850181018301860182018201" +
        "830185010000000000000000000000000000000000000000000003D000000000000000000000BE00BF00D2000000" +
        "000000548E0B0100683C0100000000003D04000064007904CA0310000101AA060000000000000000000000000700" +
        "01000000D50200000000AED63B400000000058AAFDFF0000000100020000ECE64F00000000000000000000000000" +
        "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
        "0000000000000000000000000000000000000000000000CD"
)
