package com.example.virtualcompanion.service

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import com.example.virtualcompanion.R
import com.example.virtualcompanion.ai.AiCoordinator
import com.example.virtualcompanion.data.*
import com.example.virtualcompanion.engine.*
import com.example.virtualcompanion.model.*
import com.example.virtualcompanion.ui.BubbleStyler
import com.example.virtualcompanion.ui.GroupChatActivity
import com.example.virtualcompanion.ui.MainActivity
import com.example.virtualcompanion.ui.PhotoReactionActivity
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var directory: CharacterDirectory
    private lateinit var audioManager: AudioManager
    private val windows = linkedMapOf<String, CompanionWindow>()
    private var headphoneConnected = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
            val percent = if (level >= 0) (level * 100 / scale).coerceIn(0, 100) else return
            val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
            windows.values.forEach { it.onBattery(percent, charging) }
        }
    }

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = updateHeadphones()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = updateHeadphones()
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        directory = CharacterDirectory(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createChannel(); startAsForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        directory.activeIds().forEach { addWindow(it) }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        headphoneConnected = hasHeadphones()
        audioManager.registerAudioDeviceCallback(audioCallback, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CHARACTER -> intent.getStringExtra(EXTRA_CHARACTER_ID)?.let { directory.setActive(it, true); addWindow(it); updateNotification() }
            ACTION_STOP_CHARACTER -> intent.getStringExtra(EXTRA_CHARACTER_ID)?.let { directory.setActive(it, false); removeWindow(it); updateNotification() }
            ACTION_REFRESH_CHARACTER -> intent.getStringExtra(EXTRA_CHARACTER_ID)?.let { windows[it]?.refresh() }
            ACTION_START_ALL -> { directory.activeIds().forEach { addWindow(it) }; updateNotification() }
            ACTION_REACT_IMAGE -> {
                val id = intent.getStringExtra(EXTRA_CHARACTER_ID); val ref = intent.getStringExtra(EXTRA_IMAGE_URI)
                if (!id.isNullOrBlank() && !ref.isNullOrBlank()) {
                    directory.setActive(id, true); addWindow(id); windows[id]?.reactToExternalImage(ref); updateNotification()
                }
            }
            ACTION_PHONE_NOTIFICATION -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
                val title = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE).orEmpty()
                val text = intent.getStringExtra(EXTRA_NOTIFICATION_TEXT).orEmpty()
                windows.values.forEach { it.onPhoneNotification(pkg, title, text) }
            }
            ACTION_MUSIC_CHANGED -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
                val title = intent.getStringExtra(EXTRA_MEDIA_TITLE).orEmpty()
                val artist = intent.getStringExtra(EXTRA_MEDIA_ARTIST).orEmpty()
                windows.values.forEach { it.onMusic(pkg, title, artist) }
            }
        }
        return START_STICKY
    }

    private fun updateHeadphones() {
        val now = hasHeadphones()
        if (now == headphoneConnected) return
        headphoneConnected = now
        windows.values.forEach { it.onHeadphones(now) }
    }

    private fun hasHeadphones(): Boolean = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> true
            else -> false
        }
    }

    private fun addWindow(id: String) { if (id !in windows) windows[id] = CompanionWindow(id) }
    private fun removeWindow(id: String) { windows.remove(id)?.destroy(); if (windows.isEmpty() && directory.activeIds().isEmpty()) stopSelf() }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification())
    }
    private fun updateNotification() { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 102, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val count = maxOf(windows.size, directory.activeIds().size)
        return Notification.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Virtual Companion 正在陪伴你")
            .setContentText(if (count <= 1) "點此開啟角色設定。" else "$count 隻角色正在手機裡。點此開啟角色設定。")
            .setOngoing(true).setContentIntent(open).build()
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_channel_description)
            })
    }

    inner class CompanionWindow(private val id: String) {
        private val profiles = ProfileRepository(this@OverlayService, id)
        private val states = StateRepository(this@OverlayService, id)
        private val memories = MemoryRepository(this@OverlayService, id)
        private val longMemories = LongTermMemoryRepository(this@OverlayService, id)
        private val ai = AiCoordinator(this@OverlayService, id)
        private val stickers = StickerRepository(this@OverlayService, id)
        private val cooldown = ContextCooldownRepository(this@OverlayService, id)
        private val voice = VoiceEngine(this@OverlayService)
        private val reactionAudio = ReactionAudioEngine(this@OverlayService, id)
        private val root = LinearLayout(this@OverlayService)
        private val stage = FrameLayout(this@OverlayService)
        private val image = ImageView(this@OverlayService)
        private val miniImage = ImageView(this@OverlayService)
        private val bubble = TextView(this@OverlayService)
        private lateinit var sleep: Button
        private val lp: WindowManager.LayoutParams
        private var downAt = 0L; private var downX = 0f; private var downY = 0f; private var startX = 0; private var startY = 0; private var moved = false
        private var destroyed = false

        init {
            val s = states.load(); val p = profiles.load()
            lp = WindowManager.LayoutParams(dp(390), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END; x = s.overlayX; y = s.overlayY
            }
            root.orientation = LinearLayout.VERTICAL; root.setPadding(dp(5), dp(5), dp(5), dp(5))
            stage.layoutParams = LinearLayout.LayoutParams(dp(380), dp(360))
            image.scaleType = ImageView.ScaleType.FIT_CENTER; image.setOnTouchListener { v, e -> touch(v, e) }; applyCharacterScale(p.characterScale)

            miniImage.scaleType = ImageView.ScaleType.CENTER_CROP; miniImage.visibility = View.GONE
            miniImage.background = android.graphics.drawable.GradientDrawable().apply { setColor(0xEEFFFFFF.toInt()); cornerRadius = dp(12).toFloat(); setStroke(dp(2), 0xAA6750A4.toInt()) }
            miniImage.setPadding(dp(4), dp(4), dp(4), dp(4)); miniImage.layoutParams = FrameLayout.LayoutParams(dp(92), dp(92), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(52), dp(8), 0) }

            bubble.textSize = 14f; bubble.maxLines = 5; bubble.layoutParams = bubbleLayout(p.bubblePosition)
            BubbleStyler.apply(bubble, p.bubbleStyle, this@OverlayService, p.customBubbleImageRef)
            stage.addView(image); stage.addView(miniImage); stage.addView(bubble); root.addView(stage)

            val row1 = LinearLayout(this@OverlayService).apply { gravity = Gravity.CENTER }
            row1.addView(btn("聊天") { if (!states.load().sleeping) chat() })
            row1.addView(btn("貼圖") { if (!states.load().sleeping) stickerDialog() })
            row1.addView(btn("看圖") { if (!states.load().sleeping) pickPhoto() }); root.addView(row1)
            val row2 = LinearLayout(this@OverlayService).apply { gravity = Gravity.CENTER }
            sleep = btn(if (s.sleeping) "醒來" else "睡覺") { toggleSleep() }; row2.addView(sleep)
            row2.addView(btn("群聊") { startActivity(Intent(this@OverlayService, GroupChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })
            row2.addView(btn("設定") { startActivity(Intent(this@OverlayService, MainActivity::class.java).putExtra("characterId", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }); root.addView(row2)

            wm.addView(root, lp)
            val current = EmotionEngine.decayedEmotion(s.currentEmotion, s.emotionSince, s.sleeping)
            setEmotion(current, current != s.currentEmotion)
            bubble.text = if (s.sleeping) "……zzz……" else TimeEngine.greeting(p)
        }

        fun refresh() {
            val p = profiles.load(); BubbleStyler.apply(bubble, p.bubbleStyle, this@OverlayService, p.customBubbleImageRef)
            bubble.layoutParams = bubbleLayout(p.bubblePosition); applyCharacterScale(p.characterScale); bubble.requestLayout()
            val s = states.load(); setEmotion(EmotionEngine.decayedEmotion(s.currentEmotion, s.emotionSince, s.sleeping), false); sleep.text = if (s.sleeping) "醒來" else "睡覺"
        }
        fun destroy() { destroyed = true; ai.shutdown(); try { wm.removeView(root) } catch (_: Exception) {}; voice.shutdown(); reactionAudio.shutdown() }

        fun reactToExternalImage(ref: String) {
            showMiniImage(ref)
            val p = profiles.load()
            if (p.aiEnabled && p.visionEnabled && p.aiProvider != "本機") bubble.text = "……讓我看看。"
            ai.visionAsync(p, ref, if (p.memoryEnabled) memories.load() else emptyList()) { r ->
                if (!destroyed) say(r.text, r.emotion)
            }
        }

        fun onBattery(percent: Int, charging: Boolean) {
            val p = profiles.load(); if (!p.batteryAwareness || states.load().sleeping) return
            if (percent <= 20 && !charging && cooldown.allow("battery_low", 30 * 60_000L)) {
                val text = when (p.speechStyle) { "毒舌" -> "剩 $percent% 了。你是打算把手機也累死嗎？"; "溫柔" -> "電量只剩 $percent% 了，記得找時間充電。"; else -> "手機只剩 $percent% 了，記得充電。" }
                say(text, EmotionCatalog.PUZZLED, "低電量")
            } else if (charging && percent <= 35 && cooldown.allow("battery_charging", 60 * 60_000L)) {
                say("嗯，開始充電了。這樣我就放心一點。", EmotionCatalog.CALM)
            }
        }

        fun onHeadphones(connected: Boolean) {
            val p = profiles.load(); if (!p.headphoneAwareness || states.load().sleeping || !cooldown.allow("headphones", 5 * 60_000L)) return
            say(if (connected) "戴上耳機了？要聽點什麼？" else "耳機拿掉了。", if (connected) EmotionCatalog.PUZZLED else EmotionCatalog.CALM)
        }

        fun onMusic(pkg: String, title: String, artist: String) {
            val p = profiles.load(); if (!p.musicAwareness || states.load().sleeping) return
            val key = "music_${(pkg + title + artist).hashCode()}"
            if (!cooldown.allow(key, 4 * 60_000L)) return
            val desc = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" — ").take(180)
            if (desc.isBlank()) return
            val text = when (p.speechStyle) { "毒舌" -> "喔？在聽《$desc》。我先不評論你的品味。"; "活潑" -> "欸，是《$desc》！這首現在陪你一起聽。"; else -> "你正在聽《$desc》。我也注意到了。" }
            say(text, EmotionCatalog.PUZZLED, "音樂")
        }

        fun onPhoneNotification(pkg: String, title: String, text: String) {
            val p = profiles.load(); if (!p.notificationAwareness || states.load().sleeping) return
            if (!cooldown.allow("notification_$pkg", 20_000L)) return
            val appName = appName(pkg)
            val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            val message = when {
                p.notificationDetail == "顯示內容" && !locked -> listOf(appName, title, text).filter { it.isNotBlank() }.joinToString("：").take(260).ifBlank { "你有新的通知。" }
                p.notificationDetail in listOf("顯示 App", "顯示內容") -> if (appName.isBlank()) "你有新的通知。" else "$appName 有新的通知。"
                else -> "你有新的通知。"
            }
            say(message, EmotionCatalog.PUZZLED, "通知")
        }

        private fun appName(pkg: String): String = try {
            val info = packageManager.getApplicationInfo(pkg, 0); packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) { pkg.substringAfterLast('.') }

        private fun applyCharacterScale(scale: Float) {
            val s = scale.coerceIn(0.6f, 1.6f); image.layoutParams = FrameLayout.LayoutParams(dp((210 * s).toInt()), dp((240 * s).toInt()), Gravity.CENTER)
        }
        private fun bubbleLayout(pos: String) = FrameLayout.LayoutParams(dp(350), WindowManager.LayoutParams.WRAP_CONTENT,
            when (pos) { "中間" -> Gravity.CENTER; "下方" -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL }).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
        private fun btn(t: String, c: () -> Unit) = Button(this@OverlayService).apply { text = t; textSize = 11f; minHeight = 0; minWidth = 0; setPadding(dp(6), 0, dp(6), 0); layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(1), 0, dp(1), 0) }; setOnClickListener { c() } }

        private fun touch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downAt = System.currentTimeMillis(); downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false; return true }
                MotionEvent.ACTION_MOVE -> { val dx = e.rawX - downX; val dy = e.rawY - downY; if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true; if (moved) { lp.x = startX - dx.toInt(); lp.y = startY - dy.toInt(); wm.updateViewLayout(root, lp) }; return true }
                MotionEvent.ACTION_UP -> { val s = states.load(); s.overlayX = lp.x; s.overlayY = lp.y; s.lastInteraction = System.currentTimeMillis(); states.save(s); if (!moved && !s.sleeping) { if (System.currentTimeMillis() - downAt >= 700) longPress() else tap(e.y / v.height.coerceAtLeast(1).toFloat()) }; return true }
            }; return false
        }
        private fun tap(y: Float) {
            val p = profiles.load(); val u = p.userName.ifBlank { "你" }
            val r = when { y < .34f -> listOf("欸，別一直戳頭。", "嗯？頭上有東西嗎？", "$u，你在確認我還在？").random() to EmotionCatalog.PUZZLED; y < .72f -> listOf("嗯？", "我在。", "怎麼了？").random() to EmotionCatalog.CALM; else -> listOf("喂，下面也要戳？", "你想把我搬去哪？", "……很閒嘛。").random() to EmotionCatalog.CONFUSED }
            say(r.first, r.second, "點擊")
        }
        private fun longPress() { val p = profiles.load(); say(when (p.speechStyle) { "毒舌" -> "你打算按到明年嗎？"; "傲嬌" -> "放、放手啦！"; "溫柔" -> "嗯？你想叫住我嗎？"; else -> "……你按得有點久喔。" }, EmotionCatalog.CONFUSED, "長按") }
        private fun toggleSleep() {
            val s = states.load(); s.sleeping = !s.sleeping; s.lastInteraction = System.currentTimeMillis(); s.currentEmotion = if (s.sleeping) EmotionCatalog.SLEEPING else EmotionCatalog.CALM; s.emotionSince = System.currentTimeMillis(); states.save(s)
            if (s.sleeping) { bubble.text = "……zzz……"; sleep.text = "醒來"; setEmotion(EmotionCatalog.SLEEPING, false); miniImage.visibility = View.GONE; reactionAudio.playUsage("睡覺") }
            else { val p = profiles.load(); bubble.text = TimeEngine.greeting(p); sleep.text = "睡覺"; setEmotion(EmotionCatalog.CALM, false); if (!reactionAudio.playUsage("醒來")) voice.speak(bubble.text.toString(), p) }
        }

        private fun chat() {
            val p = profiles.load(); val input = EditText(this@OverlayService).apply { hint = "跟 ${p.characterName} 說點什麼……"; setSingleLine(false); minLines = 2 }
            val d = AlertDialog.Builder(this@OverlayService, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle(p.characterName).setView(input)
                .setNeutralButton("貼圖") { _, _ -> root.post { stickerDialog() } }.setNegativeButton("取消", null).setPositiveButton("送出") { _, _ ->
                    val q = input.text.toString().trim(); if (q.isBlank()) return@setPositiveButton
                    val history = if (p.memoryEnabled) memories.load() else emptyList()
                    if (p.memoryEnabled) memories.add(MemoryItem(System.currentTimeMillis(), p.userName.ifBlank { "你" }, q, EmotionEngine.infer(q, p)))
                    if (p.longTermMemoryEnabled) LongTermMemoryEngine.extractCandidate(q)?.let { longMemories.addOrMerge(it) }
                    if (p.aiEnabled && p.aiProvider != "本機") bubble.text = "……"
                    ai.replyAsync(p, q, history) { r ->
                        if (destroyed) return@replyAsync
                        if (p.memoryEnabled) memories.add(MemoryItem(System.currentTimeMillis(), p.characterName, r.text, r.emotion, if (r.usedFallback) "local_fallback" else r.provider.lowercase()))
                        say(r.text, r.emotion)
                        if (q.contains("睡覺") || q.contains("晚安")) root.postDelayed({ if (!states.load().sleeping) toggleSleep() }, 1200)
                    }
                }.create()
            d.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY); d.show()
        }

        private fun stickerDialog() {
            val items = stickers.load(); if (items.isEmpty()) { say("你還沒有設定貼圖或顏文字。可以到角色設定裡新增。", EmotionCatalog.PUZZLED); return }
            val labels = items.map { if (it.type == StickerItem.Type.KAOMOJI) "${it.value}  ${it.label}" else "🖼 ${it.label}" }.toTypedArray()
            val d = AlertDialog.Builder(this@OverlayService, android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("選一個小貼圖").setItems(labels) { _, which ->
                val item = items[which]; val r = PhotoReactionEngine.sticker(profiles.load())
                if (item.type == StickerItem.Type.IMAGE) showMiniImage(item.value) else bubble.text = "${item.value}\n${r.text}"
                if (item.type == StickerItem.Type.IMAGE) say(r.text, r.emotion) else { setEmotion(r.emotion, true); voice.speak(r.text, profiles.load()) }
            }.setNegativeButton("取消", null).create(); d.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY); d.show()
        }

        private fun pickPhoto() { startActivity(Intent(this@OverlayService, PhotoReactionActivity::class.java).putExtra(EXTRA_CHARACTER_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        private fun showMiniImage(ref: String) { try { miniImage.setImageURI(Uri.parse(ref)); miniImage.visibility = View.VISIBLE; miniImage.bringToFront(); root.postDelayed({ miniImage.visibility = View.GONE }, 6500) } catch (_: Exception) { miniImage.visibility = View.GONE } }
        private fun say(t: String, e: String, clipUsage: String? = null) { bubble.text = t; setEmotion(e, true); val played = clipUsage?.let { reactionAudio.playUsage(it) } ?: false; if (!played) voice.speak(t, profiles.load()) }
        private fun setEmotion(e: String, persist: Boolean) {
            val uri = profiles.emotionImage(e) ?: profiles.emotionImage(EmotionCatalog.CALM)
            if (!uri.isNullOrBlank()) try { image.setImageURI(Uri.parse(uri)) } catch (_: Exception) { image.setImageResource(R.drawable.companion_placeholder) } else image.setImageResource(R.drawable.companion_placeholder)
            if (persist) { val s = states.load(); s.currentEmotion = e; s.emotionSince = System.currentTimeMillis(); states.save(s) }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        runCatching { unregisterReceiver(batteryReceiver) }; runCatching { audioManager.unregisterAudioDeviceCallback(audioCallback) }
        windows.values.toList().forEach { it.destroy() }; windows.clear(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "virtual_companion_overlay"
        const val NOTIFICATION_ID = 4102
        const val ACTION_START_CHARACTER = "vc.START_CHARACTER"
        const val ACTION_STOP_CHARACTER = "vc.STOP_CHARACTER"
        const val ACTION_REFRESH_CHARACTER = "vc.REFRESH_CHARACTER"
        const val ACTION_START_ALL = "vc.START_ALL"
        const val ACTION_REACT_IMAGE = "vc.REACT_IMAGE"
        const val ACTION_PHONE_NOTIFICATION = "vc.PHONE_NOTIFICATION"
        const val ACTION_MUSIC_CHANGED = "vc.MUSIC_CHANGED"
        const val EXTRA_CHARACTER_ID = "characterId"
        const val EXTRA_IMAGE_URI = "imageUri"
        const val EXTRA_PACKAGE_NAME = "packageName"
        const val EXTRA_NOTIFICATION_TITLE = "notificationTitle"
        const val EXTRA_NOTIFICATION_TEXT = "notificationText"
        const val EXTRA_MEDIA_TITLE = "mediaTitle"
        const val EXTRA_MEDIA_ARTIST = "mediaArtist"
    }
}
