package com.example.virtualcompanion.model

data class MemoryItem(
    val timestamp: Long,
    val speaker: String,
    val text: String,
    val emotion: String = EmotionCatalog.CALM,
    val source: String = "local",
    val conversationId: String = ""
)
