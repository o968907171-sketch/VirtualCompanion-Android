# BUILD VERIFICATION RESULTS — v0.7.1

## 已在目前環境實際執行

- XML resource / manifest parse: `PASS`
- Kotlin delimiter scan: `PASS`
- Pure Kotlin core compilation with `kotlinc`: `PASS`

`CORE_KOTLIN_OK`

## 尚未能在目前環境執行

- `:app:testDebugUnitTest`
- `:app:lintDebug`
- `:app:assembleDebug`

原因不是已知的 Android 編譯錯誤，而是目前容器沒有 Android SDK，且此容器不能直接下載 Google Android SDK 套件。

## CI 驗證

`.github/workflows/android-debug.yml` 已設定：

1. JDK 17
2. Android SDK Platform 35
3. Build Tools 35.0.0
4. Gradle 8.11.1
5. Unit tests
6. Android Lint
7. `assembleDebug`
8. APK SHA-256
9. 上傳 Debug APK artifact

CI 只有在三個驗證階段全部成功後才會產出可下載 APK artifact。
