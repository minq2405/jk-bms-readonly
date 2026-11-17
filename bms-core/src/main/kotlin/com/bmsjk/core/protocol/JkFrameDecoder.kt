package com.bmsjk.core.protocol

/**
 * Stateful frame decoder that handles AUTO protocol resolution across 0x03 -> 0x01/0x02.
 */
class JkFrameDecoder(
    initialMode: ProtocolMode = ProtocolMode.AUTO
) {
    var mode: ProtocolMode = initialMode
        set(value) {
            field = value
            if (value != ProtocolMode.AUTO) {
                detectedVariant = when (value) {
                    ProtocolMode.JK02_32S -> ProtocolVariant.JK02_32S
                    ProtocolMode.JK02_24S -> ProtocolVariant.JK02_24S
                    ProtocolMode.JK04 -> ProtocolVariant.JK04
                    ProtocolMode.AUTO -> null
                }
            } else {
                detectedVariant = null
            }
        }

    var detectedVariant: ProtocolVariant? = null
        private set

    fun activeVariantOrNull(): ProtocolVariant? = when (mode) {
        ProtocolMode.AUTO -> detectedVariant
        ProtocolMode.JK02_32S -> ProtocolVariant.JK02_32S
        ProtocolMode.JK02_24S -> ProtocolVariant.JK02_24S
        ProtocolMode.JK04 -> ProtocolVariant.JK04
    }

    fun resetAutoDetection() {
        if (mode == ProtocolMode.AUTO) {
            detectedVariant = null
        }
    }

    fun decode(frame: ByteArray, timestampMs: Long = System.currentTimeMillis()): DecodedFrame {
        require(JkFrameCodec.validateResponseFrame(frame)) {
            "Invalid JK BMS response frame (header/length/CRC check failed)"
        }

        val frameType = ByteReader.u8(frame, 4)
        return when (frameType) {
            JkFrameCodec.FRAME_TYPE_DEVICE_INFO -> {
                val info = Jk02Parser.parseDeviceInfo(frame)
                if (mode == ProtocolMode.AUTO) {
                    detectedVariant = ProtocolAutoDetector.detectFromDeviceInfo(info)
                }
                DecodedFrame.DeviceInfoFrame(info)
            }

            JkFrameCodec.FRAME_TYPE_CELL_INFO -> {
                val variant = activeVariantOrNull() ?: ProtocolAutoDetector.detectFromCellFrame(frame).also {
                    if (mode == ProtocolMode.AUTO) detectedVariant = it
                }
                val telemetry = if (variant == ProtocolVariant.JK04) {
                    Jk04Parser.parseCellInfo(frame, timestampMs)
                } else {
                    Jk02Parser.parseCellInfo(frame, variant, timestampMs)
                }
                DecodedFrame.CellInfoFrame(telemetry)
            }

            JkFrameCodec.FRAME_TYPE_SETTINGS -> {
                val variant = activeVariantOrNull() ?: ProtocolVariant.JK02_32S
                val settings = if (variant == ProtocolVariant.JK04) {
                    Jk04Parser.parseSettings(frame)
                } else {
                    Jk02Parser.parseSettings(frame, variant)
                }
                DecodedFrame.SettingsFrame(settings)
            }

            else -> DecodedFrame.UnsupportedFrame(frameType, frame.size)
        }
    }
}
