package com.example.virtualcompanion.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationAwarenessService : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private val controllerCallbacks = mutableMapOf<String, Pair<MediaController, MediaController.Callback>>()
    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers -> refreshMediaControllers(controllers) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        refreshMediaControllers()
        try {
            sessionManager?.addOnActiveSessionsChangedListener(activeSessionsListener, ComponentName(this, NotificationAwarenessService::class.java))
        } catch (_: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        emit(Intent(OverlayService.ACTION_PHONE_NOTIFICATION).apply {
            putExtra(OverlayService.EXTRA_PACKAGE_NAME, sbn.packageName)
            putExtra(OverlayService.EXTRA_NOTIFICATION_TITLE, title.take(300))
            putExtra(OverlayService.EXTRA_NOTIFICATION_TEXT, text.take(600))
        })
        refreshMediaControllers()
    }

    private fun refreshMediaControllers(controllers: List<MediaController>? = null) {
        val current = try {
            controllers ?: sessionManager?.getActiveSessions(ComponentName(this, NotificationAwarenessService::class.java)).orEmpty()
        } catch (_: Exception) { emptyList() }

        val ids = current.map { it.packageName }.toSet()
        controllerCallbacks.keys.filterNot { it in ids }.forEach { key ->
            controllerCallbacks.remove(key)?.let { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        }

        current.forEach { controller ->
            if (controller.packageName !in controllerCallbacks) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) { emitMusic(controller, metadata) }
                    override fun onPlaybackStateChanged(state: PlaybackState?) { emitMusic(controller, controller.metadata) }
                }
                runCatching { controller.registerCallback(callback) }
                controllerCallbacks[controller.packageName] = controller to callback
            }
            emitMusic(controller, controller.metadata)
        }
    }

    private fun emitMusic(controller: MediaController, metadata: MediaMetadata?) {
        if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) return
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        if (title.isBlank() && artist.isBlank()) return
        emit(Intent(OverlayService.ACTION_MUSIC_CHANGED).apply {
            putExtra(OverlayService.EXTRA_PACKAGE_NAME, controller.packageName)
            putExtra(OverlayService.EXTRA_MEDIA_TITLE, title.take(300))
            putExtra(OverlayService.EXTRA_MEDIA_ARTIST, artist.take(300))
        })
    }

    private fun emit(intent: Intent) {
        intent.setClass(this, OverlayService::class.java)
        try { startService(intent) } catch (_: Exception) { /* Overlay FGS may not currently be running. */ }
    }

    override fun onDestroy() {
        controllerCallbacks.values.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        controllerCallbacks.clear()
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        super.onDestroy()
    }
}
