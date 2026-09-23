package com.example.virtualcompanion.engine

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.example.virtualcompanion.model.CharacterProfile
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class VoiceEngine(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    private val custom = CustomHttpTtsClient(appContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var ready = false
    private var offlineVoice: Voice? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.getDefault()
            val locale = Locale.getDefault()
            offlineVoice = tts.voices
                ?.filter { !it.isNetworkConnectionRequired }
                ?.sortedByDescending { v ->
                    when {
                        v.locale == locale -> 3
                        v.locale.language == locale.language -> 2
                        else -> 1
                    }
                }
                ?.firstOrNull()
        }
    }

    fun speak(text: String, profile: CharacterProfile) {
        if (!profile.voiceEnabled) return
        if (NetworkPolicy.strictOffline(appContext) || !NetworkPolicy.networkAvailable(appContext)) {
            speakAndroidOffline(text, profile)
            return
        }
        if (profile.ttsProvider == "自訂 HTTP" && profile.ttsEndpoint.isNotBlank()) {
            executor.execute {
                val audio = runCatching { custom.synthesize(text, profile) }.getOrNull()
                if (audio != null && audio.isNotEmpty()) main.post { playBytes(audio) }
                else main.post { speakAndroid(text, profile) }
            }
        } else speakAndroid(text, profile)
    }

    private fun speakAndroidOffline(text: String, profile: CharacterProfile) {
        if (!ready) return
        val local = offlineVoice ?: return
        tts.voice = local
        tts.setSpeechRate(profile.speechRate.coerceIn(0.5f, 2.0f))
        tts.setPitch(profile.speechPitch.coerceIn(0.5f, 2.0f))
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "virtual_companion_offline")
    }

    private fun speakAndroid(text: String, profile: CharacterProfile) {
        if (!ready) return
        tts.setSpeechRate(profile.speechRate.coerceIn(0.5f, 2.0f))
        tts.setPitch(profile.speechPitch.coerceIn(0.5f, 2.0f))
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "virtual_companion")
    }

    private fun playBytes(bytes: ByteArray) {
        runCatching {
            player?.release()
            val file = File.createTempFile("vc_tts_", ".mp3", appContext.cacheDir).apply { writeBytes(bytes) }
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null; file.delete() }
                setOnErrorListener { mp, _, _ -> mp.release(); if (player === mp) player = null; file.delete(); true }
                prepare(); start()
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow(); player?.release(); player = null; tts.shutdown()
    }
}
