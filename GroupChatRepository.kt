package com.example.virtualcompanion.data

import android.content.Context
import com.example.virtualcompanion.model.GroupMessage
import com.example.virtualcompanion.model.GroupRoom
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class GroupChatRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("vc_group_chats", Context.MODE_PRIVATE)

    fun rooms(): List<GroupRoom> = try {
        val arr = JSONArray(prefs.getString("rooms", "[]") ?: "[]")
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("participantIds") ?: JSONArray()
                add(GroupRoom(
                    roomId = o.optString("roomId"),
                    name = o.optString("name", "群聊"),
                    participantIds = buildList { for (j in 0 until ids.length()) add(ids.optString(j)) }.filter { it.isNotBlank() },
                    dramaLevel = o.optInt("dramaLevel", 2).coerceIn(0, 3),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                ))
            }
        }
    } catch (_: Exception) { emptyList() }

    fun room(id: String): GroupRoom? = rooms().firstOrNull { it.roomId == id }

    fun create(name: String, participants: List<String>, dramaLevel: Int = 2): GroupRoom {
        require(participants.distinct().size >= 2) { "群聊至少需要兩隻角色" }
        val r = GroupRoom("room_" + UUID.randomUUID().toString().replace("-", "").take(12), name.ifBlank { "多人聊天室" }, participants.distinct(), dramaLevel.coerceIn(0,3))
        saveRoom(r); return r
    }

    fun saveRoom(room: GroupRoom) {
        val list = rooms().filterNot { it.roomId == room.roomId }.toMutableList().apply { add(room) }
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().put("roomId", r.roomId).put("name", r.name).put("participantIds", JSONArray(r.participantIds)).put("dramaLevel", r.dramaLevel).put("createdAt", r.createdAt))
        }
        prefs.edit().putString("rooms", arr.toString()).apply()
    }

    fun deleteRoom(id: String) { saveRooms(rooms().filterNot { it.roomId == id }); prefs.edit().remove("messages_$id").apply() }

    private fun saveRooms(list: List<GroupRoom>) {
        val arr = JSONArray(); list.forEach { r -> arr.put(JSONObject().put("roomId",r.roomId).put("name",r.name).put("participantIds",JSONArray(r.participantIds)).put("dramaLevel",r.dramaLevel).put("createdAt",r.createdAt)) }
        prefs.edit().putString("rooms", arr.toString()).apply()
    }

    fun messages(roomId: String): List<GroupMessage> = try {
        val arr = JSONArray(prefs.getString("messages_$roomId", "[]") ?: "[]")
        buildList { for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(GroupMessage(o.optLong("timestamp"), o.optString("speakerId"), o.optString("speakerName"), o.optString("text"), o.optString("emotion", "平靜"), o.optString("source", "local")))
        } }
    } catch (_: Exception) { emptyList() }

    fun addMessage(roomId: String, m: GroupMessage) {
        val current = messages(roomId).takeLast(MAX_MESSAGES - 1)
        val arr = JSONArray()
        (current + m).forEach { x -> arr.put(JSONObject().put("timestamp",x.timestamp).put("speakerId",x.speakerId).put("speakerName",x.speakerName).put("text",x.text).put("emotion",x.emotion).put("source",x.source)) }
        prefs.edit().putString("messages_$roomId", arr.toString()).apply()
    }

    fun clearMessages(roomId: String) { prefs.edit().remove("messages_$roomId").apply() }

    fun removeCharacter(characterId: String) {
        val updated = rooms().mapNotNull { room ->
            val ids = room.participantIds.filterNot { it == characterId }
            when {
                ids.size < 2 -> { prefs.edit().remove("messages_${room.roomId}").apply(); null }
                else -> room.copy(participantIds = ids)
            }
        }
        saveRooms(updated)
    }

    companion object { const val MAX_MESSAGES = 1200 }
}
