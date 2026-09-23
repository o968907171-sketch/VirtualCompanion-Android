package com.example.virtualcompanion.model

data class GroupRoom(
    val roomId: String,
    var name: String,
    var participantIds: List<String>,
    /** 0=幾乎不衝突, 1=輕微, 2=自然, 3=戲劇化 */
    var dramaLevel: Int = 2,
    var createdAt: Long = System.currentTimeMillis()
)

data class GroupMessage(
    val timestamp: Long,
    val speakerId: String,
    val speakerName: String,
    val text: String,
    val emotion: String = EmotionCatalog.CALM,
    val source: String = "local"
)
