package com.bmsjk.core.session

import com.bmsjk.core.protocol.DeviceInfo

interface PasscodeStore {
    fun isVerified(deviceAddress: String): Boolean
    fun getSavedPasscode(deviceAddress: String): String?
    fun markVerified(deviceAddress: String, passcode: String)
    fun clear(deviceAddress: String)
}

class InMemoryPasscodeStore : PasscodeStore {
    private val verified = mutableMapOf<String, String>()

    override fun isVerified(deviceAddress: String): Boolean =
        verified.containsKey(normalize(deviceAddress))

    override fun getSavedPasscode(deviceAddress: String): String? =
        verified[normalize(deviceAddress)]

    override fun markVerified(deviceAddress: String, passcode: String) {
        verified[normalize(deviceAddress)] = passcode
    }

    override fun clear(deviceAddress: String) {
        verified.remove(normalize(deviceAddress))
    }

    private fun normalize(address: String): String = address.trim().uppercase()
}

sealed interface PasscodeStatus {
    data object PendingDeviceInfo : PasscodeStatus
    data class Required(val deviceAddress: String, val deviceName: String) : PasscodeStatus
    data class Mismatch(val deviceAddress: String, val deviceName: String) : PasscodeStatus
    data class Unlocked(val deviceAddress: String, val autoUnlocked: Boolean) : PasscodeStatus
}

/**
 * First-time connection requires passcode matching DeviceInfo.devicePasscode (Frame 0x03 bytes 62..77).
 * Once verified for a BMS MAC address, subsequent connections unlock automatically.
 */
class PasscodePolicy(
    private val store: PasscodeStore
) {
    fun evaluateOnDeviceInfo(deviceAddress: String, info: DeviceInfo): PasscodeStatus {
        val expected = info.devicePasscode.trim()
        if (expected.isEmpty()) {
            return PasscodeStatus.Unlocked(deviceAddress, autoUnlocked = true)
        }

        val saved = store.getSavedPasscode(deviceAddress)?.trim()
        if (store.isVerified(deviceAddress) && saved == expected) {
            return PasscodeStatus.Unlocked(deviceAddress, autoUnlocked = true)
        }

        return PasscodeStatus.Required(deviceAddress, info.deviceName)
    }

    fun submitPasscode(
        deviceAddress: String,
        info: DeviceInfo,
        enteredPasscode: String
    ): PasscodeStatus {
        val entered = enteredPasscode.trim()
        val expected = info.devicePasscode.trim()
        return if (expected.isEmpty() || (entered.isNotEmpty() && entered == expected)) {
            store.markVerified(deviceAddress, expected)
            PasscodeStatus.Unlocked(deviceAddress, autoUnlocked = false)
        } else {
            PasscodeStatus.Mismatch(deviceAddress, info.deviceName)
        }
    }

    fun forgetDevice(deviceAddress: String) {
        store.clear(deviceAddress)
    }
}
