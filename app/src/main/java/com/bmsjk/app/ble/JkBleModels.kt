package com.bmsjk.app.ble

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
    data class Error(val message: String) : BleConnectionState
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
