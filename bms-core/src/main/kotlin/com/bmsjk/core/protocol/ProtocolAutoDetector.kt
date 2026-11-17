package com.bmsjk.core.protocol

import kotlin.math.abs

/**
 * Auto-detects JK BMS BLE protocol variant (JK04, JK02_24S, JK02_32S).
 * Primary source: hardwareVersion / softwareVersion in Frame 0x03.
 * Secondary verification: voltage-sum consistency check on Frame 0x02.
 */
object ProtocolAutoDetector {

    fun detectFromDeviceInfo(info: DeviceInfo): ProtocolVariant? {
        val hwMajor = parseMajorVersion(info.hardwareVersion)
        if (hwMajor != null) {
            return mapMajorVersion(hwMajor)
        }
        val swMajor = parseMajorVersion(info.softwareVersion)
        if (swMajor != null) {
            return mapMajorVersion(swMajor)
        }
        return null
    }

    /**
     * Heuristic fallback when Frame 0x02 arrives before Frame 0x03 or HW string is non-standard.
     * Distinguishes JK02_32S vs JK02_24S by comparing the sum of cell voltages against
     * totalVoltage at offset 150 (32S) vs offset 118 (24S).
     */
    fun detectFromCellFrame(frame: ByteArray): ProtocolVariant {
        val score32s = scoreJk02Candidate(frame, is32s = true)
        val score24s = scoreJk02Candidate(frame, is32s = false)
        if (score32s >= score24s && score32s > 0) return ProtocolVariant.JK02_32S
        if (score24s > score32s && score24s > 0) return ProtocolVariant.JK02_24S
        return ProtocolVariant.JK02_32S
    }

    private fun scoreJk02Candidate(frame: ByteArray, is32s: Boolean): Int {
        if (frame.size < JkFrameCodec.FRAME_SIZE) return -1
        val maxCells = if (is32s) 32 else 24
        val cellOffset = if (is32s) 16 else 0
        val tailOffset = cellOffset * 2

        val mask = ByteReader.u32LE(frame, 54 + cellOffset)
        var sumV = 0f
        var active = 0
        for (i in 0 until maxCells) {
            val v = ByteReader.u16LE(frame, 6 + i * 2) * 0.001f
            val bitSet = mask == 0L || ((mask ushr i) and 0x1L) == 0x1L
            if (bitSet && v in 0.5f..5.0f) {
                sumV += v
                active++
            }
        }
        if (active == 0) return -1

        val totalV = ByteReader.u32LE(frame, 118 + tailOffset) * 0.001f
        val soc = ByteReader.u8(frame, 141 + tailOffset)
        var score = 0
        if (totalV > 3f && abs(totalV - sumV) <= maxOf(1.5f, sumV * 0.05f)) {
            score += 10
        }
        if (soc in 0..100) {
            score += 2
        }
        return score
    }

    internal fun parseMajorVersion(raw: String): Int? {
        if (raw.isBlank()) return null
        val match = Regex("""(\d+)""").find(raw) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun mapMajorVersion(major: Int): ProtocolVariant = when {
        major <= 3 -> ProtocolVariant.JK04
        major <= 10 -> ProtocolVariant.JK02_24S
        else -> ProtocolVariant.JK02_32S
    }
}
