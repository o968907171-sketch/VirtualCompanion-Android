# GitHub Build Handoff

目前專案不需要 repository secrets 才能 Build Debug APK。

workflow 使用公開依賴與 Android SDK，並不需要 Gemini / Grok API Key。AI Key 屬於使用者裝置本機設定，不應放進 CI。

如果由 ChatGPT 的 GitHub 連接器接手：

- 可以對既有 repository 建立／更新檔案與 branch。
- 可以查看 workflow run。
- 可以下載 workflow artifact。
- 目前連接器不能替使用者新建 GitHub repository。

因此 repository 一旦存在，就能把剩餘「上傳 → Build → 取回 APK」流程接起來。
