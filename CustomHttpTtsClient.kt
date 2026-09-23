package com.example.virtualcompanion.engine

import android.content.Context
import android.util.Base64
import com.example.virtualcompanion.data.SecretStore
import com.example.virtualcompanion.model.CharacterProfile
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 通用自訂 TTS 協定：POST JSON {text, voice}。
 * 回應可直接是音訊位元組（Content-Type 為 audio 類型），或 JSON {audio_base64:"..."} / {audio:"..."}。
 */
class CustomHttpTtsClient(private val context: Context) {
    private val secrets = SecretStore(context)

    fun synthesize(text: String, profile: CharacterProfile): ByteArray {
        val endpoint = profile.ttsEndpoint.trim()
        require(endpoint.startsWith("https://")) { "自訂 TTS Endpoint 必須使用 HTTPS" }
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        secrets.get("tts_custom")?.takeIf { it.isNotBlank() }?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        val body = JSONObject().put("text", text).put("voice", profile.ttsVoice).toString().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val bytes = stream?.readBytes() ?: ByteArray(0)
        val contentType = conn.contentType.orEmpty().lowercase()
        conn.disconnect()
        if (code !in 200..299) error("TTS HTTP $code: ${String(bytes).take(240)}")
        if (contentType.startsWith("audio/")) return bytes
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val encoded = json.optString("audio_base64").ifBlank { json.optString("audio") }
        if (encoded.isBlank()) error("TTS 回應沒有音訊")
        return Base64.decode(encoded, Base64.DEFAULT)
    }
}
