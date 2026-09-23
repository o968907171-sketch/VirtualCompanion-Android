package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.EmotionCatalog
import com.example.virtualcompanion.model.MemoryItem

data class DialogueReply(val text: String, val emotion: String)

object DialogueEngine {
    fun reply(userText: String, profile: CharacterProfile, memories: List<MemoryItem> = emptyList()): DialogueReply {
        if (memories.isNotEmpty() && listOf("記得", "之前", "以前", "上次", "聊過").any { userText.contains(it) }) {
            MemoryRecallEngine.recall(userText, memories)?.let { m ->
                return DialogueReply(applyCatchphrase("我記得。以前有提過：「${m.text.take(120)}」", profile), EmotionCatalog.CALM)
            }
        }
        val emotion = EmotionEngine.infer(userText, profile)
        val user = profile.userName.ifBlank { "你" }
        val raw = when (emotion) {
            EmotionCatalog.SAD -> "今天辛苦了。你想說的話，我會聽。"
            EmotionCatalog.TIRED -> if (userText.contains("睡") || userText.contains("晚安")) "好，去休息吧。晚安，$user。" else "看起來真的累了。先讓自己休息一下吧。"
            EmotionCatalog.AFRAID -> "我在。先不用急著一個人扛。"
            EmotionCatalog.ANGRY -> style(profile, "……我聽到了。", "喔？火氣不小嘛。", "哼。你最好解釋一下。")
            EmotionCatalog.DISGUSTED -> style(profile, "這個我也有點接受不了。", "……呃，認真的？", "這什麼啦……")
            EmotionCatalog.EMBARRASSED -> style(profile, "我也很在意你。", "……你突然講這個，我要怎麼接啦。", "才、才不是因為你這樣說我就開心。")
            EmotionCatalog.CLINGY -> "那就再待一下吧，$user。"
            EmotionCatalog.EXCITED -> "這很值得慶祝。做得漂亮，$user！"
            EmotionCatalog.HAPPY -> "看你這麼開心，我也被感染了。"
            EmotionCatalog.PUZZLED -> "嗯？這個我得想一下。你可以再多說一點。"
            else -> fallback(profile)
        }
        return DialogueReply(applyCatchphrase(raw, profile), emotion)
    }

    private fun fallback(p: CharacterProfile): String = when (p.speechStyle) {
        "溫柔" -> "嗯，我有在聽。慢慢說就好。"
        "傲嬌" -> "我只是剛好有空聽你說而已。"
        "毒舌" -> "喔？這就是你特地跑來跟我說的？"
        "活潑" -> "嘿，繼續啊！我還在聽。"
        "冷淡" -> "嗯。知道了。"
        "可愛" -> "嗯嗯，我有好好聽著喔。"
        else -> "嗯，我聽著。再說一點？"
    }

    private fun style(p: CharacterProfile, normal: String, sharp: String, tsun: String): String = when (p.speechStyle) {
        "毒舌" -> sharp
        "傲嬌" -> tsun
        else -> normal
    }

    private fun applyCatchphrase(text: String, p: CharacterProfile): String {
        val c = p.catchphrase.trim()
        return if (c.isNotEmpty() && text.length < 45) "$text $c" else text
    }
}
