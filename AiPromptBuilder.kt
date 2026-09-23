package com.example.virtualcompanion.ai

import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.LongTermMemoryItem
import com.example.virtualcompanion.model.MemoryItem
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object AiPromptBuilder {
    fun system(profile: CharacterProfile, longTerm: List<LongTermMemoryItem>, vision: Boolean = false, socialContext: String = ""): String = buildString {
        append("你正在扮演使用者建立的虛擬角色。請自然維持角色，而不是解釋自己是模型。\n")
        append("角色名：${profile.characterName}\n")
        append("使用者名：${profile.userName.ifBlank { "使用者" }}\n")
        append("關係：${profile.relationship}\n")
        append("性格：${profile.personality.ifBlank { "自然、尊重使用者" }}\n")
        append("說話風格：${profile.speechStyle}\n")
        if (profile.catchphrase.isNotBlank()) append("口頭禪：${profile.catchphrase}\n")
        append("目前裝置當地時間：${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))}\n")
        append("預設使用繁體中文；若使用者明顯使用其他語言，可以跟隨對方語言。\n")
        append("回答要像手機桌寵自然說話，通常簡短到中等，不要每次都長篇大論。\n")
        append("不要捏造你沒有取得的手機資訊、位置、通知內容或現實事件。\n")
        if (vision) append("你這次確實收到一張圖片。只評論圖片中實際可辨識的內容；看不清楚就坦白說不確定。\n")
        if (socialContext.isNotBlank()) { append("\n多人社交情境：\n"); append(socialContext); append("\n") }
        if (longTerm.isNotEmpty()) {
            append("\n這些是此角色可參考的長期記憶，只在相關時自然使用，不要逐條念出：\n")
            longTerm.take(8).forEach { append("- ${it.summary}\n") }
        }
    }

    fun recentMessages(recent: List<MemoryItem>, characterName: String): List<Pair<String,String>> = recent.takeLast(18).map {
        val speaker = it.speaker.trim().lowercase()
        val assistantLabels = setOf(characterName.trim().lowercase(), "assistant", "ai", "model", "bot", "character", "角色", "助手")
        val role = if (speaker in assistantLabels || speaker.contains("assistant")) "assistant" else "user"
        role to it.text.take(1200)
    }
}
