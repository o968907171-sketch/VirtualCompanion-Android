package com.example.virtualcompanion.engine

import com.example.virtualcompanion.model.CharacterProfile
import java.util.Calendar

object TimeEngine {
    fun greeting(profile: CharacterProfile): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val user = profile.userName.ifBlank { "你" }
        return when (h) {
            in 5..10 -> "早安，$user。"
            in 11..13 -> "午安，$user。"
            in 14..17 -> "下午好，$user。"
            in 18..22 -> "晚上好，$user。"
            else -> "……$user，這個時間還沒睡？"
        }
    }
}
