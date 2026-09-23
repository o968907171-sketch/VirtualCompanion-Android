# Architecture v0.6.1

## 角色層

- `CharacterDirectory`：多角色索引、目前角色、啟用角色、複製 / 刪除。
- `ProfileRepository`：人格、關係、立繪、對話框、AI / Vision / TTS 設定。
- `StateRepository`：即時情緒、睡眠、Overlay 位置。
- `CharacterAssetStore`：角色包匯入後的私有素材。

## 對話 / AI 層

- `DialogueEngine`：完全離線 fallback。
- `AiCoordinator`：選擇 Provider、背景執行、錯誤 fallback、Vision 圖片讀取。
- `GeminiProvider`：Gemini generateContent / inline image。
- `OpenAiCompatibleProvider`：Grok 與其他 OpenAI-compatible endpoint。
- `AiPromptBuilder`：人格、關係、當地時間、近期對話、相關長期記憶。
- `SecretStore`：Android Keystore + AES/GCM API Key 儲存。

AI 預設 OFF；沒有 Key 或網路失敗時仍回到 `DialogueEngine`。

## 記憶層

- `MemoryRepository`：近期 / 匯入聊天，最多 2500 筆／角色。
- `LongTermMemoryRepository`：整理後的重要記憶，最多 400 筆／角色。
- `LongTermMemoryEngine`：本機規則式候選提取、tags、importance、召回。
- `MemoryRecallEngine`：本機近期對話回想。

目前仍以 SharedPreferences + JSON 為儲存，Room migration 留待後續。

## 互動層

- `OverlayService`：多隻桌寵視窗、拖曳、聊天、貼圖、照片、手機事件反應。
- `EmotionEngine` / `TimeEngine` / `PhotoReactionEngine`。
- AI chat / Vision 由每個 `CompanionWindow` 自己的 `AiCoordinator` 非同步執行。

## 語音層

- `VoiceEngine`：Android TTS + Custom HTTP TTS fallback orchestration。
- `CustomHttpTtsClient`：使用者自訂 HTTPS TTS endpoint。
- `ReactionAudioEngine`：事件型短音檔。

## 素材 / 角色包

- `StickerRepository`：圖片貼圖 / 顏文字。
- `VoiceClipRepository`：事件反應音檔。
- `CharacterPackManager`：Character Pack v4 Preview / Import / Export。

## 手機感知層

- `ACTION_BATTERY_CHANGED`：電量 / 充電。
- `AudioDeviceCallback`：耳機輸出裝置變更。
- `NotificationAwarenessService`：使用者授權後的通知。
- Notification Listener + MediaSession：目前播放資訊。
- `ContextCooldownRepository`：事件 cooldown。

所有手機感知預設 OFF。角色包匯入不會自動開啟。

## Vision 隱私界線

v0.6 Vision 不是背景螢幕監控。只有使用者按「看圖」並透過 Android 文件選擇器主動選取的圖片，且角色 AI + Vision 都已開啟時，圖片才會交給 Provider。
