package com.bmsjk.core.protocol

/**
 * Shared little-endian binary readers for 300-byte JK BMS frames.
 */
internal object ByteReader {

    fun u8(data: ByteArray, offset: Int): Int {
        if (offset !in data.indices) return 0
        return data[offset].toInt() and 0xFF
    }

    fun i8(data: ByteArray, offset: Int): Int {
        if (offset !in data.indices) return 0
        return data[offset].toInt()
    }

    fun u16LE(data: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 1 >= data.size) return 0
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun i16LE(data: ByteArray, offset: Int): Int {
        val raw = u16LE(data, offset)
        return if ((raw and 0x8000) != 0) raw - 0x10000 else raw
    }

    fun u32LE(data: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 3 >= data.size) return 0L
        return (data[offset].toLong() and 0xFFL) or
            ((data[offset + 1].toLong() and 0xFFL) shl 8) or
            ((data[offset + 2].toLong() and 0xFFL) shl 16) or
            ((data[offset + 3].toLong() and 0xFFL) shl 24)
    }

    fun i32LE(data: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 3 >= data.size) return 0
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    fun f32LE(data: ByteArray, offset: Int): Float =
        java.lang.Float.intBitsToFloat(i32LE(data, offset))

    fun cString(data: ByteArray, offset: Int, maxLen: Int): String {
        if (offset < 0 || offset >= data.size || maxLen <= 0) return ""
        val end = minOf(data.size, offset + maxLen)
        val sb = StringBuilder()
        for (i in offset until end) {
            val b = data[i].toInt() and 0xFF
            if (b == 0) break
            if (b in 0x20..0x7E) {
                sb.append(b.toChar())
            } else {
                break
            }
        }
        return sb.toString().trim()
    }
}
