# First APK — 最短建置流程

這個專案已經把 Android Build workflow 放在：

`.github/workflows/android-debug.yml`

## 使用 GitHub Actions

1. 建立一個空的 GitHub repository，例如 `VirtualCompanion-Android`。
2. 將這個專案的所有檔案放到 repository 根目錄。
3. push 到 `main`。
4. GitHub Actions 會自動執行測試、Lint、Debug Build。
5. workflow 成功後下載 artifact：`VirtualCompanion-v0.7.1-debug-apk`。

artifact 內應包含：

- `app-debug.apk`
- `app-debug.apk.sha256`
- `lint-results-debug.html`

## 判定方式

只有 `assembleDebug` 成功並真的存在 `app-debug.apk`，才代表 APK 建置完成。

## Android 手機測試順序

第一次安裝 Debug APK 後，依序測試：

1. App 是否可正常開啟。
2. 建立一隻角色。
3. 匯入 7 張或更少的測試立繪，確認缺圖 fallback。
4. 開啟「顯示在其他 App 上方」。
5. 啟動角色。
6. 回 Home Screen，確認桌寵仍存在。
7. 拖曳／點擊／長按。
8. 聊天與鍵盤。
9. 睡眠／喚醒。
10. 完全關網後測試嚴格離線模式。
11. 建立第二隻角色與多人聊天室。
12. 再測電量／耳機／音樂／通知權限。

任何 crash 都先記錄 Android 版本、手機型號與操作步驟，再修，不繼續堆新功能。
