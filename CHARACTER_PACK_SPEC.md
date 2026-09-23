# Virtual Companion Character Pack v4

建議副檔名：`.companionpack`，實際格式為受限制 ZIP。

## 檔案

- `pack.json`：格式、版本、匯出時間、是否含記憶、作者。
- `character.json`：角色人格與顯示設定。
- `meta/cover.*` + `meta/cover.json`：可選封面。
- `bubble/background.*` + `bubble/index.json`：可選自訂對話框背景。
- `images/index.json` + `images/*`：7～38 個立繪 / 情緒圖片。
- `stickers/index.json` + `stickers/*`：圖片貼圖與顏文字。
- `voice/index.json` + `voice/*`：事件反應音檔。
- `memory.json`：可選近期 / 匯入聊天記憶。
- `long_memory.json`：v4 新增；可選整理後長期記憶。

## v4 新增

若使用者明確勾選「包含記憶」，除了近期聊天，也會匯出長期記憶 summary / tags / importance / timestamps / source。

## 絕不放入角色包

- Gemini / Grok / custom Provider API Key。
- Custom TTS API Key。
- AI / Vision 啟用授權狀態。
- Android 通知 / Overlay 等系統權限。
- 實際使用者名稱。

## 匯入規則

- 一律建立新角色，不覆蓋現有角色。
- Battery / Headphone / Music / Notification Awareness 保持 OFF。
- AI / Vision 保持預設 OFF。
- API Key 只能由新裝置使用者自行設定。

## 安全

- 防 ZIP path traversal。
- 限制 entries、單檔與總解壓大小。
- 匯入失敗回滾半完成角色。
