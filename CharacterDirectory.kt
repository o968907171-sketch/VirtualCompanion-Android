package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.StickerItem
import com.example.virtualcompanion.model.VoiceClip
import org.json.JSONArray
import java.util.UUID

class CharacterDirectory(private val context: Context) {
    private val prefs = context.getSharedPreferences("vc_characters", Context.MODE_PRIVATE)

    init { ensureAtLeastOneCharacter() }

    fun ids(): List<String> {
        val raw = prefs.getString("ids", null)
        if (raw.isNullOrBlank()) return listOf(DEFAULT_ID)
        return try {
            val a = JSONArray(raw)
            buildList { for (i in 0 until a.length()) add(a.optString(i)) }.filter { it.isNotBlank() }
        } catch (_: Exception) { listOf(DEFAULT_ID) }
    }

    fun currentId(): String {
        val id = prefs.getString("current", null)
        return if (id != null && id in ids()) id else ids().first()
    }

    fun setCurrent(id: String) { if (id in ids()) prefs.edit().putString("current", id).apply() }

    fun create(displayName: String = "新角色"): String {
        val id = "char_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val list = ids().toMutableList().apply { add(id) }
        saveIds(list)
        ProfileRepository(context, id).save(CharacterProfile(characterId = id, characterName = displayName.ifBlank { "新角色" }))
        setCurrent(id)
        return id
    }

    fun duplicate(sourceId: String): String {
        val sourceRepo = ProfileRepository(context, sourceId)
        val source = sourceRepo.load()
        val id = create(source.characterName + " 副本")
        val targetRepo = ProfileRepository(context, id)
        val duplicate = source.copy(characterId = id, characterName = source.characterName + " 副本", createdAt = System.currentTimeMillis())
        duplicate.coverImageRef = copyRef(sourceId, id, "cover", source.coverImageRef)
        duplicate.customBubbleImageRef = copyRef(sourceId, id, "bubble", source.customBubbleImageRef)
        targetRepo.save(duplicate)

        sourceRepo.customEmotions().forEach { targetRepo.addCustomEmotion(it) }
        sourceRepo.allEmotions().forEach { emotion ->
            sourceRepo.emotionImage(emotion)?.let { ref ->
                targetRepo.setEmotionImage(emotion, CharacterAssetStore.duplicateReference(context, sourceId, id, emotion, ref))
            }
        }
        MemoryRepository(context, id).addAll(MemoryRepository(context, sourceId).load())
        LongTermMemoryRepository(context, id).addAll(LongTermMemoryRepository(context, sourceId).load())

        val targetStickers = StickerRepository(context, id)
        StickerRepository(context, sourceId).load().forEach { item ->
            when (item.type) {
                StickerItem.Type.KAOMOJI -> targetStickers.addKaomoji(item.value, item.label)
                StickerItem.Type.IMAGE -> targetStickers.addImage(item.label, CharacterAssetStore.duplicateReference(context, sourceId, id, "sticker_${item.id}", item.value))
            }
        }

        val targetVoice = VoiceClipRepository(context, id)
        VoiceClipRepository(context, sourceId).load().forEach { clip ->
            val copied = CharacterAssetStore.duplicateReference(context, sourceId, id, "voice_${clip.id}", clip.ref)
            targetVoice.add(VoiceClip("dup_${clip.id}", clip.label, clip.usage, copied))
        }
        return id
    }

    private fun copyRef(sourceId: String, targetId: String, key: String, ref: String): String {
        if (ref.isBlank()) return ""
        return CharacterAssetStore.duplicateReference(context, sourceId, targetId, key, ref)
    }

    fun delete(id: String): Boolean {
        val list = ids().toMutableList()
        if (list.size <= 1 || id !in list) return false
        val wasCurrent = currentId() == id
        list.remove(id); saveIds(list)
        ProfileRepository(context, id).clearAll()
        StateRepository(context, id).clear()
        MemoryRepository(context, id).clear()
        LongTermMemoryRepository(context, id).clear()
        StickerRepository(context, id).clear()
        VoiceClipRepository(context, id).clear()
        ContextCooldownRepository(context, id).clear()
        CharacterAssetStore.clear(context, id)
        SocialRelationshipRepository(context).clearCharacter(id)
        GroupChatRepository(context).removeCharacter(id)
        setActive(id, false)
        if (wasCurrent) setCurrent(list.first())
        return true
    }

    fun activeIds(): Set<String> {
        val raw = prefs.getStringSet("active", emptySet()) ?: emptySet()
        return raw.intersect(ids().toSet())
    }
    fun isActive(id: String) = id in activeIds()
    fun setActive(id: String, active: Boolean) {
        val set = activeIds().toMutableSet(); if (active) set += id else set -= id
        prefs.edit().putStringSet("active", set).apply()
    }

    private fun ensureAtLeastOneCharacter() {
        if (prefs.getString("ids", null) == null) {
            saveIds(listOf(DEFAULT_ID)); prefs.edit().putString("current", DEFAULT_ID).apply(); migrateLegacyProfile()
        }
    }

    private fun migrateLegacyProfile() {
        val legacy = context.getSharedPreferences("vc_profile", Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        val target = ProfileRepository(context, DEFAULT_ID)
        val p = target.load().apply {
            userName = legacy.getString("userName", "") ?: ""
            characterName = legacy.getString("characterName", "我的夥伴") ?: "我的夥伴"
            relationship = legacy.getString("relationship", "朋友") ?: "朋友"
            personality = legacy.getString("personality", "") ?: ""
            speechStyle = legacy.getString("speechStyle", "自然") ?: "自然"
            catchphrase = legacy.getString("catchphrase", "") ?: ""
            bubblePosition = legacy.getString("bubblePosition", "上方") ?: "上方"
            bubbleStyle = legacy.getString("bubbleStyle", "圓角") ?: "圓角"
            voiceEnabled = legacy.getBoolean("voiceEnabled", true)
            speechRate = legacy.getFloat("speechRate", 1f)
            speechPitch = legacy.getFloat("speechPitch", 1f)
            memoryEnabled = legacy.getBoolean("memoryEnabled", true)
        }
        target.save(p)
        legacy.all.forEach { (k,v) -> if (k.startsWith("emotionImage::") && v is String) target.setEmotionImage(k.removePrefix("emotionImage::"), v) }
    }

    private fun saveIds(ids: List<String>) {
        val a = JSONArray(); ids.distinct().forEach { a.put(it) }
        prefs.edit().putString("ids", a.toString()).apply()
    }

    companion object { const val DEFAULT_ID = "main" }
}
