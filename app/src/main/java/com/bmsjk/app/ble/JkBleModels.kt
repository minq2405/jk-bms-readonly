package com.bmsjk.app.ble

import com.bmsjk.core.protocol.JkFrameCodec
import java.util.UUID

data class BleDeviceItem(
    val address: String,
    val name: String,
    val rssi: Int,
    val hasJkService: Boolean
)

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data class Connecting(val address: String, val name: String) : BleConnectionState
    data class Discovering(val address: String, val name: String) : BleConnectionState
    data class Connected(val address: String, val name: String) : BleConnectionState
    data class ConnectionLost(val address: String, val name: String, val reason: String) : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

/**
 * Strictly filters BLE scan results to Jikong (JK) BMS devices so unrelated
 * peripherals (watches, headphones, TVs, phones) are excluded from the Connect list.
 */
object JkBleFilter {
    fun isJkBmsDevice(name: String?, serviceUuids: List<UUID>): Boolean {
        if (serviceUuids.any { it == JkFrameCodec.SERVICE_UUID }) {
            return true
        }
        val cleanName = name?.trim().orEmpty()
        if (cleanName.isEmpty()) return false
        return cleanName.startsWith("JK", ignoreCase = true) ||
            cleanName.contains("JKBMS", ignoreCase = true) ||
            cleanName.contains("JK-B", ignoreCase = true) ||
            cleanName.contains("JK_B", ignoreCase = true) ||
            cleanName.contains("JK_", ignoreCase = true)
    }
}

/**
 * Extracts a normalized MAC address (AA:BB:CC:DD:EE:FF) from raw QR text, URL, or JSON.
 */
object MacPayloadParser {
    private val COLON_OR_DASH_MAC = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
    private val COMPACT_MAC = Regex("\\b[0-9A-Fa-f]{12}\\b")

    fun extractMac(raw: String): String? {
        val trimmed = raw.trim()
        COLON_OR_DASH_MAC.find(trimmed)?.let { match ->
            return match.value.replace('-', ':').uppercase()
        }
        COMPACT_MAC.find(trimmed)?.let { match ->
            return match.value.uppercase().chunked(2).joinToString(":")
        }
        return null
    }
}
