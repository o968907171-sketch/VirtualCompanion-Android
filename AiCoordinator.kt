package com.example.virtualcompanion.ai

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.virtualcompanion.data.LongTermMemoryRepository
import com.example.virtualcompanion.data.SecretStore
import com.example.virtualcompanion.engine.DialogueEngine
import com.example.virtualcompanion.engine.DialogueReply
import com.example.virtualcompanion.engine.EmotionEngine
import com.example.virtualcompanion.engine.LongTermMemoryEngine
import com.example.virtualcompanion.engine.NetworkPolicy
import com.example.virtualcompanion.engine.PhotoReactionEngine
import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.MemoryItem
import java.util.concurrent.Executors

class AiCoordinator(private val context: Context, private val characterId: String) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val secrets = SecretStore(context)
    private val longRepo = LongTermMemoryRepository(context, characterId)

    fun replyAsync(profile: CharacterProfile, userText: String, recent: List<MemoryItem>, socialContext: String = "", localOverride: DialogueReply? = null, callback: (CompanionAiReply) -> Unit) {
        val local = localOverride ?: DialogueEngine.reply(userText, profile, recent)
        val fallback = CompanionAiReply(local.text, local.emotion, "本機", usedFallback = false)
        if (!profile.aiEnabled || profile.aiProvider == "本機") { main.post { callback(fallback) }; return }
        if (NetworkPolicy.strictOffline(context)) {
            main.post { callback(fallback.copy(provider = "本機・嚴格離線", usedFallback = false)) }
            return
        }
        if (!NetworkPolicy.networkAvailable(context)) {
            main.post { callback(fallback.copy(provider = "本機・離線回退", usedFallback = true, error = "目前沒有可用網路，已切回本機模式")) }
            return
        }
        val provider = provider(profile)
        val secretId = secretId(profile.aiProvider)
        val key = secrets.get(secretId)
        if (key.isNullOrBlank()) { main.post { callback(fallback.copy(usedFallback = true, error = "尚未設定 ${profile.aiProvider} API Key")) }; return }
        val recalled = LongTermMemoryEngine.recall(userText, longRepo.load())
        recalled.forEach { longRepo.touch(it.id) }
        executor.execute {
            val result = runCatching { provider.chat(AiChatRequest(profile, userText, recent.takeLast(18), recalled, socialContext), key) }
            val reply = result.fold(
                onSuccess = { CompanionAiReply(it.text, EmotionEngine.infer(userText, profile), provider.id) },
                onFailure = { fallback.copy(usedFallback = true, error = it.message) }
            )
            main.post { callback(reply) }
        }
    }

    fun visionAsync(profile: CharacterProfile, imageUri: String, recent: List<MemoryItem>, callback: (CompanionAiReply) -> Unit) {
        val local = PhotoReactionEngine.photo(profile)
        val fallback = CompanionAiReply(local.text, local.emotion, "本機", usedFallback = false)
        if (!profile.aiEnabled || !profile.visionEnabled || profile.aiProvider == "本機") { main.post { callback(fallback) }; return }
        if (NetworkPolicy.strictOffline(context)) {
            main.post { callback(fallback.copy(provider = "本機・嚴格離線", usedFallback = false)) }
            return
        }
        if (!NetworkPolicy.networkAvailable(context)) {
            main.post { callback(fallback.copy(provider = "本機・離線回退", usedFallback = true, error = "目前沒有可用網路，照片不會上傳；已使用本機反應")) }
            return
        }
        val provider = provider(profile)
        val key = secrets.get(secretId(profile.aiProvider))
        if (key.isNullOrBlank()) { main.post { callback(fallback.copy(usedFallback = true, error = "尚未設定 ${profile.aiProvider} API Key")) }; return }
        executor.execute {
            val result = runCatching {
                val payload = readImage(imageUri)
                val recalled = LongTermMemoryEngine.recall("圖片 照片 評價", longRepo.load())
                recalled.forEach { longRepo.touch(it.id) }
                provider.vision(AiVisionRequest(profile, "看看這張照片，請依你的角色性格自然回應或評價。", payload, recent.takeLast(8), recalled), key)
            }
            val reply = result.fold(
                onSuccess = { CompanionAiReply(it.text, EmotionEngine.infer(it.text, profile), provider.id) },
                onFailure = { fallback.copy(usedFallback = true, error = it.message) }
            )
            main.post { callback(reply) }
        }
    }

    fun shutdown() { executor.shutdownNow() }

    private fun provider(profile: CharacterProfile): AiProvider = when (profile.aiProvider) {
        "Gemini" -> GeminiProvider(profile.aiModel.takeUnless { it.isBlank() || it.startsWith("grok-", true) } ?: "gemini-3.8-flash")
        "Grok" -> OpenAiCompatibleProvider("Grok", "https://api.x.ai/v1", profile.aiModel.takeUnless { it.isBlank() || it.startsWith("gemini-", true) } ?: "grok-4.7")
        "OpenAI-compatible" -> OpenAiCompatibleProvider("OpenAI-compatible", profile.aiEndpoint, profile.aiModel)
        else -> error("未知 AI Provider：${profile.aiProvider}")
    }

    private fun readImage(ref: String): ImagePayload {
        val uri = Uri.parse(ref)
        val type = context.contentResolver.getType(uri) ?: "image/jpeg"
        require(type.startsWith("image/")) { "選取的檔案不是圖片" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192); var total = 0
            while (true) {
                val n = input.read(buf); if (n <= 0) break
                total += n; require(total <= 8 * 1024 * 1024) { "圖片超過 8MB，請先縮小後再試" }
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: error("無法讀取圖片")
        return ImagePayload(type, bytes)
    }

    companion object {
        fun secretId(provider: String) = when (provider) {
            "Gemini" -> "ai_gemini"
            "Grok" -> "ai_grok"
            "OpenAI-compatible" -> "ai_openai_compatible"
            else -> "ai_local"
        }
    }
}
