package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.StickerItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class StickerRepository(context: Context, characterId: String) {
    private val prefs = context.getSharedPreferences("vc_stickers_$characterId", Context.MODE_PRIVATE)

    fun load(): List<StickerItem> = try {
        val arr = JSONArray(prefs.getString("items", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = runCatching { StickerItem.Type.valueOf(o.optString("type")) }.getOrNull() ?: continue
                val value = o.optString("value")
                if (value.isBlank()) continue
                add(StickerItem(o.optString("id", UUID.randomUUID().toString()), o.optString("label", "貼圖"), type, value))
            }
        }
    } catch (_: Exception) { emptyList() }

    fun addImage(label: String, ref: String): Boolean {
        if (load().size >= MAX_ITEMS || ref.isBlank()) return false
        return add(StickerItem(newId(), label.trim().ifBlank { "圖片貼圖" }.take(80), StickerItem.Type.IMAGE, ref))
    }

    fun addKaomoji(text: String, label: String = "顏文字"): Boolean {
        val clean = text.trim().take(200)
        if (clean.isBlank() || load().size >= MAX_ITEMS) return false
        return add(StickerItem(newId(), label.trim().ifBlank { clean }.take(80), StickerItem.Type.KAOMOJI, clean))
    }

    fun add(item: StickerItem): Boolean {
        val items = load().toMutableList()
        if (items.size >= MAX_ITEMS) return false
        items += item
        save(items)
        return true
    }

    fun remove(id: String) { save(load().filterNot { it.id == id }) }
    fun clear() = prefs.edit().clear().apply()

    private fun save(items: List<StickerItem>) {
        val arr = JSONArray()
        items.take(MAX_ITEMS).forEach { s ->
            arr.put(JSONObject().put("id", s.id).put("label", s.label).put("type", s.type.name).put("value", s.value))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun newId() = "st_" + UUID.randomUUID().toString().replace("-", "").take(12)

    companion object { const val MAX_ITEMS = 64 }
}
