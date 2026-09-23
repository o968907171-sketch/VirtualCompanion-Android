package com.example.virtualcompanion.data

import android.content.Context

/**
 * App-wide privacy/network switch. When strictOffline is enabled, cloud AI,
 * Vision and custom HTTP TTS must not issue network requests. Core companion
 * features continue to run locally.
 */
class OfflineSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vc_offline_settings", Context.MODE_PRIVATE)

    fun strictOffline(): Boolean = prefs.getBoolean("strictOffline", false)

    fun setStrictOffline(enabled: Boolean) {
        prefs.edit().putBoolean("strictOffline", enabled).apply()
    }
}
