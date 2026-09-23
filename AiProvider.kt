package com.example.virtualcompanion.ai

interface AiProvider {
    val id: String
    fun chat(request: AiChatRequest, apiKey: String): AiProviderReply
    fun vision(request: AiVisionRequest, apiKey: String): AiProviderReply
}
