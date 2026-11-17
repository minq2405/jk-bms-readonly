package com.bmsjk.app.data

import android.content.Context
import android.content.SharedPreferences
import com.bmsjk.core.session.PasscodeStore

enum class AppLanguage {
    VI,
    EN
}

/**
 * Persists verified JK BMS passcodes per normalized MAC address so subsequent
 * connections unlock automatically without re-prompting.
 */
class SharedPrefsPasscodeStore(context: Context) : PasscodeStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isVerified(deviceAddress: String): Boolean =
        prefs.contains(keyFor(deviceAddress))

    override fun getSavedPasscode(deviceAddress: String): String? =
        prefs.getString(keyFor(deviceAddress), null)

    override fun markVerified(deviceAddress: String, passcode: String) {
        prefs.edit().putString(keyFor(deviceAddress), passcode).apply()
    }

    override fun clear(deviceAddress: String) {
        prefs.edit().remove(keyFor(deviceAddress)).apply()
    }

    fun getLanguage(): AppLanguage {
        val code = prefs.getString(KEY_LANG, AppLanguage.VI.name)
        return runCatching { AppLanguage.valueOf(code ?: AppLanguage.VI.name) }
            .getOrDefault(AppLanguage.VI)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANG, language.name).apply()
    }

    private fun keyFor(address: String): String =
        KEY_PASS_PREFIX + address.trim().uppercase()

    private companion object {
        const val PREFS_NAME = "jk_bms_readonly_prefs"
        const val KEY_PASS_PREFIX = "pass_"
        const val KEY_LANG = "app_language"
    }
}
