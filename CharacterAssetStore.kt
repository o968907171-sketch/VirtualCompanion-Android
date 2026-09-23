package com.example.virtualcompanion.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object CharacterAssetStore {
    private fun root(context: Context, characterId: String): File =
        File(context.filesDir, "character_assets/$characterId").apply { mkdirs() }

    fun importFile(context: Context, characterId: String, emotion: String, source: File, extension: String): String {
        val safe = safeName(emotion)
        val ext = extension.trim('.').ifBlank { "bin" }.take(8)
        val out = File(root(context, characterId), "$safe.$ext")
        source.inputStream().use { input -> FileOutputStream(out).use { output -> input.copyTo(output) } }
        return Uri.fromFile(out).toString()
    }

    fun duplicateReference(context: Context, sourceCharacterId: String, targetCharacterId: String, emotion: String, ref: String): String {
        val uri = Uri.parse(ref)
        if (uri.scheme != "file") return ref
        val source = uri.path?.let(::File) ?: return ref
        val sourceRoot = root(context, sourceCharacterId).canonicalFile
        val canonical = runCatching { source.canonicalFile }.getOrNull() ?: return ref
        if (!canonical.path.startsWith(sourceRoot.path + File.separator) || !canonical.isFile) return ref
        val ext = canonical.extension.ifBlank { "bin" }
        return importFile(context, targetCharacterId, emotion, canonical, ext)
    }

    fun clear(context: Context, characterId: String) {
        File(context.filesDir, "character_assets/$characterId").deleteRecursively()
    }

    fun openFileUri(ref: String): FileInputStream? {
        val uri = Uri.parse(ref)
        if (uri.scheme != "file") return null
        val f = uri.path?.let(::File) ?: return null
        return if (f.isFile) FileInputStream(f) else null
    }

    private fun safeName(value: String): String = value.trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .take(80)
        .ifBlank { "emotion" }
}
