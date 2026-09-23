# v0.7 Build Verification

v0.7 的優先目標不是繼續堆功能，而是把目前專案送進真正的 Android 建置流程。

## 必須通過的三關

1. `:app:testDebugUnitTest`
2. `:app:lintDebug`
3. `:app:assembleDebug`

只有第三關真的產生 `app/build/outputs/apk/debug/app-debug.apk`，才能稱為「APK 已建置」。

## 本機

需要：

- JDK 17
- Gradle 8.11.1
- Android SDK Platform 35
- Android Build Tools 35.0.0

執行：

```bash
./build_local.sh
```

## GitHub Actions

`.github/workflows/android-debug.yml` 會自動安裝 Android SDK 35、執行單元測試、Lint、Build，然後上傳：

- `app-debug.apk`
- `app-debug.apk.sha256`
- `lint-results-debug.html`

## 本次聊天環境限制

目前執行環境沒有 Android SDK，而且外部 SDK 下載被網路環境阻擋，因此無法在此機器誠實宣稱 `assembleDebug` 成功。
純 Kotlin 核心、XML 與原始碼結構可在本機腳本中先行驗證。
