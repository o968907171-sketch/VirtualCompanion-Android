package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.EmotionCatalog

/**
 * v0.4.x 的本機照片反應器。
 * 現階段不假裝具有真正影像理解；只負責「收到照片/貼圖」後依人格回應。
 * v0.6 可在這個介面後接可選的 Vision provider。
 */
object PhotoReactionEngine {
    data class Reaction(val text: String, val emotion: String)

    fun photo(profile: CharacterProfile): Reaction {
        val name = profile.userName.ifBlank { "你" }
        val text = when (profile.speechStyle) {
            "毒舌" -> "嗯？特地拿照片給我看？……行，我收到了。"
            "傲嬌" -> "才、才不是我想看，是${name}自己拿過來的。"
            "溫柔" -> "我看到你給我的照片了。謝謝你想到要拿給我看。"
            "活潑" -> "喔！照片！拿近一點，我有興趣。"
            "冷淡" -> "看到了。這張我先記住。"
            else -> "我看到你給我的照片了。挺有意思的。"
        }
        return Reaction(text, EmotionCatalog.CURIOUS)
    }

    fun sticker(profile: CharacterProfile): Reaction {
        val text = when (profile.speechStyle) {
            "毒舌" -> "拿貼圖砸我？幼不幼稚……再來一張。"
            "傲嬌" -> "這、這貼圖又不是特地給我看的吧？"
            "溫柔" -> "這張小貼圖很可愛。"
            "活潑" -> "嘿，貼圖收到！"
            else -> "嗯？給我的小貼圖？收到。"
        }
        return Reaction(text, EmotionCatalog.HAPPY)
    }
}
