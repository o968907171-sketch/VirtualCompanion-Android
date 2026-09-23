package com.example.virtualcompanion.ai

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class GeminiProvider(private val model: String) : AiProvider {
    override val id = "Gemini"
    private val cleanModel get() = model.removePrefix("models/").ifBlank { "gemini-3.8-flash" }

    override fun chat(request: AiChatRequest, apiKey: String): AiProviderReply {
        val root = JSONObject()
        root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", AiPromptBuilder.system(request.profile, request.longTerm, socialContext = request.socialContext)))))
        val contents = JSONArray()
        AiPromptBuilder.recentMessages(request.recent, request.profile.characterName).forEach { (role, text) ->
            contents.put(content(if (role == "assistant") "model" else "user", JSONArray().put(JSONObject().put("text", text))))
        }
        contents.put(content("user", JSONArray().put(JSONObject().put("text", request.userText))))
        root.put("contents", contents)
        root.put("generationConfig", JSONObject().put("temperature", 0.9).put("maxOutputTokens", 700))
        return call(root, apiKey)
    }

    override fun vision(request: AiVisionRequest, apiKey: String): AiProviderReply {
        val root = JSONObject()
        root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", AiPromptBuilder.system(request.profile, request.longTerm, vision = true)))))
        val parts = JSONArray()
            .put(JSONObject().put("text", request.prompt))
            .put(JSONObject().put("inlineData", JSONObject()
                .put("mimeType", request.image.mimeType)
                .put("data", Base64.encodeToString(request.image.bytes, Base64.NO_WRAP))))
        root.put("contents", JSONArray().put(content("user", parts)))
        root.put("generationConfig", JSONObject().put("temperature", 0.8).put("maxOutputTokens", 500))
        return call(root, apiKey)
    }

    private fun content(role: String, parts: JSONArray) = JSONObject().put("role", role).put("parts", parts)

    private fun call(root: JSONObject, apiKey: String): AiProviderReply {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent"
        val (code, body) = HttpJson.post(url, mapOf(
            "x-goog-api-key" to apiKey,
            "x-goog-api-client" to "virtual-companion/0.6.1"
        ), root)
        if (code !in 200..299) error("Gemini HTTP $code: ${body.take(300)}")
        val json = JSONObject(body)
        val candidates = json.optJSONArray("candidates") ?: error("Gemini 沒有回傳 candidates")
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: error("Gemini 回覆內容為空")
        val out = buildString { for (i in 0 until parts.length()) parts.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }?.let { append(it) } }.trim()
        if (out.isBlank()) error("Gemini 回覆為空")
        return AiProviderReply(out)
    }
}
