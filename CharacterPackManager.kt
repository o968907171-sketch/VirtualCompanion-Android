package com.example.virtualcompanion.pack

import android.content.Context
import android.net.Uri
import com.example.virtualcompanion.data.*
import com.example.virtualcompanion.model.MemoryItem
import com.example.virtualcompanion.model.LongTermMemoryItem
import com.example.virtualcompanion.model.StickerItem
import com.example.virtualcompanion.model.VoiceClip
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object CharacterPackManager {
    const val FORMAT = "virtual-companion-character-pack"
    const val VERSION = 4
    private const val MAX_ENTRIES = 320
    private const val MAX_ENTRY_BYTES = 40L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 280L * 1024L * 1024L

    data class ExportResult(val images: Int, val memories: Int, val stickers: Int, val voiceClips: Int)
    data class ImportResult(
        val characterId: String,
        val characterName: String,
        val images: Int,
        val memories: Int,
        val stickers: Int,
        val voiceClips: Int,
        val warnings: List<String>
    )

    data class PackPreview(
        val characterName: String,
        val authorName: String,
        val description: String,
        val version: Int,
        val includesMemories: Boolean,
        val images: Int,
        val stickers: Int,
        val voiceClips: Int
    )

    fun preview(context: Context, input: InputStream): PackPreview {
        val temp = File(context.cacheDir, "character_pack_preview_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            extractSafely(input, temp)
            val manifest = readJsonObject(File(temp, "pack.json"))
            require(manifest.optString("format") == FORMAT) { "這不是 Virtual Companion 角色包。" }
            val version = manifest.optInt("version", 0)
            require(version in 1..VERSION) { "角色包版本 $version 尚未支援。" }
            val data = readJsonObject(File(temp, "character.json"))
            val imageCount = File(temp, "images/index.json").takeIf { it.isFile }?.let { readJsonObject(it).length() } ?: 0
            val stickerCount = File(temp, "stickers/index.json").takeIf { it.isFile }?.let { JSONArray(it.readText(Charsets.UTF_8)).length() } ?: 0
            val voiceCount = File(temp, "voice/index.json").takeIf { it.isFile }?.let { JSONArray(it.readText(Charsets.UTF_8)).length() } ?: 0
            return PackPreview(
                characterName = clean(data.optString("characterName", "匯入角色"), 200).ifBlank { "匯入角色" },
                authorName = clean(data.optString("authorName", ""), 300),
                description = clean(data.optString("description", ""), 3000),
                version = version,
                includesMemories = manifest.optBoolean("includesMemories", false),
                images = imageCount,
                stickers = stickerCount,
                voiceClips = voiceCount
            )
        } finally { temp.deleteRecursively() }
    }

    fun export(context: Context, characterId: String, output: OutputStream, includeMemories: Boolean): ExportResult {
        val repo = ProfileRepository(context, characterId)
        val profile = repo.load()
        val memoryItems = if (includeMemories) MemoryRepository(context, characterId).load() else emptyList()
        val longMemoryItems = if (includeMemories) LongTermMemoryRepository(context, characterId).load() else emptyList()
        var imageCount = 0
        var stickerCount = 0
        var voiceCount = 0
        val imageIndex = JSONObject()

        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            putText(zip, "pack.json", JSONObject()
                .put("format", FORMAT).put("version", VERSION).put("exportedAt", System.currentTimeMillis())
                .put("includesMemories", includeMemories).put("includesLongTermMemories", includeMemories && longMemoryItems.isNotEmpty()).put("displayName", profile.characterName)
                .put("author", profile.authorName).toString(2))

            val profileJson = JSONObject()
                .put("characterName", profile.characterName).put("userName", "")
                .put("relationship", profile.relationship).put("personality", profile.personality)
                .put("speechStyle", profile.speechStyle).put("catchphrase", profile.catchphrase)
                .put("authorName", profile.authorName).put("description", profile.description)
                .put("bubblePosition", profile.bubblePosition).put("bubbleStyle", profile.bubbleStyle)
                .put("voiceEnabled", profile.voiceEnabled).put("speechRate", profile.speechRate.toDouble())
                .put("speechPitch", profile.speechPitch.toDouble()).put("memoryEnabled", profile.memoryEnabled)
                .put("portraitSlotCount", profile.portraitSlotCount).put("characterScale", profile.characterScale.toDouble())
                .put("batteryAwareness", profile.batteryAwareness).put("headphoneAwareness", profile.headphoneAwareness)
                .put("musicAwareness", profile.musicAwareness).put("notificationAwareness", profile.notificationAwareness)
                .put("notificationDetail", profile.notificationDetail).put("createdAt", profile.createdAt)
                .put("customEmotions", JSONArray(repo.customEmotions()))
            putText(zip, "character.json", profileJson.toString(2))

            profile.coverImageRef.takeIf { it.isNotBlank() }?.let { ref ->
                writeReference(context, zip, ref, "meta/cover")?.let { putText(zip, "meta/cover.json", JSONObject().put("path", it).toString()) }
            }
            profile.customBubbleImageRef.takeIf { it.isNotBlank() }?.let { ref ->
                writeReference(context, zip, ref, "bubble/background")?.let { putText(zip, "bubble/index.json", JSONObject().put("path", it).toString()) }
            }

            repo.allEmotions().forEachIndexed { index, emotion ->
                val ref = repo.emotionImage(emotion) ?: return@forEachIndexed
                val input = openReference(context, ref) ?: return@forEachIndexed
                input.use {
                    val path = "images/${index.toString().padStart(3, '0')}.${extensionFor(context, ref, "img")}" 
                    zip.putNextEntry(ZipEntry(path)); copyLimited(it, zip, MAX_ENTRY_BYTES); zip.closeEntry()
                    imageIndex.put(emotion, path); imageCount++
                }
            }
            putText(zip, "images/index.json", imageIndex.toString(2))

            val stickerArr = JSONArray()
            StickerRepository(context, characterId).load().forEachIndexed { index, item ->
                when (item.type) {
                    StickerItem.Type.KAOMOJI -> { stickerArr.put(JSONObject().put("id", item.id).put("label", item.label).put("type", "KAOMOJI").put("value", item.value)); stickerCount++ }
                    StickerItem.Type.IMAGE -> {
                        val input = openReference(context, item.value)
                        if (input != null) input.use {
                            val path = "stickers/${index.toString().padStart(3, '0')}.${extensionFor(context, item.value, "img")}" 
                            zip.putNextEntry(ZipEntry(path)); copyLimited(it, zip, MAX_ENTRY_BYTES); zip.closeEntry()
                            stickerArr.put(JSONObject().put("id", item.id).put("label", item.label).put("type", "IMAGE").put("path", path)); stickerCount++
                        }
                    }
                }
            }
            putText(zip, "stickers/index.json", stickerArr.toString(2))

            val voiceArr = JSONArray()
            VoiceClipRepository(context, characterId).load().forEachIndexed { index, clip ->
                val input = openReference(context, clip.ref) ?: return@forEachIndexed
                input.use {
                    val path = "voice/${index.toString().padStart(3, '0')}.${extensionFor(context, clip.ref, "audio")}" 
                    zip.putNextEntry(ZipEntry(path)); copyLimited(it, zip, MAX_ENTRY_BYTES); zip.closeEntry()
                    voiceArr.put(JSONObject().put("id", clip.id).put("label", clip.label).put("usage", clip.usage).put("path", path)); voiceCount++
                }
            }
            putText(zip, "voice/index.json", voiceArr.toString(2))

            if (includeMemories) {
                val arr = JSONArray()
                memoryItems.forEach { item -> arr.put(JSONObject().put("timestamp", item.timestamp).put("speaker", item.speaker).put("text", item.text).put("emotion", item.emotion).put("source", item.source).put("conversationId", item.conversationId)) }
                putText(zip, "memory.json", arr.toString(2))
                val longArr = JSONArray()
                longMemoryItems.forEach { item ->
                    val tags = JSONArray(); item.tags.forEach { tags.put(it) }
                    longArr.put(JSONObject().put("id", item.id).put("summary", item.summary).put("tags", tags).put("importance", item.importance)
                        .put("createdAt", item.createdAt).put("lastUsedAt", item.lastUsedAt).put("source", item.source))
                }
                putText(zip, "long_memory.json", longArr.toString(2))
            }
        }
        return ExportResult(imageCount, memoryItems.size + longMemoryItems.size, stickerCount, voiceCount)
    }

    fun import(context: Context, input: InputStream): ImportResult {
        val temp = File(context.cacheDir, "character_pack_${UUID.randomUUID()}").apply { mkdirs() }
        val warnings = mutableListOf<String>()
        var createdId: String? = null
        try {
            extractSafely(input, temp)
            val manifest = readJsonObject(File(temp, "pack.json"))
            require(manifest.optString("format") == FORMAT) { "這不是 Virtual Companion 角色包。" }
            val version = manifest.optInt("version", 0)
            require(version in 1..VERSION) { "角色包版本 $version 尚未支援。" }

            val data = readJsonObject(File(temp, "character.json"))
            val characterName = clean(data.optString("characterName", "匯入角色"), 200).ifBlank { "匯入角色" }
            val directory = CharacterDirectory(context); val newId = directory.create(characterName); createdId = newId
            val repo = ProfileRepository(context, newId)
            val profile = repo.load().apply {
                this.characterName = characterName
                userName = ""; relationship = clean(data.optString("relationship", "朋友"), 200)
                personality = clean(data.optString("personality", ""), 12_000); speechStyle = clean(data.optString("speechStyle", "自然"), 200)
                catchphrase = clean(data.optString("catchphrase", ""), 1_000); authorName = clean(data.optString("authorName", ""), 300)
                description = clean(data.optString("description", ""), 10_000)
                bubblePosition = clean(data.optString("bubblePosition", "上方"), 100); bubbleStyle = clean(data.optString("bubbleStyle", "圓角"), 100)
                voiceEnabled = data.optBoolean("voiceEnabled", true); speechRate = data.optDouble("speechRate", 1.0).toFloat().coerceIn(0.5f, 2.0f)
                speechPitch = data.optDouble("speechPitch", 1.0).toFloat().coerceIn(0.5f, 2.0f); memoryEnabled = data.optBoolean("memoryEnabled", true)
                portraitSlotCount = data.optInt("portraitSlotCount", 7).coerceIn(7, 38); characterScale = data.optDouble("characterScale", 1.0).toFloat().coerceIn(0.6f, 1.6f)
                // 角色包匯入不能替使用者自動打開手機感知；全部重設為關閉。
                batteryAwareness = false; headphoneAwareness = false; musicAwareness = false; notificationAwareness = false
                notificationDetail = "只提醒"; createdAt = data.optLong("createdAt", System.currentTimeMillis())
            }
            repo.save(profile)
            data.optJSONArray("customEmotions")?.let { arr -> for (i in 0 until arr.length()) repo.addCustomEmotion(clean(arr.optString(i), 100)) }

            val coverIndex = File(temp, "meta/cover.json")
            if (coverIndex.isFile) runCatching {
                val source = safeChild(temp, readJsonObject(coverIndex).optString("path"))
                if (source?.isFile == true) { val p = repo.load(); p.coverImageRef = CharacterAssetStore.importFile(context, newId, "cover", source, source.extension.ifBlank { "img" }); repo.save(p) }
            }.onFailure { warnings += "封面無法匯入：${it.message ?: "未知錯誤"}" }

            val bubbleIndex = File(temp, "bubble/index.json")
            if (bubbleIndex.isFile) runCatching {
                val source = safeChild(temp, readJsonObject(bubbleIndex).optString("path"))
                if (source?.isFile == true) { val p = repo.load(); p.customBubbleImageRef = CharacterAssetStore.importFile(context, newId, "bubble", source, source.extension.ifBlank { "img" }); repo.save(p) }
            }.onFailure { warnings += "自訂對話框圖片無法匯入：${it.message ?: "未知錯誤"}" }

            var imageCount = 0
            val indexFile = File(temp, "images/index.json")
            if (indexFile.isFile) {
                val index = readJsonObject(indexFile); val keys = index.keys()
                while (keys.hasNext()) {
                    val emotion = clean(keys.next(), 100); if (emotion !in repo.allEmotions()) repo.addCustomEmotion(emotion)
                    val source = safeChild(temp, index.optString(emotion))
                    if (source?.isFile == true) runCatching {
                        repo.setEmotionImage(emotion, CharacterAssetStore.importFile(context, newId, emotion, source, source.extension.ifBlank { "img" })); imageCount++
                    }.onFailure { warnings += "無法匯入情緒圖片「$emotion」：${it.message ?: "未知錯誤"}" }
                }
            }

            var stickerCount = 0
            val stickerIndex = File(temp, "stickers/index.json")
            if (stickerIndex.isFile) runCatching {
                val arr = JSONArray(stickerIndex.readText(Charsets.UTF_8)); val stickerRepo = StickerRepository(context, newId)
                for (i in 0 until minOf(arr.length(), StickerRepository.MAX_ITEMS)) {
                    val o = arr.optJSONObject(i) ?: continue; val label = clean(o.optString("label", "貼圖"), 80)
                    if (o.optString("type") == "KAOMOJI") { if (stickerRepo.addKaomoji(clean(o.optString("value", ""), 200), label)) stickerCount++ }
                    else if (o.optString("type") == "IMAGE") {
                        val source = safeChild(temp, o.optString("path")); if (source?.isFile == true) {
                            val ref = CharacterAssetStore.importFile(context, newId, "sticker_$i", source, source.extension.ifBlank { "img" }); if (stickerRepo.addImage(label, ref)) stickerCount++
                        }
                    }
                }
            }.onFailure { warnings += "部分貼圖無法匯入：${it.message ?: "未知錯誤"}" }

            var voiceCount = 0
            val voiceIndex = File(temp, "voice/index.json")
            if (voiceIndex.isFile) runCatching {
                val arr = JSONArray(voiceIndex.readText(Charsets.UTF_8)); val voiceRepo = VoiceClipRepository(context, newId)
                for (i in 0 until minOf(arr.length(), VoiceClipRepository.MAX_ITEMS)) {
                    val o = arr.optJSONObject(i) ?: continue; val source = safeChild(temp, o.optString("path"))
                    if (source?.isFile == true) {
                        val ref = CharacterAssetStore.importFile(context, newId, "voice_$i", source, source.extension.ifBlank { "audio" })
                        if (voiceRepo.add(VoiceClip(o.optString("id", "voice_$i"), clean(o.optString("label", "聲音"), 80), clean(o.optString("usage", "通用"), 40), ref))) voiceCount++
                    }
                }
            }.onFailure { warnings += "部分反應音檔無法匯入：${it.message ?: "未知錯誤"}" }

            var memoryCount = 0
            val memoryFile = File(temp, "memory.json")
            if (memoryFile.isFile) { val parsed = readMemories(memoryFile); MemoryRepository(context, newId).addAll(parsed); memoryCount += parsed.size }
            val longMemoryFile = File(temp, "long_memory.json")
            if (longMemoryFile.isFile) { val parsed = readLongMemories(longMemoryFile); LongTermMemoryRepository(context, newId).addAll(parsed); memoryCount += parsed.size }
            directory.setCurrent(newId)
            return ImportResult(newId, characterName, imageCount, memoryCount, stickerCount, voiceCount, warnings)
        } catch (e: Exception) {
            createdId?.let { id -> runCatching { CharacterDirectory(context).delete(id) } }; throw e
        } finally { temp.deleteRecursively() }
    }

    private fun writeReference(context: Context, zip: ZipOutputStream, ref: String, pathBase: String): String? {
        val input = openReference(context, ref) ?: return null
        input.use {
            val path = "$pathBase.${extensionFor(context, ref, "bin")}"; zip.putNextEntry(ZipEntry(path)); copyLimited(it, zip, MAX_ENTRY_BYTES); zip.closeEntry(); return path
        }
    }

    private fun readMemories(file: File): List<MemoryItem> {
        val arr = JSONArray(file.readText(Charsets.UTF_8)); val out = ArrayList<MemoryItem>(minOf(arr.length(), 2500)); val start = maxOf(0, arr.length() - 2500)
        for (i in start until arr.length()) { val o = arr.optJSONObject(i) ?: continue; val text = clean(o.optString("text", ""), 200_000); if (text.isBlank()) continue
            out += MemoryItem(o.optLong("timestamp", System.currentTimeMillis()), clean(o.optString("speaker", "角色"), 300), text, clean(o.optString("emotion", "平靜"), 100), clean(o.optString("source", "character_pack"), 100), clean(o.optString("conversationId", ""), 500)) }
        return out
    }

    private fun readLongMemories(file: File): List<LongTermMemoryItem> {
        val arr = JSONArray(file.readText(Charsets.UTF_8)); val out = ArrayList<LongTermMemoryItem>(minOf(arr.length(), 400)); val start = maxOf(0, arr.length() - 400)
        for (i in start until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue; val summary = clean(o.optString("summary", ""), 4000); if (summary.isBlank()) continue
            val tagsJson = o.optJSONArray("tags") ?: JSONArray(); val tags = buildList { for (j in 0 until tagsJson.length()) add(clean(tagsJson.optString(j), 120)) }.filter { it.isNotBlank() }
            out += LongTermMemoryItem(
                id = clean(o.optString("id", UUID.randomUUID().toString()), 200), summary = summary, tags = tags.take(16),
                importance = o.optInt("importance", 3).coerceIn(1, 5), createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                lastUsedAt = o.optLong("lastUsedAt", System.currentTimeMillis()), source = clean(o.optString("source", "character_pack"), 100)
            )
        }
        return out
    }

    private fun extractSafely(input: InputStream, temp: File) {
        var total = 0L; var entries = 0
        ZipInputStream(BufferedInputStream(input)).use { zip -> var entry = zip.nextEntry
            while (entry != null) {
                entries++; require(entries <= MAX_ENTRIES) { "角色包包含過多檔案。" }
                val name = entry.name.replace('\\', '/'); require(name.isNotBlank() && !name.startsWith('/') && !name.contains("../") && !name.contains("/..")) { "角色包包含不安全路徑。" }
                val out = safeChild(temp, name) ?: throw IOException("無效角色包路徑。")
                if (entry.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); FileOutputStream(out).use { fileOut -> val copied = copyLimited(zip, fileOut, MAX_ENTRY_BYTES); total += copied; require(total <= MAX_TOTAL_BYTES) { "角色包總大小超過限制。" } } }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
    }

    private fun safeChild(root: File, relative: String): File? { if (relative.isBlank()) return null; val child = File(root, relative).canonicalFile; val base = root.canonicalFile; return child.takeIf { it.path == base.path || it.path.startsWith(base.path + File.separator) } }
    private fun readJsonObject(file: File): JSONObject { require(file.isFile) { "角色包缺少 ${file.name}。" }; require(file.length() <= 2L * 1024L * 1024L) { "${file.name} 過大。" }; return JSONObject(file.readText(Charsets.UTF_8)) }
    private fun openReference(context: Context, ref: String): InputStream? { CharacterAssetStore.openFileUri(ref)?.let { return it }; return try { context.contentResolver.openInputStream(Uri.parse(ref)) } catch (_: Exception) { null } }

    private fun extensionFor(context: Context, ref: String, fallback: String): String {
        val uri = Uri.parse(ref); val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()?.lowercase(Locale.ROOT)
        val fromMime = when (mime) { "image/png" -> "png"; "image/jpeg" -> "jpg"; "image/webp" -> "webp"; "image/gif" -> "gif"; "audio/mpeg" -> "mp3"; "audio/mp4", "audio/aac" -> "m4a"; "audio/ogg" -> "ogg"; "audio/wav", "audio/x-wav" -> "wav"; else -> null }
        if (fromMime != null) return fromMime
        val ext = (uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: fallback
    }

    private fun putText(zip: ZipOutputStream, name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long { val buffer = ByteArray(16 * 1024); var total = 0L; while (true) { val n = input.read(buffer); if (n <= 0) break; total += n; require(total <= limit) { "單一檔案超過 ${limit / 1024 / 1024} MB 限制。" }; output.write(buffer, 0, n) }; return total }
    private fun clean(value: String, max: Int): String = value.trim().take(max)
}
