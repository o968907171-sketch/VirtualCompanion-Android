package com.example.virtualcompanion.model

data class SocialRelationship(
    val sourceCharacterId: String,
    val targetCharacterId: String,
    var affinity: Int = 0,
    var trust: Int = 0,
    var attachment: Int = 0,
    var jealousy: Int = 0,
    var rivalry: Int = 0,
    var irritation: Int = 0,
    var lastEvent: String = "",
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun normalize(): SocialRelationship {
        affinity = affinity.coerceIn(-100, 100)
        trust = trust.coerceIn(-100, 100)
        attachment = attachment.coerceIn(0, 100)
        jealousy = jealousy.coerceIn(0, 100)
        rivalry = rivalry.coerceIn(0, 100)
        irritation = irritation.coerceIn(0, 100)
        updatedAt = System.currentTimeMillis()
        return this
    }

    fun dominantFeeling(): String = when {
        irritation >= 70 -> "惱火"
        jealousy >= 65 -> "吃醋"
        rivalry >= 65 -> "競爭"
        affinity >= 65 && trust >= 45 -> "親近"
        affinity <= -45 -> "反感"
        trust <= -45 -> "戒備"
        else -> "普通"
    }
}
