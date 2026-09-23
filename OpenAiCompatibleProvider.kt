package com.example.virtualcompanion.ai

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleProvider(
    override val id: String,
    private val endpoint: String,
    private val model: String
) : AiProvider {
    override fun chat(request: AiChatRequest, apiKey: String): AiProviderReply {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", AiPromptBuilder.system(request.profile, request.longTerm, socialContext = request.socialContext)))
        AiPromptBuilder.recentMessages(request.recent, request.profile.characterName).forEach { (role,text) ->
            messages.put(JSONObject().put("role", role).put("content", text))
        }
        messages.put(JSONObject().put("role", "user").put("content", request.userText))
        return call(messages, apiKey)
    }

    override fun vision(request: AiVisionRequest, apiKey: String): AiProviderReply {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", AiPromptBuilder.system(request.profile, request.longTerm, vision = true)))
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", request.prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url",
                "data:${request.image.mimeType};base64,${Base64.encodeToString(request.image.bytes, Base64.NO_WRAP)}")))
        messages.put(JSONObject().put("role", "user").put("content", content))
        return call(messages, apiKey)
    }

    private fun call(messages: JSONArray, apiKey: String): AiProviderReply {
        val root = JSONObject().put("model", model.ifBlank { error("尚未設定模型名稱") })
            .put("messages", messages).put("temperature", 0.9).put("max_tokens", 700)
        val (code, body) = HttpJson.post(normalize(endpoint), mapOf("Authorization" to "Bearer $apiKey"), root)
        if (code !in 200..299) error("$id HTTP $code: ${body.take(300)}")
        val json = JSONObject(body)
        val content = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
        val out = when (content) {
            is String -> content
            is JSONArray -> buildString { for (i in 0 until content.length()) content.optJSONObject(i)?.optString("text")?.let { append(it) } }
            else -> ""
        }.trim()
        if (out.isBlank()) error("$id 回覆為空")
        return AiProviderReply(out)
    }

    private fun normalize(raw: String): String {
        val s = raw.trim().trimEnd('/')
        require(s.startsWith("https://")) { "AI Endpoint 必須使用 HTTPS" }
        return when {
            s.endsWith("/chat/completions") -> s
            s.endsWith("/v1") -> "$s/chat/completions"
            else -> "$s/v1/chat/completions"
        }
    }
}
