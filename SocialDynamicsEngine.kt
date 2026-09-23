package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.GroupMessage
import com.example.virtualcompanion.model.SocialRelationship

object SocialDynamicsEngine {
    data class Delta(val targetId: String, val affinity: Int = 0, val trust: Int = 0, val attachment: Int = 0, val jealousy: Int = 0, val rivalry: Int = 0, val irritation: Int = 0, val event: String = "")

    fun onUserMessage(self: CharacterProfile, others: List<CharacterProfile>, text: String, dramaLevel: Int): List<Delta> {
        val lower = text.lowercase()
        if (listOf("別吵", "不要吵", "和好", "都乖", "別生氣").any { lower.contains(it) }) {
            return others.map { Delta(it.characterId, affinity = 2, trust = 2, jealousy = -8, rivalry = -5, irritation = -14, event = "玩家勸和") }
        }
        val affectionate = listOf("喜歡", "愛你", "最喜歡", "抱抱", "親親", "想你").any { lower.contains(it) }
        if (!affectionate || dramaLevel <= 0) return emptyList()
        val mentioned = others.filter { o -> text.contains(o.characterName, ignoreCase = true) }
        if (mentioned.isEmpty()) return emptyList()
        return mentioned.map { target ->
            val scale = if (dramaLevel >= 3) 12 else if (dramaLevel == 2) 8 else 4
            Delta(target.characterId, jealousy = scale, rivalry = scale / 2, irritation = scale / 3, event = "玩家對 ${target.characterName} 表現親密")
        }
    }

    fun onCharacterMessage(selfId: String, otherId: String, text: String): Delta? {
        val sharp = listOf("閉嘴", "少來", "別搶", "煩", "不爽", "胡說", "才不是", "讓開").any { text.contains(it) }
        val warm = listOf("謝謝", "同意", "沒事", "一起", "放心", "對啊", "你說得對").any { text.contains(it) }
        return when {
            sharp -> Delta(otherId, affinity = -3, trust = -1, rivalry = 4, irritation = 8, event = "群聊爭執")
            warm -> Delta(otherId, affinity = 4, trust = 3, rivalry = -2, irritation = -4, event = "群聊互動良好")
            else -> null
        }
    }

    fun context(self: CharacterProfile, others: List<Pair<CharacterProfile, SocialRelationship>>, recent: List<GroupMessage>, dramaLevel: Int): String = buildString {
        append("你正在多人聊天室。你只能扮演 ${self.characterName}，不要替其他角色說話。\n")
        append("群聊衝突強度：${when(dramaLevel){0->"和平";1->"輕微";2->"自然";else->"戲劇化"}}。不要無緣無故吵架。\n")
        if (others.isNotEmpty()) {
            append("你對其他角色目前的感受：\n")
            others.forEach { (p,r) -> append("- ${p.characterName}: ${r.dominantFeeling()}；好感${r.affinity}、信任${r.trust}、依戀${r.attachment}、吃醋${r.jealousy}、競爭${r.rivalry}、不滿${r.irritation}\n") }
        }
        if (recent.isNotEmpty()) {
            append("最近群聊：\n")
            recent.takeLast(10).forEach { append("${it.speakerName}: ${it.text.take(220)}\n") }
        }
        append("你可以接別人的話、吐槽、吃醋、調停或沉默；是否衝突要符合上述關係狀態。玩家是聊天室中的真人，不要替玩家發言。")
    }
}
