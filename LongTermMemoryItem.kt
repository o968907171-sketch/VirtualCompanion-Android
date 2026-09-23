package com.example.virtualcompanion.model

data class LongTermMemoryItem(
    val id: String,
    val summary: String,
    val tags: List<String> = emptyList(),
    val importance: Int = 3,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val source: String = "local"
)
