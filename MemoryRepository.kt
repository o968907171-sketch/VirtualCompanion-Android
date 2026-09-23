package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.MemoryItem
import org.json.JSONArray
import org.json.JSONObject

class MemoryRepository(context: Context, characterId: String) {
    private val prefs = context.getSharedPreferences("vc_memory_$characterId", Context.MODE_PRIVATE)
    private val maxItems = 2500

    fun add(item: MemoryItem) = addAll(listOf(item))
    fun addAll(newItems: List<MemoryItem>) {
        if (newItems.isEmpty()) return
        val merged = (load() + newItems).sortedBy { it.timestamp }.takeLast(maxItems)
        save(merged)
    }

    fun load(): List<MemoryItem> = try {
        val arr = JSONArray(prefs.getString("items", "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(MemoryItem(o.optLong("timestamp",0L),o.optString("speaker",""),o.optString("text",""),
                o.optString("emotion","平靜"),o.optString("source","local"),o.optString("conversationId","")))
        }}
    } catch (_: Exception) { emptyList() }

    fun clear() = prefs.edit().remove("items").apply()
    fun count() = load().size

    private fun save(items: List<MemoryItem>) {
        val arr=JSONArray(); items.forEach { arr.put(JSONObject().put("timestamp",it.timestamp).put("speaker",it.speaker)
            .put("text",it.text).put("emotion",it.emotion).put("source",it.source).put("conversationId",it.conversationId)) }
        prefs.edit().putString("items",arr.toString()).apply()
    }
}
