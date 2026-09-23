package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.MemoryItem

object MemoryRecallEngine {
    private val stop = setOf("我","你","他","她","它","的","了","是","有","在","嗎","呢","啊","吧","跟","和","想","說","這","那")
    fun recall(query:String, memories:List<MemoryItem>):MemoryItem? {
        val tokens=query.split(Regex("[\\s，。！？、,.!?：:；;()（）]+" )).map{it.trim()}.filter{it.length>=2 && it !in stop}
        if(tokens.isEmpty()) return memories.lastOrNull()
        return memories.asReversed().maxByOrNull { m -> tokens.count { t -> m.text.contains(t,ignoreCase=true) } }
            ?.takeIf { m -> tokens.any { t -> m.text.contains(t,ignoreCase=true) } }
    }
}
