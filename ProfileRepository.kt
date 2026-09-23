package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.EmotionCatalog
import org.json.JSONArray

class ProfileRepository(private val context: Context, val characterId: String) {
    private val prefs = context.getSharedPreferences("vc_profile_$characterId", Context.MODE_PRIVATE)

    fun load(): CharacterProfile = CharacterProfile(
        characterId = characterId,
        userName = prefs.getString("userName", "") ?: "",
        characterName = prefs.getString("characterName", if (characterId == "main") "我的夥伴" else "新角色") ?: "我的夥伴",
        relationship = prefs.getString("relationship", "朋友") ?: "朋友",
        personality = prefs.getString("personality", "") ?: "",
        speechStyle = prefs.getString("speechStyle", "自然") ?: "自然",
        catchphrase = prefs.getString("catchphrase", "") ?: "",
        authorName = prefs.getString("authorName", "") ?: "",
        description = prefs.getString("description", "") ?: "",
        coverImageRef = prefs.getString("coverImageRef", "") ?: "",
        customBubbleImageRef = prefs.getString("customBubbleImageRef", "") ?: "",
        bubblePosition = prefs.getString("bubblePosition", "上方") ?: "上方",
        bubbleStyle = prefs.getString("bubbleStyle", "圓角") ?: "圓角",
        voiceEnabled = prefs.getBoolean("voiceEnabled", true),
        speechRate = prefs.getFloat("speechRate", 1.0f),
        speechPitch = prefs.getFloat("speechPitch", 1.0f),
        memoryEnabled = prefs.getBoolean("memoryEnabled", true),
        portraitSlotCount = prefs.getInt("portraitSlotCount", EmotionCatalog.MIN_PORTRAIT_SLOTS).coerceIn(EmotionCatalog.MIN_PORTRAIT_SLOTS, EmotionCatalog.MAX_PORTRAIT_SLOTS),
        characterScale = prefs.getFloat("characterScale", 1.0f).coerceIn(0.6f, 1.6f),
        batteryAwareness = prefs.getBoolean("batteryAwareness", false),
        headphoneAwareness = prefs.getBoolean("headphoneAwareness", false),
        musicAwareness = prefs.getBoolean("musicAwareness", false),
        notificationAwareness = prefs.getBoolean("notificationAwareness", false),
        notificationDetail = prefs.getString("notificationDetail", "只提醒") ?: "只提醒",
        aiEnabled = prefs.getBoolean("aiEnabled", false),
        aiProvider = prefs.getString("aiProvider", "本機") ?: "本機",
        aiModel = prefs.getString("aiModel", "gemini-3.8-flash") ?: "gemini-3.8-flash",
        aiEndpoint = prefs.getString("aiEndpoint", "") ?: "",
        visionEnabled = prefs.getBoolean("visionEnabled", false),
        longTermMemoryEnabled = prefs.getBoolean("longTermMemoryEnabled", true),
        ttsProvider = prefs.getString("ttsProvider", "Android TTS") ?: "Android TTS",
        ttsEndpoint = prefs.getString("ttsEndpoint", "") ?: "",
        ttsVoice = prefs.getString("ttsVoice", "") ?: "",
        createdAt = prefs.getLong("createdAt", System.currentTimeMillis())
    )

    fun save(p: CharacterProfile) {
        prefs.edit()
            .putString("userName", p.userName).putString("characterName", p.characterName)
            .putString("relationship", p.relationship).putString("personality", p.personality)
            .putString("speechStyle", p.speechStyle).putString("catchphrase", p.catchphrase)
            .putString("authorName", p.authorName).putString("description", p.description)
            .putString("coverImageRef", p.coverImageRef).putString("customBubbleImageRef", p.customBubbleImageRef)
            .putString("bubblePosition", p.bubblePosition).putString("bubbleStyle", p.bubbleStyle)
            .putBoolean("voiceEnabled", p.voiceEnabled).putFloat("speechRate", p.speechRate)
            .putFloat("speechPitch", p.speechPitch).putBoolean("memoryEnabled", p.memoryEnabled)
            .putInt("portraitSlotCount", p.portraitSlotCount.coerceIn(EmotionCatalog.MIN_PORTRAIT_SLOTS, EmotionCatalog.MAX_PORTRAIT_SLOTS))
            .putFloat("characterScale", p.characterScale.coerceIn(0.6f, 1.6f))
            .putBoolean("batteryAwareness", p.batteryAwareness)
            .putBoolean("headphoneAwareness", p.headphoneAwareness)
            .putBoolean("musicAwareness", p.musicAwareness)
            .putBoolean("notificationAwareness", p.notificationAwareness)
            .putString("notificationDetail", p.notificationDetail)
            .putBoolean("aiEnabled", p.aiEnabled)
            .putString("aiProvider", p.aiProvider)
            .putString("aiModel", p.aiModel)
            .putString("aiEndpoint", p.aiEndpoint)
            .putBoolean("visionEnabled", p.visionEnabled)
            .putBoolean("longTermMemoryEnabled", p.longTermMemoryEnabled)
            .putString("ttsProvider", p.ttsProvider)
            .putString("ttsEndpoint", p.ttsEndpoint)
            .putString("ttsVoice", p.ttsVoice)
            .putLong("createdAt", p.createdAt).apply()
    }

    fun setEmotionImage(emotion: String, uri: String) { prefs.edit().putString("emotionImage::$emotion", uri).apply() }
    fun emotionImage(emotion: String): String? = prefs.getString("emotionImage::$emotion", null)
    fun allEmotions(): List<String> {
        val slots = load().portraitSlotCount.coerceIn(EmotionCatalog.MIN_PORTRAIT_SLOTS, EmotionCatalog.MAX_PORTRAIT_SLOTS)
        val base = EmotionCatalog.builtIns.take(slots)
        val extra = customEmotions().filterNot { it in base }
        return (base + extra).distinct().take(EmotionCatalog.MAX_PORTRAIT_SLOTS)
    }

    fun setPortraitSlotCount(count: Int) {
        val p = load(); p.portraitSlotCount = count.coerceIn(EmotionCatalog.MIN_PORTRAIT_SLOTS, EmotionCatalog.MAX_PORTRAIT_SLOTS); save(p)
    }

    fun addCustomEmotion(name: String) {
        val clean = name.trim(); if (clean.isBlank() || clean in EmotionCatalog.builtIns) return
        if (allEmotions().size >= EmotionCatalog.MAX_PORTRAIT_SLOTS) return
        val list = customEmotions().toMutableList(); if (clean !in list) list += clean
        val arr = JSONArray(); list.forEach { arr.put(it) }
        prefs.edit().putString("customEmotions", arr.toString()).apply()
    }

    fun customEmotions(): List<String> = try {
        val arr = JSONArray(prefs.getString("customEmotions", "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    fun clearAll() { prefs.edit().clear().apply() }
}
