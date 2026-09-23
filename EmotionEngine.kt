package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.EmotionCatalog

object EmotionEngine {
    fun infer(userText: String, profile: CharacterProfile): String {
        val t = userText.lowercase()
        return when {
            containsAny(t, "睡", "晚安", "sleep", "good night") -> EmotionCatalog.TIRED
            containsAny(t, "累", "疲倦", "睏", "tired", "exhausted") -> EmotionCatalog.TIRED
            containsAny(t, "難過", "傷心", "哭", "sad", "cry") -> EmotionCatalog.SAD
            containsAny(t, "怕", "害怕", "恐怖", "scared", "afraid") -> EmotionCatalog.AFRAID
            containsAny(t, "討厭", "噁", "嫌棄", "gross", "disgust") -> EmotionCatalog.DISGUSTED
            containsAny(t, "生氣", "煩", "閉嘴", "angry", "mad") -> EmotionCatalog.ANGRY
            containsAny(t, "什麼", "為什麼", "?", "？", "why", "what") -> EmotionCatalog.PUZZLED
            containsAny(t, "想你", "愛你", "喜歡你", "love you", "miss you") && isRomantic(profile) -> EmotionCatalog.EMBARRASSED
            containsAny(t, "抱抱", "陪我", "hug", "stay with me") && isRomantic(profile) -> EmotionCatalog.CLINGY
            containsAny(t, "成功", "滿分", "贏了", "太好了", "yay", "won") -> EmotionCatalog.EXCITED
            containsAny(t, "哈哈", "開心", "高興", "happy", "lol", "haha") -> EmotionCatalog.HAPPY
            else -> EmotionCatalog.CALM
        }
    }

    fun decayedEmotion(current: String, since: Long, sleeping: Boolean): String {
        if (sleeping) return EmotionCatalog.SLEEPING
        val age = System.currentTimeMillis() - since
        return if (age > 10 * 60 * 1000L) EmotionCatalog.CALM else current
    }

    private fun containsAny(text: String, vararg words: String) = words.any { text.contains(it) }
    private fun isRomantic(p: CharacterProfile) = p.relationship.contains("戀") || p.relationship.contains("情侶") || p.relationship.contains("伴侶")
}
