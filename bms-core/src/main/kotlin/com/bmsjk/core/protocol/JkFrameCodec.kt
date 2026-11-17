package com.bmsjk.core.protocol

import java.util.UUID

/**
 * Strictly READ-ONLY JK BMS BLE codec.
 * Only read-request commands (0x96, 0x97) are modeled or allowed.
 */
object JkFrameCodec {

    val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val FRAME_SIZE = 300
    const val MAX_BUFFER_SIZE = 420
    const val COMMAND_FRAME_SIZE = 20

    const val FRAME_TYPE_SETTINGS = 0x01
    const val FRAME_TYPE_CELL_INFO = 0x02
    const val FRAME_TYPE_DEVICE_INFO = 0x03
    const val FRAME_TYPE_LOGBOOK = 0x05

    private val REQUEST_HEADER = byteArrayOf(
        0xAA.toByte(),
        0x55.toByte(),
        0x90.toByte(),
        0xEB.toByte()
    )

    private val RESPONSE_HEADER = byteArrayOf(
        0x55.toByte(),
        0xAA.toByte(),
        0xEB.toByte(),
        0x90.toByte()
    )

    enum class ReadCommand(val code: Int) {
        CELL_INFO(0x96),
        DEVICE_INFO(0x97)
    }

    fun buildReadCommand(command: ReadCommand, sequenceCounter: Int): ByteArray {
        val frame = ByteArray(COMMAND_FRAME_SIZE)
        REQUEST_HEADER.copyInto(frame, destinationOffset = 0)
        frame[4] = (command.code and 0xFF).toByte()
        frame[5] = 0x00
        frame[16] = (sequenceCounter and 0xFF).toByte()
        frame[19] = crc8(frame, COMMAND_FRAME_SIZE - 1)
        return frame
    }

    fun crc8(data: ByteArray, length: Int = data.size): Byte {
        var sum = 0
        val limit = minOf(length, data.size)
        for (i in 0 until limit) {
            sum = (sum + (data[i].toInt() and 0xFF)) and 0xFF
        }
        return sum.toByte()
    }

    fun validateResponseFrame(frame: ByteArray): Boolean {
        if (frame.size < FRAME_SIZE) return false
        if (!startsWithResponseHeader(frame)) return false
        val expected = crc8(frame, FRAME_SIZE - 1)
        return expected == frame[FRAME_SIZE - 1]
    }

    fun startsWithResponseHeader(chunk: ByteArray): Boolean =
        chunk.size >= 4 &&
            chunk[0] == RESPONSE_HEADER[0] &&
            chunk[1] == RESPONSE_HEADER[1] &&
            chunk[2] == RESPONSE_HEADER[2] &&
            chunk[3] == RESPONSE_HEADER[3]

    fun parseHexString(hex: String): ByteArray {
        val cleaned = hex.replace(Regex("[^0-9A-Fa-f]"), "")
        require(cleaned.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun toHexString(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}

sealed interface FrameAssembleResult {
    data object Incomplete : FrameAssembleResult
    data object IgnoredOutOfSync : FrameAssembleResult
    data object OverflowReset : FrameAssembleResult
    data class CrcError(val expected: Int, val actual: Int, val rawFrame: ByteArray) : FrameAssembleResult
    data class Assembled(val frame: ByteArray) : FrameAssembleResult
}

/**
 * Reassembles fragmented BLE GATT notifications into validated 300-byte JK BMS frames.
 */
class FrameAssembler {
    private val buffer = ArrayList<Byte>(JkFrameCodec.MAX_BUFFER_SIZE)

    fun reset() {
        buffer.clear()
    }

    fun appendChunk(chunk: ByteArray): FrameAssembleResult {
        if (chunk.isEmpty()) return FrameAssembleResult.Incomplete

        if (JkFrameCodec.startsWithResponseHeader(chunk)) {
            buffer.clear()
        } else if (buffer.isEmpty()) {
            // Wait for a valid 55 AA EB 90 preamble if connecting mid-stream.
            return FrameAssembleResult.IgnoredOutOfSync
        }

        if (buffer.size + chunk.size > JkFrameCodec.MAX_BUFFER_SIZE) {
            buffer.clear()
            return FrameAssembleResult.OverflowReset
        }

        for (b in chunk) {
            buffer.add(b)
        }

        if (buffer.size < JkFrameCodec.FRAME_SIZE) {
            return FrameAssembleResult.Incomplete
        }

        val frame = ByteArray(JkFrameCodec.FRAME_SIZE) { i -> buffer[i] }
        buffer.clear()

        val computed = JkFrameCodec.crc8(frame, JkFrameCodec.FRAME_SIZE - 1).toInt() and 0xFF
        val remote = frame[JkFrameCodec.FRAME_SIZE - 1].toInt() and 0xFF
        if (computed != remote) {
            return FrameAssembleResult.CrcError(computed, remote, frame)
        }

        return FrameAssembleResult.Assembled(frame)
    }
}
