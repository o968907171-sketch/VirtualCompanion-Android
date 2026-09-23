#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if ! command -v gradle >/dev/null; then
  echo "Gradle 8.11.1 is required."
  echo "Open this project in Android Studio, install Android SDK 35, or run the included GitHub Actions workflow."
  exit 1
fi

if [ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then
  echo "ANDROID_HOME / ANDROID_SDK_ROOT is not set. Android SDK 35 is required."
  exit 1
fi

gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
APK="app/build/outputs/apk/debug/app-debug.apk"
test -f "$APK"
sha256sum "$APK" | tee "$APK.sha256"
echo "BUILD SUCCESS: $APK"
