package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.VoiceClip
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class VoiceClipRepository(context: Context, characterId: String) {
    private val prefs = context.getSharedPreferences("vc_voiceclips_$characterId", Context.MODE_PRIVATE)

    fun load(): List<VoiceClip> = try {
        val arr = JSONArray(prefs.getString("items", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ref = o.optString("ref")
                if (ref.isBlank()) continue
                add(VoiceClip(
                    id = o.optString("id", newId()),
                    label = o.optString("label", "聲音"),
                    usage = o.optString("usage", "通用"),
                    ref = ref
                ))
            }
        }
    } catch (_: Exception) { emptyList() }

    fun add(label: String, usage: String, ref: String): Boolean {
        if (ref.isBlank()) return false
        val items = load().toMutableList()
        if (items.size >= MAX_ITEMS) return false
        items += VoiceClip(newId(), label.trim().ifBlank { "聲音" }.take(80), usage.trim().ifBlank { "通用" }.take(40), ref)
        save(items)
        return true
    }

    fun add(item: VoiceClip): Boolean {
        val items = load().toMutableList()
        if (items.size >= MAX_ITEMS) return false
        items += item
        save(items)
        return true
    }

    fun remove(id: String) = save(load().filterNot { it.id == id })
    fun clear() = prefs.edit().clear().apply()

    private fun save(items: List<VoiceClip>) {
        val arr = JSONArray()
        items.take(MAX_ITEMS).forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("label", c.label).put("usage", c.usage).put("ref", c.ref))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun newId() = "vc_" + UUID.randomUUID().toString().replace("-", "").take(12)

    companion object {
        const val MAX_ITEMS = 24
        val USAGES = listOf("通用", "點擊", "長按", "睡覺", "醒來", "通知", "音樂", "低電量")
    }
}
