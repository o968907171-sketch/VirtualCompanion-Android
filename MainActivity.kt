package com.example.virtualcompanion.ui

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import com.example.virtualcompanion.data.*
import com.example.virtualcompanion.ai.AiCoordinator
import com.example.virtualcompanion.engine.LongTermMemoryEngine
import com.example.virtualcompanion.importer.MemoryImportEngine
import com.example.virtualcompanion.model.CharacterProfile
import com.example.virtualcompanion.model.EmotionCatalog
import com.example.virtualcompanion.model.StickerItem
import com.example.virtualcompanion.pack.CharacterPackManager
import com.example.virtualcompanion.service.OverlayService

class MainActivity : android.app.Activity() {
    private lateinit var directory: CharacterDirectory
    private lateinit var profiles: ProfileRepository
    private lateinit var memories: MemoryRepository
    private lateinit var longMemories: LongTermMemoryRepository
    private lateinit var stickers: StickerRepository
    private lateinit var voiceClips: VoiceClipRepository
    private lateinit var profile: CharacterProfile

    private lateinit var userName: EditText
    private lateinit var characterName: EditText
    private lateinit var personality: EditText
    private lateinit var catchphrase: EditText
    private lateinit var authorName: EditText
    private lateinit var descriptionField: EditText
    private lateinit var relationship: Spinner
    private lateinit var speechStyle: Spinner
    private lateinit var bubblePosition: Spinner
    private lateinit var bubbleStyle: Spinner
    private lateinit var voiceEnabled: CheckBox
    private lateinit var memoryEnabled: CheckBox
    private lateinit var speechRate: Spinner
    private lateinit var speechPitch: Spinner
    private lateinit var portraitCount: Spinner
    private lateinit var characterScale: Spinner
    private lateinit var batteryAwareness: CheckBox
    private lateinit var headphoneAwareness: CheckBox
    private lateinit var musicAwareness: CheckBox
    private lateinit var notificationAwareness: CheckBox
    private lateinit var notificationDetail: Spinner
    private lateinit var strictOfflineMode: CheckBox
    private lateinit var aiEnabled: CheckBox
    private lateinit var visionEnabled: CheckBox
    private lateinit var longTermMemoryEnabled: CheckBox
    private lateinit var aiProvider: Spinner
    private lateinit var aiModel: EditText
    private lateinit var aiEndpoint: EditText
    private lateinit var aiKeyInput: EditText
    private lateinit var ttsProvider: Spinner
    private lateinit var ttsEndpoint: EditText
    private lateinit var ttsVoice: EditText
    private lateinit var ttsKeyInput: EditText

    private var pendingEmotion: String? = null
    private var pendingImportSource = MemoryImportEngine.Source.AUTO
    private var pendingStart = false
    private var pendingExportMemories = false
    private var pendingVoiceUsage = "通用"
    private val currentId get() = directory.currentId()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        directory = CharacterDirectory(this)
        intent.getStringExtra("characterId")?.let { directory.setCurrent(it) }
        loadRepos(); render()
    }

    override fun onResume() {
        super.onResume()
        if (pendingStart && Settings.canDrawOverlays(this)) { pendingStart = false; startCharacter(currentId) }
    }

    private fun loadRepos() {
        profiles = ProfileRepository(this, currentId)
        memories = MemoryRepository(this, currentId)
        longMemories = LongTermMemoryRepository(this, currentId)
        stickers = StickerRepository(this, currentId)
        voiceClips = VoiceClipRepository(this, currentId)
        profile = profiles.load()
    }

    private fun render() {
        loadRepos()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(40))
            setBackgroundColor(Color.rgb(250, 248, 255))
        }
        root.addView(TextView(this).apply { text = "Virtual Companion\n角色工作室 v0.7.1"; textSize = 28f; setTextColor(Color.rgb(40, 36, 46)) })

        root.addView(section("多角色"))
        val ids = directory.ids()
        val names = ids.map { ProfileRepository(this, it).load().characterName + if (directory.isActive(it)) "  ●" else "" }
        val chooser = spinner(names, names[ids.indexOf(currentId)])
        chooser.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, l: Long) {
                val id = ids[pos]
                if (id != currentId) { saveForm(); directory.setCurrent(id); render() }
            }
        }
        root.addView(chooser)
        val row = LinearLayout(this)
        row.addView(action("＋ 新角色") {
            saveForm(); val input = EditText(this).apply { hint = "角色名字" }
            AlertDialog.Builder(this).setTitle("建立角色").setView(input).setNegativeButton("取消", null).setPositiveButton("建立") { _, _ -> directory.create(input.text.toString()); render() }.show()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(action("複製") { saveForm(); directory.duplicate(currentId); render() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(action("刪除") { confirmDelete() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(row)
        root.addView(note("可以同時啟動多隻角色；每隻都有自己的立繪、貼圖、情緒、記憶、位置與睡眠狀態。"))
        root.addView(action("💬 多人聊天室 / 角色彼此關係") { saveForm(); startActivity(Intent(this, GroupChatActivity::class.java)) })
        root.addView(note("群聊中的角色會各自回覆，也會記住彼此的好感、信任、依戀、吃醋、競爭與不滿。可以把衝突強度設成和平、輕微、自然或戲劇化。"))

        userName = field("你的名字", profile.userName)
        characterName = field("角色名字", profile.characterName)
        personality = field("角色個性（自然語言描述）", profile.personality, 3)
        catchphrase = field("口頭禪（可空白）", profile.catchphrase)
        authorName = field("角色包作者（可空白）", profile.authorName)
        descriptionField = field("角色介紹／角色包說明（可空白）", profile.description, 3)
        root.addView(userName); root.addView(characterName); root.addView(personality); root.addView(catchphrase)
        root.addView(authorName); root.addView(descriptionField)

        root.addView(section("角色包外觀"))
        root.addView(note("封面用於角色包預覽；自訂對話框背景會直接套用到桌寵的文字框。"))
        val coverPreview = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; adjustViewBounds = true; minimumHeight = dp(120) }
        if (profile.coverImageRef.isNotBlank()) try { coverPreview.setImageURI(Uri.parse(profile.coverImageRef)) } catch (_: Exception) {}
        root.addView(coverPreview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150)))
        val metaRow = LinearLayout(this)
        metaRow.addView(action(if (profile.coverImageRef.isBlank()) "設定封面" else "更換封面") { pickImage(REQ_COVER_IMAGE) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        metaRow.addView(action(if (profile.customBubbleImageRef.isBlank()) "自訂對話框圖" else "更換對話框圖") { pickImage(REQ_BUBBLE_IMAGE) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(metaRow)

        root.addView(section("關係與說話方式"))
        relationship = spinner(listOf("陌生人", "朋友", "好朋友", "家人", "搭檔", "戀人 / 情侶", "主僕", "自訂"), profile.relationship)
        speechStyle = spinner(listOf("自然", "溫柔", "可愛", "傲嬌", "毒舌", "活潑", "冷淡", "正經", "自訂"), profile.speechStyle)
        root.addView(label("關係")); root.addView(relationship); root.addView(label("說話風格")); root.addView(speechStyle)

        root.addView(section("立繪設定"))
        root.addView(note("不用準備幾十張圖。預設只顯示 7 個基本立繪槽位；需要時可增加，最多 38 個。沒有對應立繪的情緒會回退到「平靜」圖片。"))
        portraitCount = spinner((EmotionCatalog.MIN_PORTRAIT_SLOTS..EmotionCatalog.MAX_PORTRAIT_SLOTS).map { it.toString() }, profile.portraitSlotCount.toString())
        characterScale = spinner(listOf("0.6", "0.7", "0.8", "0.9", "1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6"), profile.characterScale.toString())
        root.addView(label("立繪槽位數（7～38）")); root.addView(portraitCount)
        root.addView(label("桌寵立繪大小")); root.addView(characterScale)
        root.addView(action("套用立繪數量 / 大小") { saveForm(); refreshCharacter(); render() })
        profiles.allEmotions().forEach { root.addView(emotionRow(it)) }
        root.addView(action("＋ 新增自訂情緒") { addEmotion() })

        root.addView(section("小貼圖與顏文字"))
        root.addView(note("可替每隻角色準備自己的圖片貼圖或顏文字。桌寵聊天時可以叫出貼圖，圖片會在角色旁邊短暫出現，角色也會回應。每角色最多 ${StickerRepository.MAX_ITEMS} 個。"))
        val stickerRow = LinearLayout(this)
        stickerRow.addView(action("＋ 圖片貼圖") { pickStickerImage() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        stickerRow.addView(action("＋ 顏文字") { addKaomoji() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        stickerRow.addView(action("管理 (${stickers.load().size})") { manageStickers() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(stickerRow)
        root.addView(action("給這隻桌寵看一張照片…") {
            saveForm(); startActivity(Intent(this, PhotoReactionActivity::class.java).putExtra(OverlayService.EXTRA_CHARACTER_ID, currentId))
        })
        root.addView(note("若啟用 Vision AI，角色會把你選的照片交給目前 AI Provider 理解；未啟用或連線失敗時仍會安全退回本機反應。"))

        root.addView(section("對話框"))
        bubblePosition = spinner(listOf("上方", "中間", "下方"), profile.bubblePosition)
        bubbleStyle = spinner(listOf("圓角", "漫畫框", "簡約", "透明", "自訂圖片"), profile.bubbleStyle)
        root.addView(label("位置")); root.addView(bubblePosition); root.addView(label("樣式")); root.addView(bubbleStyle)

        root.addView(section("語音與記憶"))
        voiceEnabled = CheckBox(this).apply { text = "朗讀角色回答"; isChecked = profile.voiceEnabled }
        memoryEnabled = CheckBox(this).apply { text = "保存對話記憶"; isChecked = profile.memoryEnabled }
        speechRate = spinner(listOf("0.8", "1.0", "1.2", "1.5"), profile.speechRate.toString())
        speechPitch = spinner(listOf("0.8", "1.0", "1.2", "1.4"), profile.speechPitch.toString())
        root.addView(voiceEnabled); root.addView(memoryEnabled); root.addView(label("語速")); root.addView(speechRate); root.addView(label("音調")); root.addView(speechPitch)
        root.addView(note("也可以加入自訂反應音檔。這些是點擊／睡覺／通知等事件用的短音訊，不會假裝成能朗讀任意文字的語音克隆。"))
        val voiceRow = LinearLayout(this)
        voiceRow.addView(action("＋ 反應音檔") { chooseVoiceClip() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        voiceRow.addView(action("管理 (${voiceClips.load().size})") { manageVoiceClips() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(voiceRow)
        ttsProvider = spinner(listOf("Android TTS", "自訂 HTTP"), profile.ttsProvider)
        ttsEndpoint = field("自訂 TTS HTTPS Endpoint（可空白）", profile.ttsEndpoint)
        ttsVoice = field("自訂 TTS voice 名稱 / ID（可空白）", profile.ttsVoice)
        ttsKeyInput = field(if (SecretStore(this).has("tts_custom")) "自訂 TTS API Key 已儲存；輸入新值可覆蓋" else "自訂 TTS API Key（不會放進角色包）", "")
        root.addView(label("語音 Provider")); root.addView(ttsProvider); root.addView(ttsEndpoint); root.addView(ttsVoice); root.addView(ttsKeyInput)
        val ttsKeyRow = LinearLayout(this)
        ttsKeyRow.addView(action("儲存 TTS Key") { saveTtsKey() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        ttsKeyRow.addView(action("清除 TTS Key") { SecretStore(this).remove("tts_custom"); toast("已清除 TTS Key"); render() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(ttsKeyRow)
        root.addView(note("自訂 HTTP TTS 使用 POST JSON {text, voice}；伺服器可直接回 audio/*，或 JSON 的 audio_base64。若呼叫失敗會自動退回 Android TTS。"))

        root.addView(section("離線優先 v0.6"))
        val offlineRepo = OfflineSettingsRepository(this)
        strictOfflineMode = CheckBox(this).apply {
            text = "嚴格離線模式：禁止所有外部 AI、Vision、HTTP TTS 網路請求"
            isChecked = offlineRepo.strictOffline()
            setOnCheckedChangeListener { _, checked ->
                offlineRepo.setStrictOffline(checked)
                Toast.makeText(this@MainActivity, if (checked) "已進入嚴格離線模式" else "已允許使用你選擇的雲端 Provider", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(strictOfflineMode)
        root.addView(note("開啟後，桌寵仍可使用：Overlay、多角色、立繪／貼圖、本機聊天與情緒、近期與長期記憶、時間、電量、耳機、睡眠、角色包。雲端 AI 與圖片理解不會送出任何網路請求。語音只使用裝置上可離線使用的 Android TTS voice；若裝置沒有離線 voice，角色仍會顯示文字但不強迫連網。"))

        root.addView(section("AI、Vision 與長期記憶 v0.6"))
        aiEnabled = CheckBox(this).apply { text = "啟用 AI 對話（關閉時完全使用本機對話引擎）"; isChecked = profile.aiEnabled }
        visionEnabled = CheckBox(this).apply { text = "允許目前 AI Provider 理解我主動選給角色看的照片"; isChecked = profile.visionEnabled }
        longTermMemoryEnabled = CheckBox(this).apply { text = "啟用長期記憶整理與召回"; isChecked = profile.longTermMemoryEnabled }
        aiProvider = spinner(listOf("本機", "Gemini", "Grok", "OpenAI-compatible"), profile.aiProvider)
        aiModel = field("模型名稱，例如 gemini-3.8-flash / grok-4.7", profile.aiModel)
        aiEndpoint = field("OpenAI-compatible Base URL（例如 https://example.com）", profile.aiEndpoint)
        val secret = SecretStore(this)
        aiKeyInput = field(if (secret.has(AiCoordinator.secretId(profile.aiProvider))) "此 Provider 的 API Key 已儲存；輸入新值可覆蓋" else "API Key（加密保存在本機，不會放進角色包）", "")
        root.addView(aiEnabled); root.addView(visionEnabled); root.addView(longTermMemoryEnabled)
        root.addView(label("AI Provider")); root.addView(aiProvider); root.addView(aiModel); root.addView(aiEndpoint); root.addView(aiKeyInput)
        val aiKeyRow = LinearLayout(this)
        aiKeyRow.addView(action("儲存 API Key") { saveAiKey() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        aiKeyRow.addView(action("清除此 Provider Key") { clearAiKey() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(aiKeyRow)
        root.addView(action("測試 AI 對話") { testAi() })
        root.addView(note("Gemini 使用官方 generateContent；Grok 使用 xAI 的 OpenAI-compatible API；其他 OpenAI-compatible 服務可自行填 HTTPS Base URL 與模型。API Key 以 Android Keystore 加密後保存在此裝置，不會跟角色包分享。外部 AI 服務是否免費、額度與價格由各服務商決定。嚴格離線模式開啟時，這些 Provider 設定會保留，但不會發出網路請求。"))
        root.addView(note("隱私：AI 開啟時，當前訊息、最多 18 則近期對話與最多 8 筆相關長期記憶可能送到你選擇的 Provider。圖片只有在你主動按『看圖』選擇照片、且 Vision 開啟時才會送出。"))
        root.addView(note("長期記憶目前共有 ${longMemories.count()} 筆。新對話會自動擷取明確的偏好、重要日期、計畫等；也可以手動從既有聊天整理。"))
        val longRow = LinearLayout(this)
        longRow.addView(action("整理近期記憶") { consolidateLongTerm() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        longRow.addView(action("查看長期記憶") { showLongTermMemories() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(longRow)
        root.addView(action("清除長期記憶") { confirmClearLongTerm() })

        root.addView(section("手機感知 v0.5"))
        batteryAwareness = CheckBox(this).apply { text = "感知電量／充電狀態"; isChecked = profile.batteryAwareness }
        headphoneAwareness = CheckBox(this).apply { text = "感知耳機接上／拔除"; isChecked = profile.headphoneAwareness }
        musicAwareness = CheckBox(this).apply { text = "感知目前播放的音樂"; isChecked = profile.musicAwareness }
        notificationAwareness = CheckBox(this).apply { text = "感知手機通知"; isChecked = profile.notificationAwareness }
        notificationDetail = spinner(listOf("只提醒", "顯示 App", "顯示內容"), profile.notificationDetail)
        root.addView(batteryAwareness); root.addView(headphoneAwareness); root.addView(musicAwareness); root.addView(notificationAwareness)
        root.addView(label("通知顯示程度")); root.addView(notificationDetail)
        root.addView(note("電量與耳機功能使用 Android 本機狀態。音樂與通知需要你另外在系統設定授權『通知存取』。預設所有感知都關閉；通知預設只說『有通知』。"))
        root.addView(action("前往 Android『通知存取』設定") {
            try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })

        root.addView(section("跨平台記憶匯入"))
        root.addView(note("可匯入 Gemini、Character.AI、Grok 或其他聊天／角色 App 的匯出檔。支援 ZIP、JSON、JSONL、TXT、Markdown、CSV、HTML；解析後會轉成此角色自己的本機記憶。"))
        val sources = MemoryImportEngine.Source.values().toList()
        val sourceSpinner = spinner(sources.map { it.label }, pendingImportSource.label)
        root.addView(sourceSpinner)
        root.addView(action("匯入記憶檔…") {
            saveForm(); pendingImportSource = sources[sourceSpinner.selectedItemPosition]
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, REQ_MEMORY)
        })
        root.addView(note("目前此角色共有 ${memories.count()} 筆記憶。匯入不會覆蓋原記憶。"))
        val mrow = LinearLayout(this)
        mrow.addView(action("查看記憶") { saveForm(); showMemories() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        mrow.addView(action("清除記憶") { confirmClear() }, LinearLayout.LayoutParams(0, dp(48), 1f)); root.addView(mrow)

        root.addView(section("角色包 Character Pack"))
        root.addView(note("角色包 v4 會包含角色設定、作者／介紹、封面、自訂對話框背景、7～38 張立繪、角色大小、圖片貼圖、顏文字、反應音檔，以及可選的近期／長期記憶。記憶仍預設不匯出；API Key 與 AI/Vision 開關永遠不放入角色包。"))
        val prow = LinearLayout(this)
        prow.addView(action("匯出角色包") { saveForm(); showExportPackDialog() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        prow.addView(action("匯入角色包") { saveForm(); startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, REQ_PACK_IMPORT) }, LinearLayout.LayoutParams(0, dp(48), 1f)); root.addView(prow)

        root.addView(section("v0.7 測試與權限診斷"))
        root.addView(note("這一區不會改動角色資料，只用來確認浮動視窗、通知與通知存取等 Android 權限是否準備好。"))
        root.addView(action("執行裝置診斷") { showRuntimeDiagnostics() })
        root.addView(action("要求通知權限（Android 13+）") { requestNotifications() })

        root.addView(section("角色常駐"))
        root.addView(action("儲存設定") { saveForm(); refreshCharacter(); toast("設定已儲存") })
        root.addView(action(if (directory.isActive(currentId)) "重新顯示 / 更新這隻角色" else "啟動這隻角色") { saveForm(); ensureOverlayThenStart() })
        root.addView(action("停止這隻角色") { stopCharacter(currentId); toast("已停止 ${profile.characterName}"); render() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun saveForm() {
        if (!::userName.isInitialized) return
        profile = profiles.load().apply {
            userName = this@MainActivity.userName.text.toString().trim()
            characterName = this@MainActivity.characterName.text.toString().trim().ifBlank { "我的夥伴" }
            personality = this@MainActivity.personality.text.toString().trim()
            catchphrase = this@MainActivity.catchphrase.text.toString().trim()
            authorName = this@MainActivity.authorName.text.toString().trim()
            description = this@MainActivity.descriptionField.text.toString().trim()
            relationship = this@MainActivity.relationship.selectedItem.toString()
            speechStyle = this@MainActivity.speechStyle.selectedItem.toString()
            bubblePosition = this@MainActivity.bubblePosition.selectedItem.toString()
            bubbleStyle = this@MainActivity.bubbleStyle.selectedItem.toString()
            voiceEnabled = this@MainActivity.voiceEnabled.isChecked
            memoryEnabled = this@MainActivity.memoryEnabled.isChecked
            speechRate = this@MainActivity.speechRate.selectedItem.toString().toFloatOrNull() ?: 1f
            speechPitch = this@MainActivity.speechPitch.selectedItem.toString().toFloatOrNull() ?: 1f
            portraitSlotCount = this@MainActivity.portraitCount.selectedItem.toString().toIntOrNull()?.coerceIn(7, 38) ?: 7
            characterScale = this@MainActivity.characterScale.selectedItem.toString().toFloatOrNull()?.coerceIn(0.6f, 1.6f) ?: 1f
            batteryAwareness = this@MainActivity.batteryAwareness.isChecked
            headphoneAwareness = this@MainActivity.headphoneAwareness.isChecked
            musicAwareness = this@MainActivity.musicAwareness.isChecked
            notificationAwareness = this@MainActivity.notificationAwareness.isChecked
            notificationDetail = this@MainActivity.notificationDetail.selectedItem.toString()
            aiEnabled = this@MainActivity.aiEnabled.isChecked
            visionEnabled = this@MainActivity.visionEnabled.isChecked
            longTermMemoryEnabled = this@MainActivity.longTermMemoryEnabled.isChecked
            aiProvider = this@MainActivity.aiProvider.selectedItem.toString()
            aiModel = this@MainActivity.aiModel.text.toString().trim()
            aiEndpoint = this@MainActivity.aiEndpoint.text.toString().trim()
            ttsProvider = this@MainActivity.ttsProvider.selectedItem.toString()
            ttsEndpoint = this@MainActivity.ttsEndpoint.text.toString().trim()
            ttsVoice = this@MainActivity.ttsVoice.text.toString().trim()
        }
        profiles.save(profile)
        if (::strictOfflineMode.isInitialized) OfflineSettingsRepository(this).setStrictOffline(strictOfflineMode.isChecked)
    }

    private fun emotionRow(e: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val img = ImageView(this@MainActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            profiles.emotionImage(e)?.let { try { setImageURI(Uri.parse(it)) } catch (_: Exception) {} }
            if (drawable == null) setImageResource(android.R.drawable.ic_menu_gallery)
        }
        addView(img, LinearLayout.LayoutParams(dp(52), dp(52)).apply { setMargins(0, 0, dp(10), 0) })
        addView(TextView(this@MainActivity).apply { text = e; textSize = 16f }, LinearLayout.LayoutParams(0, dp(52), 1f))
        addView(Button(this@MainActivity).apply {
            text = if (profiles.emotionImage(e) == null) "匯入" else "更換"
            setOnClickListener {
                saveForm(); pendingEmotion = e
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, REQ_IMAGE)
            }
        })
    }

    override fun onActivityResult(r: Int, c: Int, data: Intent?) {
        super.onActivityResult(r, c, data); if (c != RESULT_OK) return; val uri = data?.data ?: return
        when (r) {
            REQ_IMAGE -> { persistRead(uri); pendingEmotion?.let { profiles.setEmotionImage(it, uri.toString()) }; refreshCharacter(); render() }
            REQ_STICKER_IMAGE -> { persistRead(uri); if (!stickers.addImage(queryName(uri) ?: "圖片貼圖", uri.toString())) toast("貼圖已達上限"); render() }
            REQ_COVER_IMAGE -> { persistRead(uri); profile = profiles.load().apply { coverImageRef = uri.toString() }; profiles.save(profile); render() }
            REQ_BUBBLE_IMAGE -> { persistRead(uri); profile = profiles.load().apply { customBubbleImageRef = uri.toString(); bubbleStyle = "自訂圖片" }; profiles.save(profile); refreshCharacter(); render() }
            REQ_VOICE_CLIP -> { persistRead(uri); if (!voiceClips.add(queryName(uri) ?: "反應音檔", pendingVoiceUsage, uri.toString())) toast("反應音檔已達上限"); render() }
            REQ_MEMORY -> importMemory(uri)
            REQ_PACK_EXPORT -> exportPack(uri)
            REQ_PACK_IMPORT -> importPack(uri)
        }
    }

    private fun pickImage(requestCode: Int) {
        saveForm()
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, requestCode)
    }

    private fun chooseVoiceClip() {
        val usages = VoiceClipRepository.USAGES.toTypedArray()
        AlertDialog.Builder(this).setTitle("這個音檔要用在哪裡？").setItems(usages) { _, which ->
            pendingVoiceUsage = usages[which]
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, REQ_VOICE_CLIP)
        }.setNegativeButton("取消", null).show()
    }

    private fun manageVoiceClips() {
        val items = voiceClips.load()
        if (items.isEmpty()) { toast("目前沒有自訂反應音檔"); return }
        val labels = items.map { "🔊 [${it.usage}] ${it.label}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("反應音檔").setItems(labels) { _, which ->
            val clip = items[which]
            val actions = arrayOf("播放預覽", "刪除")
            AlertDialog.Builder(this).setTitle(clip.label).setItems(actions) { _, action ->
                if (action == 0) {
                    try { MediaPlayer.create(this, Uri.parse(clip.ref))?.apply { setOnCompletionListener { it.release() }; start() } }
                    catch (_: Exception) { toast("無法播放此音檔") }
                } else { voiceClips.remove(clip.id); render() }
            }.show()
        }.setNegativeButton("關閉", null).show()
    }

    private fun pickStickerImage() {
        saveForm(); startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, REQ_STICKER_IMAGE)
    }

    private fun addKaomoji() {
        val input = EditText(this).apply { hint = "例如：(˶ᵔ ᵕ ᵔ˶)  或  ಠ_ಠ" }
        AlertDialog.Builder(this).setTitle("新增顏文字").setView(input).setNegativeButton("取消", null).setPositiveButton("新增") { _, _ ->
            if (!stickers.addKaomoji(input.text.toString())) toast("無法新增：內容空白或已達上限") else render()
        }.show()
    }

    private fun manageStickers() {
        val items = stickers.load()
        if (items.isEmpty()) { toast("目前沒有貼圖"); return }
        val labels = items.map { if (it.type == StickerItem.Type.KAOMOJI) "${it.value}  ${it.label}" else "🖼 ${it.label}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("管理貼圖（點選可刪除）").setItems(labels) { _, which ->
            val item = items[which]
            AlertDialog.Builder(this).setTitle("刪除 ${item.label}？").setNegativeButton("取消", null).setPositiveButton("刪除") { _, _ -> stickers.remove(item.id); render() }.show()
        }.setNegativeButton("關閉", null).show()
    }

    private fun persistRead(uri: Uri) { try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {} }

    private fun showExportPackDialog() {
        val include = CheckBox(this).apply { text = "包含這隻角色的對話記憶（可能含私人內容）"; isChecked = false }
        AlertDialog.Builder(this).setTitle("匯出 ${profile.characterName}").setMessage("角色設定、立繪與貼圖一定會包含。對話記憶預設不匯出，只有你勾選後才會加入角色包。")
            .setView(include).setNegativeButton("取消", null).setPositiveButton("選擇儲存位置") { _, _ ->
                pendingExportMemories = include.isChecked
                val safeName = profile.characterName.replace(Regex("[\\/:*?\"<>|]+"), "_").trim().ifBlank { "character" }.take(60)
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE, "$safeName.companionpack") }, REQ_PACK_EXPORT)
            }.show()
    }

    private fun exportPack(uri: Uri) {
        try {
            val result = contentResolver.openOutputStream(uri, "w")?.use { CharacterPackManager.export(this, currentId, it, pendingExportMemories) } ?: error("無法建立輸出檔案")
            AlertDialog.Builder(this).setTitle("角色包已匯出").setMessage("角色：${profile.characterName}\n立繪：${result.images} 張\n貼圖／顏文字：${result.stickers} 個\n反應音檔：${result.voiceClips} 個\n記憶：${result.memories} 筆\n\n你現在可以保存或分享這個 .companionpack 檔案。")
                .setPositiveButton("好", null).show()
        } catch (e: Exception) { AlertDialog.Builder(this).setTitle("匯出失敗").setMessage(e.message ?: "未知錯誤").setPositiveButton("好", null).show() }
    }

    private fun importPack(uri: Uri) {
        try {
            val preview = contentResolver.openInputStream(uri)?.use { CharacterPackManager.preview(this, it) } ?: error("無法讀取角色包")
            val author = if (preview.authorName.isBlank()) "未填寫" else preview.authorName
            val memoryNote = if (preview.includesMemories) "包含對話記憶（匯入後會加入新角色）" else "不包含對話記憶"
            val message = buildString {
                append("角色：${preview.characterName}\n")
                append("作者：$author\n")
                append("角色包版本：v${preview.version}\n")
                append("立繪：${preview.images} 張\n貼圖／顏文字：${preview.stickers} 個\n反應音檔：${preview.voiceClips} 個\n")
                append("$memoryNote\n\n")
                if (preview.description.isNotBlank()) append(preview.description.take(900))
                append("\n\n匯入後會建立『新角色』，不會覆蓋你現有的角色。手機感知設定也會保持關閉。")
            }
            AlertDialog.Builder(this).setTitle("角色包預覽").setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("匯入") { _, _ -> performPackImport(uri) }
                .show()
        } catch (e: Exception) { AlertDialog.Builder(this).setTitle("無法讀取角色包").setMessage(e.message ?: "角色包格式不正確").setPositiveButton("好", null).show() }
    }

    private fun performPackImport(uri: Uri) {
        try {
            val result = contentResolver.openInputStream(uri)?.use { CharacterPackManager.import(this, it) } ?: error("無法讀取角色包")
            val extra = if (result.warnings.isEmpty()) "" else "\n\n注意：\n" + result.warnings.take(6).joinToString("\n")
            AlertDialog.Builder(this).setTitle("角色包匯入完成").setMessage("已建立：${result.characterName}\n立繪：${result.images} 張\n貼圖／顏文字：${result.stickers} 個\n反應音檔：${result.voiceClips} 個\n記憶：${result.memories} 筆$extra")
                .setPositiveButton("切換到角色") { _, _ -> render() }.show()
        } catch (e: Exception) { AlertDialog.Builder(this).setTitle("匯入失敗").setMessage(e.message ?: "角色包格式不正確").setPositiveButton("好", null).show() }
    }

    private fun importMemory(uri: Uri) {
        val name = queryName(uri) ?: "memory.txt"
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val result = MemoryImportEngine.import(stream, name, pendingImportSource, profile.userName, profile.characterName)
                memories.addAll(result.items)
                AlertDialog.Builder(this).setTitle("匯入完成").setMessage("來源：${pendingImportSource.label}\n讀取檔案：${result.filesRead}\n匯入記憶：${result.items.size}\n目前總記憶：${memories.count()}" + (if (result.warnings.isEmpty()) "" else "\n\n部分警告：\n" + result.warnings.take(5).joinToString("\n"))).setPositiveButton("好") { _, _ -> render() }.show()
            }
        } catch (e: Exception) { AlertDialog.Builder(this).setTitle("無法匯入").setMessage(e.message ?: "未知錯誤").setPositiveButton("好", null).show() }
    }

    private fun saveAiKey() {
        saveForm()
        val provider = aiProvider.selectedItem.toString()
        if (provider == "本機") { toast("本機 Provider 不需要 API Key"); return }
        val value = aiKeyInput.text.toString().trim()
        if (value.isBlank()) { toast("請先輸入 API Key"); return }
        SecretStore(this).put(AiCoordinator.secretId(provider), value)
        aiKeyInput.text.clear(); toast("API Key 已加密保存在此裝置"); render()
    }

    private fun clearAiKey() {
        val provider = aiProvider.selectedItem.toString()
        SecretStore(this).remove(AiCoordinator.secretId(provider)); toast("已清除 $provider API Key"); render()
    }

    private fun saveTtsKey() {
        val value = ttsKeyInput.text.toString().trim()
        if (value.isBlank()) { toast("請先輸入 TTS API Key"); return }
        SecretStore(this).put("tts_custom", value); ttsKeyInput.text.clear(); toast("TTS Key 已加密保存在此裝置"); render()
    }

    private fun testAi() {
        saveForm()
        val coordinator = AiCoordinator(this, currentId)
        toast("正在測試 AI…")
        coordinator.replyAsync(profiles.load(), "請用一句符合你角色性格的話向我打招呼。", memories.load().takeLast(12)) { r ->
            coordinator.shutdown()
            val extra = if (r.usedFallback) "\n\n已退回本機模式：${r.error ?: "未知原因"}" else "\n\nProvider：${r.provider}"
            AlertDialog.Builder(this).setTitle("AI 測試結果").setMessage(r.text + extra).setPositiveButton("好", null).show()
        }
    }

    private fun consolidateLongTerm() {
        saveForm()
        val p = profiles.load()
        val items = LongTermMemoryEngine.consolidate(memories.load().takeLast(800), p.characterName)
        longMemories.addAll(items)
        AlertDialog.Builder(this).setTitle("長期記憶整理完成").setMessage("這次找到 ${items.size} 個候選記憶；去重合併後目前共有 ${longMemories.count()} 筆。")
            .setPositiveButton("好") { _, _ -> render() }.show()
    }

    private fun showLongTermMemories() {
        val items = longMemories.load().sortedWith(compareByDescending<com.example.virtualcompanion.model.LongTermMemoryItem> { it.importance }.thenByDescending { it.lastUsedAt })
        val text = if (items.isEmpty()) "目前沒有長期記憶。" else items.take(100).joinToString("\n\n") { "★".repeat(it.importance) + "  " + it.summary + if (it.tags.isEmpty()) "" else "\n#" + it.tags.joinToString(" #") }
        AlertDialog.Builder(this).setTitle("${profile.characterName} 的長期記憶").setMessage(text).setPositiveButton("關閉", null).show()
    }

    private fun confirmClearLongTerm() {
        AlertDialog.Builder(this).setTitle("清除長期記憶？").setMessage("只會清除此角色整理出的長期記憶；原始聊天紀錄不會因此刪除。")
            .setNegativeButton("取消", null).setPositiveButton("清除") { _, _ -> longMemories.clear(); render() }.show()
    }

    private fun queryName(uri: Uri): String? { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) return it.getString(0) }; return null }
    private fun ensureOverlayThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            pendingStart = true
            AlertDialog.Builder(this).setTitle("需要浮動視窗權限").setMessage("只用來讓你建立的角色顯示在其他 App 上方。").setNegativeButton("取消", null).setPositiveButton("前往設定") { _, _ -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }.show()
        } else {
            requestNotifications()
            startCharacter(currentId)
        }
    }
    private fun startCharacter(id: String) {
        val i = Intent(this, OverlayService::class.java)
            .setAction(OverlayService.ACTION_START_CHARACTER)
            .putExtra(OverlayService.EXTRA_CHARACTER_ID, id)
        try {
            directory.setActive(id, true)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            toast("角色已啟動")
        } catch (e: RuntimeException) {
            directory.setActive(id, false)
            AlertDialog.Builder(this)
                .setTitle("角色啟動失敗")
                .setMessage("Android 沒有允許常駐服務啟動。請確認浮動視窗權限與系統背景限制後再試一次。\n\n${e.javaClass.simpleName}")
                .setPositiveButton("好", null)
                .show()
        }
        render()
    }
    private fun stopCharacter(id: String) { directory.setActive(id, false); startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP_CHARACTER).putExtra(OverlayService.EXTRA_CHARACTER_ID, id)) }
    private fun refreshCharacter() { if (directory.isActive(currentId)) startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_REFRESH_CHARACTER).putExtra(OverlayService.EXTRA_CHARACTER_ID, currentId)) }
    private fun showMemories() { val items = memories.load().takeLast(80); val text = if (items.isEmpty()) "目前沒有記憶。" else items.joinToString("\n\n") { "[${it.source}] ${it.speaker}: ${it.text}" }; AlertDialog.Builder(this).setTitle("${profile.characterName} 的記憶").setMessage(text).setPositiveButton("關閉", null).show() }
    private fun confirmClear() { AlertDialog.Builder(this).setTitle("清除這隻角色的記憶？").setMessage("只會清除 ${profile.characterName} 的記憶，不影響其他角色。").setNegativeButton("取消", null).setPositiveButton("清除") { _, _ -> memories.clear(); render() }.show() }
    private fun confirmDelete() { if (directory.ids().size <= 1) { toast("至少要保留一隻角色"); return }; AlertDialog.Builder(this).setTitle("刪除 ${profile.characterName}？").setMessage("角色設定、立繪、貼圖與記憶都會刪除。").setNegativeButton("取消", null).setPositiveButton("刪除") { _, _ -> stopCharacter(currentId); directory.delete(currentId); render() }.show() }
    private fun addEmotion() {
        if (profiles.allEmotions().size >= EmotionCatalog.MAX_PORTRAIT_SLOTS) { toast("最多 38 個立繪／情緒槽位"); return }
        val input = EditText(this).apply { hint = "例如：吃醋、思考、期待" }
        AlertDialog.Builder(this).setTitle("新增自訂情緒").setView(input).setNegativeButton("取消", null).setPositiveButton("新增") { _, _ -> profiles.addCustomEmotion(input.text.toString()); render() }.show()
    }
    private fun showRuntimeDiagnostics() {
        val overlay = if (Settings.canDrawOverlays(this)) "✓ 已允許" else "✗ 尚未允許"
        val notification = if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "✓ 已允許 / 不需要" else "△ 尚未允許"
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        val notificationListener = if (enabledListeners.contains(packageName, ignoreCase = true)) "✓ 已授權" else "△ 未授權"
        val active = directory.activeIds().size
        val strictOffline = OfflineSettingsRepository(this).strictOffline()
        val message = buildString {
            append("版本：0.7.1\n")
            append("浮動視窗：$overlay\n")
            append("通知顯示：$notification\n")
            append("通知／音樂存取：$notificationListener\n")
            append("已啟動角色：$active 隻\n")
            append("嚴格離線模式：${if (strictOffline) "開啟" else "關閉"}\n\n")
            append("提示：通知／音樂存取只有在你主動開啟相關感知功能時才需要。")
        }
        AlertDialog.Builder(this).setTitle("Virtual Companion 裝置診斷").setMessage(message)
            .setNegativeButton("關閉", null)
            .setNeutralButton("通知存取設定") { _, _ ->
                try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
                catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
            .setPositiveButton("浮動視窗設定") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }.show()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 900)
        }
    }
    private fun section(t: String) = TextView(this).apply { text = t; textSize = 20f; setTextColor(Color.rgb(70, 58, 88)); setPadding(0, dp(24), 0, dp(8)) }
    private fun label(t: String) = TextView(this).apply { text = t; textSize = 14f; setPadding(0, dp(10), 0, 0) }
    private fun note(t: String) = TextView(this).apply { text = t; textSize = 13f; setTextColor(Color.DKGRAY); setPadding(0, dp(4), 0, dp(8)) }
    private fun field(h: String, v: String, lines: Int = 1) = EditText(this).apply { hint = h; setText(v); minLines = lines }
    private fun spinner(xs: List<String>, current: String) = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, xs); setSelection(xs.indexOf(current).coerceAtLeast(0)) }
    private fun action(t: String, c: () -> Unit) = Button(this).apply { text = t; setOnClickListener { c() } }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val REQ_IMAGE = 3001
        const val REQ_MEMORY = 3002
        const val REQ_PACK_EXPORT = 3003
        const val REQ_PACK_IMPORT = 3004
        const val REQ_STICKER_IMAGE = 3005
        const val REQ_COVER_IMAGE = 3006
        const val REQ_BUBBLE_IMAGE = 3007
        const val REQ_VOICE_CLIP = 3008
    }
}
