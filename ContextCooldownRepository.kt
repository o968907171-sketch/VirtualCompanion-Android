package com.example.virtualcompanion.data

import android.content.Context

class ContextCooldownRepository(context: Context, characterId: String) {
    private val prefs = context.getSharedPreferences("vc_context_cooldown_$characterId", Context.MODE_PRIVATE)

    fun allow(key: String, cooldownMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < cooldownMs) return false
        prefs.edit().putLong(key, now).apply()
        return true
    }

    fun clear() = prefs.edit().clear().apply()
}
