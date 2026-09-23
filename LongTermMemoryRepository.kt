package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.LongTermMemoryItem
import org.json.JSONArray
import org.json.JSONObject

class LongTermMemoryRepository(context: Context, characterId: String) {
    private val prefs = context.getSharedPreferences("vc_long_memory_$characterId", Context.MODE_PRIVATE)
    private val maxItems = 400

    fun load(): List<LongTermMemoryItem> = try {
        val arr = JSONArray(prefs.getString("items", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tagsJson = o.optJSONArray("tags") ?: JSONArray()
                val tags = buildList { for (j in 0 until tagsJson.length()) add(tagsJson.optString(j)) }.filter { it.isNotBlank() }
                add(LongTermMemoryItem(
                    id = o.optString("id"),
                    summary = o.optString("summary"),
                    tags = tags,
                    importance = o.optInt("importance", 3).coerceIn(1, 5),
                    createdAt = o.optLong("createdAt", 0L),
                    lastUsedAt = o.optLong("lastUsedAt", 0L),
                    source = o.optString("source", "local")
                ))
            }
        }.filter { it.id.isNotBlank() && it.summary.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    fun addOrMerge(item: LongTermMemoryItem) {
        val items = load().toMutableList()
        val normalized = normalize(item.summary)
        val index = items.indexOfFirst { normalize(it.summary) == normalized || overlap(it.tags, item.tags) >= 0.75 }
        if (index >= 0) {
            val old = items[index]
            items[index] = old.copy(
                summary = if (item.summary.length > old.summary.length) item.summary else old.summary,
                tags = (old.tags + item.tags).distinct().take(16),
                importance = maxOf(old.importance, item.importance),
                lastUsedAt = System.currentTimeMillis()
            )
        } else items += item
        save(items.sortedWith(compareByDescending<LongTermMemoryItem> { it.importance }.thenByDescending { it.lastUsedAt }).take(maxItems))
    }

    fun addAll(items: List<LongTermMemoryItem>) = items.forEach(::addOrMerge)

    fun touch(id: String) {
        val items = load().map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
        save(items)
    }

    fun clear() = prefs.edit().remove("items").apply()
    fun count() = load().size

    private fun save(items: List<LongTermMemoryItem>) {
        val arr = JSONArray()
        items.forEach { m ->
            val tags = JSONArray(); m.tags.forEach { tags.put(it) }
            arr.put(JSONObject()
                .put("id", m.id).put("summary", m.summary).put("tags", tags)
                .put("importance", m.importance).put("createdAt", m.createdAt)
                .put("lastUsedAt", m.lastUsedAt).put("source", m.source))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[\\s，。！？、,.!?：:；;()（）]+"), "")
    private fun overlap(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val sa = a.toSet(); val sb = b.toSet(); val union = (sa + sb).size.coerceAtLeast(1)
        return sa.intersect(sb).size.toDouble() / union.toDouble()
    }
}
