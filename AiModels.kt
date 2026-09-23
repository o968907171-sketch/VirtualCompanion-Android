package com.example.virtualcompanion.ai

import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.LongTermMemoryItem
import com.example.virtualcompanion.model.MemoryItem

data class AiChatRequest(
    val profile: CharacterProfile,
    val userText: String,
    val recent: List<MemoryItem>,
    val longTerm: List<LongTermMemoryItem>,
    val socialContext: String = ""
)

data class AiVisionRequest(
    val profile: CharacterProfile,
    val prompt: String,
    val image: ImagePayload,
    val recent: List<MemoryItem>,
    val longTerm: List<LongTermMemoryItem>,
    val socialContext: String = ""
)

data class ImagePayload(val mimeType: String, val bytes: ByteArray)

data class AiProviderReply(val text: String)

data class CompanionAiReply(
    val text: String,
    val emotion: String,
    val provider: String,
    val usedFallback: Boolean = false,
    val error: String? = null
)
