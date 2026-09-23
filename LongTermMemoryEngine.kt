package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.LongTermMemoryItem
import com.example.virtualcompanion.model.MemoryItem
import java.util.UUID

object LongTermMemoryEngine {
    private val importantSignals = listOf(
        "我喜歡", "我最喜歡", "我討厭", "我不喜歡", "我的生日", "我生日", "我叫", "我的名字",
        "我住", "我的家", "我的工作", "我在讀", "我的學校", "我的朋友", "我的家人", "我的寵物",
        "明天", "下週", "下礼拜", "下禮拜", "下個月", "我要考試", "要考試", "面試", "約會", "紀念日",
        "請記得", "記住", "對我很重要"
    )
    private val stop = setOf("我","你","他","她","它","我們","你們","的","了","是","有","在","會","要","很","也","都","就","跟","和","或","這","那","一個")

    fun extractCandidate(text: String, source: String = "local"): LongTermMemoryItem? {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 6 || importantSignals.none { clean.contains(it, ignoreCase = true) }) return null
        val importance = when {
            listOf("生日","紀念日","對我很重要","請記得","記住").any { clean.contains(it) } -> 5
            listOf("考試","面試","明天","下週","下禮拜","下個月","我的名字","我叫").any { clean.contains(it) } -> 4
            else -> 3
        }
        return LongTermMemoryItem(
            id = UUID.randomUUID().toString(),
            summary = clean.take(260),
            tags = keywords(clean).take(12),
            importance = importance,
            source = source
        )
    }

    fun consolidate(memories: List<MemoryItem>, characterName: String): List<LongTermMemoryItem> {
        val aiLabels = setOf(characterName.trim().lowercase(), "assistant", "ai", "model", "bot", "character", "角色", "助手")
        return memories
        .filter { m -> val s = m.speaker.trim().lowercase(); s !in aiLabels && !s.contains("assistant") }
        .mapNotNull { extractCandidate(it.text, it.source) }
        .distinctBy { it.summary.lowercase() }
        .takeLast(120)
    }

    fun recall(query: String, items: List<LongTermMemoryItem>, limit: Int = 8): List<LongTermMemoryItem> {
        if (items.isEmpty()) return emptyList()
        val q = keywords(query).toSet()
        return items.map { item ->
            val hit = if (q.isEmpty()) 0 else item.tags.count { it in q }
            val textHit = if (q.isEmpty()) 0 else q.count { item.summary.contains(it, ignoreCase = true) }
            val score = hit * 4 + textHit * 3 + item.importance * 2 + if (System.currentTimeMillis() - item.lastUsedAt < 7L * 24 * 3600_000) 1 else 0
            item to score
        }.sortedWith(compareByDescending<Pair<LongTermMemoryItem,Int>> { it.second }.thenByDescending { it.first.importance })
            .filter { it.second > 0 || q.isEmpty() }
            .take(limit).map { it.first }
    }

    private fun keywords(text: String): List<String> = text
        .split(Regex("[\\s，。！？、,.!?：:；;()（）\\[\\]{}]+"))
        .map { it.trim().lowercase() }
        .filter { it.length >= 2 && it !in stop }
        .distinct()
}
