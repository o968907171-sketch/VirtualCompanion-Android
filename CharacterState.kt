package com.example.virtualcompanion.model

data class CharacterState(
    var currentEmotion: String = EmotionCatalog.CALM,
    var emotionSince: Long = System.currentTimeMillis(),
    var sleeping: Boolean = false,
    var overlayX: Int = 24,
    var overlayY: Int = 80,
    var lastInteraction: Long = System.currentTimeMillis()
)
