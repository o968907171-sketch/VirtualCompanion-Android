package com.example.virtualcompanion.model

data class StickerItem(
    val id: String,
    val label: String,
    val type: Type,
    val value: String
) {
    enum class Type { IMAGE, KAOMOJI }
}
