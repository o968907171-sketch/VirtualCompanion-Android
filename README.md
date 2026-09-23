# Virtual Companion Android v0.7.1 — First APK Build Candidate

v0.7.1 不再以增加功能數量為第一目標，而是把 v0.6.1 的功能送進可重複驗證的 Android Build / Test 流程。

## v0.7 新增

- 版本升級到 0.7.1 / versionCode 9。
- App 內加入「裝置診斷」：浮動視窗、通知、通知存取、啟動角色數、離線模式。
- 啟動常駐角色失敗時會回滾 active 狀態，不再假裝已啟動。
- 通知權限改為啟動角色／使用者主動要求時詢問，不在第一次打開 App 時立刻彈出。
- GitHub Actions 現在依序執行 unit test → Android lint → assembleDebug。
- CI 成功後會直接上傳 Debug APK、SHA-256 與 Lint HTML。
- 加入 CoreEngineTest，覆蓋情緒、社交關係上下限與玩家勸和。
- 加入 `scripts/verify_source.sh`、`docs/BUILD_VERIFICATION.md` 與 `docs/TESTING.md`。

## 重要

目前聊天執行環境沒有 Android SDK，且無法從外部下載 SDK，所以本包仍不能聲稱已在此環境產出 APK。
真正的 APK 成功標準是 CI / Android Studio 顯示 `assembleDebug` 成功並產生 `app-debug.apk`。

---

# Virtual Companion Android v0.6.1

Android-first 的「住在手機裡的虛擬角色」開發版。

v0.6.1 延續 v0.6.0 的 AI、Vision、長期記憶與離線優先架構，新增「真正的多角色社交系統」與多人聊天室。

1. 可替換 AI Provider。
2. 真正的 Vision 圖片理解入口。
3. 長期記憶整理與召回。
4. 可選的自訂 HTTP TTS Provider。


## v0.6.1 多角色社交／群聊

- 可建立多個多人聊天室，每個房間至少 2 隻角色。
- 每個房間可自由選擇參加角色。
- 每一對角色保存方向性的社交狀態：好感、信任、依戀、吃醋、競爭、不滿。
- A 對 B 的感受與 B 對 A 分開保存。
- 玩家偏愛或親密稱呼某角色時，其他角色可以依房間設定逐步累積吃醋／競爭。
- 角色間的支持、吐槽、爭執會反過來改變彼此關係。
- 玩家說「別吵／和好」等內容可以讓衝突下降。
- 群聊有「和平／輕微／自然／戲劇化」四級互動強度。
- 三隻以上角色時不強迫全員每輪回話；優先由被點名或情緒／關係較相關的角色接話，讓聊天室更像真正群聊。
- 後回覆的角色可以看到前一隻角色剛才說的內容，因此能接話、反駁、吐槽或調停。
- 群聊內容會保存到房間歷史，也會以 `group:<roomId>` 來源寫入各角色本機記憶。
- AI Provider 可取得目前角色的社交關係與最近群聊上下文；離線時由本機 `GroupDialogueEngine` 回退。
- 桌寵 Overlay 與角色工作室都可以進入多人聊天室。
- Social Relationship 與群聊房間不會被普通 Character Pack 自動分享，避免把玩家自己的世界關係綁進單一角色包。

詳見 `docs/MULTI_CHARACTER_SOCIAL.md`。

## v0.6 新增

## 離線優先與跨地區使用

- 新增「嚴格離線模式」：禁止雲端 AI、Vision、自訂 HTTP TTS 發出網路請求。
- 沒有網路時，AI / Vision 會立即回退本機引擎，不等待遠端 timeout。
- Overlay、多角色、立繪／貼圖、本機聊天與情緒、近期／長期記憶、睡眠、時間、電量、耳機、角色包均可離線使用。
- Android TTS 在嚴格離線模式只選擇裝置上標記為不需要網路的 voice；沒有離線 voice 時仍正常顯示文字。
- App 核心不依賴 Google Play Services；雲端 Provider 全是可選增強功能。
- 新增 `distribution-site/` 純靜態下載頁模板、`docs/DISTRIBUTION.md`、release manifest 範例與 SHA-256 打包腳本，方便以 APK 直連與多鏡像分發。


### AI Provider

每隻角色可以獨立選擇：

- `本機`：完全離線，使用既有 Dialogue / Emotion Engine。
- `Gemini`：Google Gemini `generateContent`。
- `Grok`：xAI OpenAI-compatible REST API。
- `OpenAI-compatible`：使用者自行填入 HTTPS Base URL 與模型名稱。

目前預設模型基線（2026-09-22）：

- Gemini：`gemini-3.8-flash`
- Grok：`grok-4.7`

外部服務的價格、免費額度與可用模型會變動，因此 App 不假設外部 AI 永遠免費。即使沒有 API Key，角色仍可用本機模式。

### API Key 安全

- API Key 不寫進 Character Pack。
- API Key 不放進角色的一般設定 JSON。
- App 使用 Android Keystore 產生本機 AES/GCM 金鑰，加密後才把密文存到私有 SharedPreferences。
- 可以在角色設定中隨時清除 Provider Key。

### AI 隱私

AI 預設關閉。

開啟 AI 後，一次聊天最多會帶入：

- 當前使用者訊息。
- 最近 18 則對話。
- 最多 8 筆與問題相關的長期記憶。
- 角色名稱、人格、關係、說話方式與當地時間。

Vision 只有在使用者主動按「看圖」、選擇圖片，而且 Vision 開關已開啟時才會將圖片交給所選 Provider。Vision 失敗會退回本機 PhotoReactionEngine，不會假裝已看懂照片。

### Vision

- Gemini：使用 `inlineData` 傳送使用者主動選取的圖片。
- Grok / OpenAI-compatible：使用相容的 `image_url` data URI 形式。
- 單張圖片目前限制 8 MB，避免桌寵因大型圖片耗用過多記憶體。
- 圖片不是背景自動截圖；沒有 Screen Monitoring。

### 長期記憶

新增每角色獨立的 Long-term Memory Repository。

目前 v0.6 使用「本機規則整理」而非假裝有高階 AI 摘要：

- 從「我喜歡／討厭、生日、重要日期、考試、面試、未來計畫、家人／朋友／寵物」等明確訊息提取候選記憶。
- 每筆有 summary、tags、importance、createdAt、lastUsedAt、source。
- 對話時依關鍵詞與重要度召回相關記憶。
- 可從既有的 Gemini / Character.AI / Grok / 其他匯入聊天手動整理長期記憶。
- 可查看與清除長期記憶。

長期記憶目前最多 400 筆／角色。近期聊天記憶仍最多 2500 筆／角色。

### Custom HTTP TTS

除了 Android TTS，v0.6 增加「自訂 HTTP」Voice Provider。

協定：

- 只接受 HTTPS Endpoint。
- POST JSON：`{"text":"...", "voice":"..."}`
- 可選 Bearer API Key（同樣以 SecretStore 加密）。
- 回應可以直接是 `audio/*` 音訊位元組。
- 或 JSON：`{"audio_base64":"..."}` / `{"audio":"..."}`。
- 自訂 TTS 失敗時自動退回 Android TTS。

這是一個公開、簡單的 Provider 介面，不把任一語音服務商寫死。

## Character Pack v4

`.companionpack` 仍是安全受限 ZIP。

若使用者勾選「包含記憶」，v4 除了近期聊天 `memory.json`，也會包含 `long_memory.json`。

仍然不會打包：

- AI API Key。
- TTS API Key。
- AI / Vision 授權狀態。
- 手機感知授權。
- 原使用者名字。

匯入永遠建立新角色。

## 既有核心

- 多角色建立、複製、刪除、切換與同時 Overlay。
- 每角色獨立人格、關係、立繪、貼圖、記憶、位置、睡眠狀態。
- 預設 7 個立繪槽位，可增加到最多 38 個；角色大小 0.6x～1.6x。
- 自訂情緒、圖片貼圖、顏文字、照片互動。
- Gemini / Character.AI / Grok / ZIP / JSON / JSONL / TXT / Markdown / CSV / HTML 記憶匯入。
- Android TTS、事件反應音檔、自訂 HTTP TTS。
- 電量、耳機、音樂、通知感知。
- 通知列沒有「停止角色」按鈕。

## 權限

- `INTERNET`：只有啟用外部 AI / Vision / Custom TTS 時才實際需要網路。
- Overlay：角色顯示在其他 App 上方。
- Foreground Service：維持使用者可見桌寵服務。
- Notification runtime permission：桌寵常駐狀態通知。
- Notification Listener：只有使用者在 Android 系統設定明確授權後，通知與 MediaSession 感知才會運作。

## 驗證狀態

- Android 無關 Kotlin core：`CORE_KOTLIN_OK`。
- 全 Kotlin 原始碼 parser 掃描：`KOTLIN_PARSER_SCAN_OK`。
- 當前執行環境沒有完整 Android SDK，因此不能宣稱本機 `assembleDebug` 已成功。
- 專案保留 GitHub Actions Android build workflow。

## 下一步

v0.6.x / v0.7 可繼續：

- Room 資料庫，取代大型 SharedPreferences 記憶儲存。
- 更成熟的長期記憶摘要、合併、編輯與刪除單筆 UI。
- Provider 串流回覆。
- AI / Vision Provider 個別模型能力偵測。
- 角色主動行為與日常事件引擎。
- 天氣模組。
- Screen Awareness 的明確授權式 MediaProjection 流程。
