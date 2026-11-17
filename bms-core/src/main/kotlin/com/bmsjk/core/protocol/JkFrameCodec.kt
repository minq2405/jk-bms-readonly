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
    const val MAX_BUFFER_SIZE = 2048
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
        val withoutPrefix = hex.replace(Regex("0[xX](?=[0-9A-Fa-f])"), "")
        val cleaned = withoutPrefix.replace(Regex("[^0-9A-Fa-f]"), "")
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
 * Handles split 4-byte preambles (55 AA EB 90), trailing bytes belonging to subsequent frames,
 * mid-frame payload coincidences, and concatenated frames in a single notification.
 */
class FrameAssembler {
    private val buffer = ArrayList<Byte>(JkFrameCodec.MAX_BUFFER_SIZE)
    private var inSync = false

    @Synchronized
    fun reset() {
        buffer.clear()
        inSync = false
    }

    @Synchronized
    fun appendChunk(chunk: ByteArray): FrameAssembleResult {
        val results = appendChunkAll(chunk)
        return results.lastOrNull() ?: FrameAssembleResult.Incomplete
    }

    @Synchronized
    fun appendChunkAll(chunk: ByteArray): List<FrameAssembleResult> {
        if (chunk.isEmpty()) return listOf(FrameAssembleResult.Incomplete)

        if (buffer.size + chunk.size > JkFrameCodec.MAX_BUFFER_SIZE) {
            buffer.clear()
            inSync = false
            return listOf(FrameAssembleResult.OverflowReset)
        }

        for (b in chunk) {
            buffer.add(b)
        }

        if (!inSync) {
            val headerIdx = findHeaderIndex(buffer, startIndex = 0)
            if (headerIdx >= 0) {
                if (headerIdx > 0) {
                    buffer.subList(0, headerIdx).clear()
                }
                inSync = true
            } else {
                // Keep only a trailing partial match (1..3 bytes) of 55 AA EB 90 in case header spans chunks.
                val partialLen = trailingHeaderPrefixLength(buffer)
                if (partialLen > 0) {
                    val dropCount = buffer.size - partialLen
                    if (dropCount > 0) {
                        buffer.subList(0, dropCount).clear()
                    }
                    return listOf(FrameAssembleResult.Incomplete)
                }
                buffer.clear()
                return listOf(FrameAssembleResult.IgnoredOutOfSync)
            }
        }

        if (buffer.size < JkFrameCodec.FRAME_SIZE) {
            return listOf(FrameAssembleResult.Incomplete)
        }

        val results = ArrayList<FrameAssembleResult>(2)
        while (inSync && buffer.size >= JkFrameCodec.FRAME_SIZE) {
            val frame = ByteArray(JkFrameCodec.FRAME_SIZE) { i -> buffer[i] }
            val computed = JkFrameCodec.crc8(frame, JkFrameCodec.FRAME_SIZE - 1).toInt() and 0xFF
            val remote = frame[JkFrameCodec.FRAME_SIZE - 1].toInt() and 0xFF

            if (computed == remote) {
                buffer.subList(0, JkFrameCodec.FRAME_SIZE).clear()
                results.add(FrameAssembleResult.Assembled(frame))
                resyncRemainingBuffer()
            } else {
                results.add(FrameAssembleResult.CrcError(computed, remote, frame))
                // Try resyncing from byte 1 in case a truncated frame was followed by a real header.
                val nextHeader = findHeaderIndex(buffer, startIndex = 1)
                if (nextHeader > 0) {
                    buffer.subList(0, nextHeader).clear()
                    inSync = true
                } else {
                    val partialLen = trailingHeaderPrefixLength(buffer)
                    if (partialLen > 0) {
                        val dropCount = buffer.size - partialLen
                        if (dropCount > 0) {
                            buffer.subList(0, dropCount).clear()
                        }
                    } else {
                        buffer.clear()
                    }
                    inSync = false
                }
            }
        }

        return results
    }

    private fun resyncRemainingBuffer() {
        if (buffer.isEmpty()) {
            inSync = false
            return
        }
        val headerIdx = findHeaderIndex(buffer, startIndex = 0)
        if (headerIdx >= 0) {
            if (headerIdx > 0) {
                buffer.subList(0, headerIdx).clear()
            }
            inSync = true
            return
        }
        val partialLen = trailingHeaderPrefixLength(buffer)
        if (partialLen > 0) {
            val dropCount = buffer.size - partialLen
            if (dropCount > 0) {
                buffer.subList(0, dropCount).clear()
            }
        } else {
            buffer.clear()
        }
        inSync = false
    }

    private fun findHeaderIndex(bytes: List<Byte>, startIndex: Int): Int {
        val limit = bytes.size - 4
        if (startIndex > limit) return -1
        for (i in startIndex..limit) {
            if (bytes[i] == 0x55.toByte() &&
                bytes[i + 1] == 0xAA.toByte() &&
                bytes[i + 2] == 0xEB.toByte() &&
                bytes[i + 3] == 0x90.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    private fun trailingHeaderPrefixLength(bytes: List<Byte>): Int {
        val header = byteArrayOf(0x55.toByte(), 0xAA.toByte(), 0xEB.toByte(), 0x90.toByte())
        val maxCheck = minOf(3, bytes.size)
        for (len in maxCheck downTo 1) {
            var matched = true
            val start = bytes.size - len
            for (j in 0 until len) {
                if (bytes[start + j] != header[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return len
        }
        return 0
    }
}
