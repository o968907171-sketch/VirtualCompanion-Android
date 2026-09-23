package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.SocialRelationship
import org.json.JSONObject

class SocialRelationshipRepository(context: Context) {
    private val prefs = context.getSharedPreferences("vc_social_relationships", Context.MODE_PRIVATE)

    fun get(sourceId: String, targetId: String): SocialRelationship {
        if (sourceId == targetId) return SocialRelationship(sourceId, targetId)
        val raw = prefs.getString(key(sourceId, targetId), null) ?: return SocialRelationship(sourceId, targetId)
        return try {
            val o = JSONObject(raw)
            SocialRelationship(
                sourceCharacterId = sourceId,
                targetCharacterId = targetId,
                affinity = o.optInt("affinity", 0),
                trust = o.optInt("trust", 0),
                attachment = o.optInt("attachment", 0),
                jealousy = o.optInt("jealousy", 0),
                rivalry = o.optInt("rivalry", 0),
                irritation = o.optInt("irritation", 0),
                lastEvent = o.optString("lastEvent", ""),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            ).normalize()
        } catch (_: Exception) { SocialRelationship(sourceId, targetId) }
    }

    fun save(r: SocialRelationship) {
        if (r.sourceCharacterId == r.targetCharacterId) return
        r.normalize()
        val o = JSONObject()
            .put("affinity", r.affinity)
            .put("trust", r.trust)
            .put("attachment", r.attachment)
            .put("jealousy", r.jealousy)
            .put("rivalry", r.rivalry)
            .put("irritation", r.irritation)
            .put("lastEvent", r.lastEvent)
            .put("updatedAt", r.updatedAt)
        prefs.edit().putString(key(r.sourceCharacterId, r.targetCharacterId), o.toString()).apply()
    }

    fun adjust(sourceId: String, targetId: String, affinity: Int = 0, trust: Int = 0, attachment: Int = 0, jealousy: Int = 0, rivalry: Int = 0, irritation: Int = 0, event: String = ""): SocialRelationship {
        val r = get(sourceId, targetId)
        r.affinity += affinity; r.trust += trust; r.attachment += attachment
        r.jealousy += jealousy; r.rivalry += rivalry; r.irritation += irritation
        if (event.isNotBlank()) r.lastEvent = event
        save(r)
        return r
    }

    fun clearCharacter(characterId: String) {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$characterId->") || it.endsWith("->$characterId") }.forEach { e.remove(it) }
        e.apply()
    }

    private fun key(a: String, b: String) = "$a->$b"
}
