package com.example.virtualcompanion.model

data class CharacterProfile(
    val characterId: String,
    var userName: String = "",
    var characterName: String = "我的夥伴",
    var relationship: String = "朋友",
    var personality: String = "",
    var speechStyle: String = "自然",
    var catchphrase: String = "",
    var authorName: String = "",
    var description: String = "",
    var coverImageRef: String = "",
    var customBubbleImageRef: String = "",
    var bubblePosition: String = "上方",
    var bubbleStyle: String = "圓角",
    var voiceEnabled: Boolean = true,
    var speechRate: Float = 1.0f,
    var speechPitch: Float = 1.0f,
    var memoryEnabled: Boolean = true,
    /** 立繪槽位數：至少 7、最多 38。 */
    var portraitSlotCount: Int = EmotionCatalog.MIN_PORTRAIT_SLOTS,
    /** 桌寵立繪顯示倍率，0.6x ~ 1.6x。 */
    var characterScale: Float = 1.0f,
    /** 手機狀態感知皆預設關閉，由使用者自行開啟。 */
    var batteryAwareness: Boolean = false,
    var headphoneAwareness: Boolean = false,
    var musicAwareness: Boolean = false,
    var notificationAwareness: Boolean = false,
    /** 只提醒 / 顯示 App / 顯示內容 */
    var notificationDetail: String = "只提醒",
    /** AI / Vision 預設完全關閉；API Key 另外存於 Android Keystore 加密的 SecretStore。 */
    var aiEnabled: Boolean = false,
    var aiProvider: String = "本機",
    var aiModel: String = "gemini-3.8-flash",
    var aiEndpoint: String = "",
    var visionEnabled: Boolean = false,
    var longTermMemoryEnabled: Boolean = true,
    /** 語音 Provider：Android TTS 或使用者自己的 HTTPS TTS endpoint。 */
    var ttsProvider: String = "Android TTS",
    var ttsEndpoint: String = "",
    var ttsVoice: String = "",
    var createdAt: Long = System.currentTimeMillis()
)
