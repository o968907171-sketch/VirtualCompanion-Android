package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.CharacterState
import com.example.virtualcompanion.model.EmotionCatalog

class StateRepository(context: Context, characterId: String) {
    private val prefs = context.getSharedPreferences("vc_state_$characterId", Context.MODE_PRIVATE)
    fun load(): CharacterState = CharacterState(
        currentEmotion = prefs.getString("emotion", EmotionCatalog.CALM) ?: EmotionCatalog.CALM,
        emotionSince = prefs.getLong("emotionSince", System.currentTimeMillis()),
        sleeping = prefs.getBoolean("sleeping", false),
        overlayX = prefs.getInt("overlayX", 24), overlayY = prefs.getInt("overlayY", 80),
        lastInteraction = prefs.getLong("lastInteraction", System.currentTimeMillis())
    )
    fun save(s: CharacterState) { prefs.edit().putString("emotion",s.currentEmotion).putLong("emotionSince",s.emotionSince)
        .putBoolean("sleeping",s.sleeping).putInt("overlayX",s.overlayX).putInt("overlayY",s.overlayY)
        .putLong("lastInteraction",s.lastInteraction).apply() }
    fun clear() = prefs.edit().clear().apply()
}
