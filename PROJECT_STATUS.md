# PROJECT STATUS — v0.7.1

## 本版目標

第一個「真正 APK」之前的最後 Build-ready 版本。此階段停止擴充產品功能，優先處理可重複建置、驗證、APK 產物與實機測試入口。

## 已完成

- `versionName 0.7.1` / `versionCode 9`。
- v0.6.1 的多角色、多人聊天室、社交關係、吃醋／爭執／和好功能完整保留。
- v0.6.0 的 AI Provider、Vision、長期記憶、嚴格離線模式完整保留。
- v0.5 的電量、耳機、音樂、通知感知完整保留。
- 7～38 立繪、尺寸調整、貼圖／顏文字、照片互動、Character Pack 完整保留。
- 通知列 **沒有**「直接停止角色」按鈕。
- 裝置診斷、啟動失敗回滾、權限檢查。
- CI 固定流程：`testDebugUnitTest` → `lintDebug` → `assembleDebug` → SHA-256 → APK artifact。
- 純 Kotlin 核心可在無 Android SDK 的環境先做編譯檢查。

## 本輪實際驗證

- `XML_OK`
- `KOTLIN_DELIMITER_SCAN_OK`
- `CORE_KOTLIN_OK`

目前執行環境沒有 Android SDK / Build Tools，因此不能在本機誠實宣稱 `assembleDebug` 成功。

## 第一個 APK 的最短路徑

本專案已內建 `.github/workflows/android-debug.yml`。只要放進可執行 GitHub Actions 的 repository，workflow 會自行安裝 Android SDK 35 與 Build Tools 35.0.0，然後真正執行 Android Build。

目前已連結的 GitHub 帳號沒有任何 repository，而現有 GitHub 連接器無法代替使用者建立新 repository。因此唯一尚缺的外部動作是：建立一個空的 GitHub repository。之後可以把本專案上傳進去並直接取得 CI 產生的 Debug APK。

## 成功標準

只有在以下檔案真的由 Android Build 產生後，才稱為 APK 完成：

`app/build/outputs/apk/debug/app-debug.apk`
