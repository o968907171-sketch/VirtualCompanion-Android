package com.example.virtualcompanion.engine

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.virtualcompanion.data.VoiceClipRepository

class ReactionAudioEngine(private val context: Context, private val characterId: String) {
    private var player: MediaPlayer? = null

    fun playUsage(usage: String): Boolean {
        val clips = VoiceClipRepository(context, characterId).load().filter { it.usage == usage || it.usage == "通用" }
        val clip = clips.randomOrNull() ?: return false
        return try {
            player?.release()
            player = MediaPlayer.create(context, Uri.parse(clip.ref))?.apply {
                setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                start()
            }
            player != null
        } catch (_: Exception) { false }
    }

    fun shutdown() { try { player?.release() } catch (_: Exception) {}; player = null }
}
