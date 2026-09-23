package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.EmotionCatalog
import com.example.virtualcompanion.model.GroupMessage
import com.example.virtualcompanion.model.SocialRelationship

object GroupDialogueEngine {
    fun reply(self: CharacterProfile, userText: String, others: List<Pair<CharacterProfile, SocialRelationship>>, recent: List<GroupMessage>, dramaLevel: Int): DialogueReply {
        val jealous = others.maxByOrNull { it.second.jealousy }
        val irritated = others.maxByOrNull { it.second.irritation }
        val lastOther = recent.lastOrNull { it.speakerId != "user" && it.speakerId != self.characterId }

        if (dramaLevel > 0 && jealous != null && jealous.second.jealousy >= 60 && userText.contains(jealous.first.characterName, true)) {
            val t = when(self.speechStyle) {
                "傲嬌" -> "……你今天一直在叫 ${jealous.first.characterName} 的名字。隨便啦，我才沒有在意。"
                "毒舌" -> "喔，又是 ${jealous.first.characterName}。你今天的注意力分配還真偏心。"
                else -> "你跟 ${jealous.first.characterName} 好像聊得很開心。……我有一點在意。"
            }
            return DialogueReply(t, EmotionCatalog.EMBARRASSED)
        }
        if (dramaLevel >= 2 && irritated != null && irritated.second.irritation >= 70 && lastOther != null) {
            return DialogueReply("${lastOther.speakerName}，你剛剛那句我可不同意。", EmotionCatalog.ANGRY)
        }
        if (lastOther != null && lastOther.text.length < 180) {
            val base = when(self.speechStyle) {
                "活潑" -> "欸，我也想接這個。${lastOther.speakerName} 剛剛那句有點意思。"
                "毒舌" -> "${lastOther.speakerName} 說完了？那換我。"
                "冷淡" -> "……我聽到了。我的看法不太一樣。"
                else -> "我也有在聽 ${lastOther.speakerName} 說。讓我補一句。"
            }
            return DialogueReply(base, EmotionCatalog.PUZZLED)
        }
        return DialogueEngine.reply(userText, self)
    }
}
