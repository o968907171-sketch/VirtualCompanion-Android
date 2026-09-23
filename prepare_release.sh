#!/usr/bin/env bash
set -euo pipefail
VERSION="${1:-0.6.1}"
APK="${2:-app/build/outputs/apk/release/app-release.apk}"
OUT="release-out"
if [[ ! -f "$APK" ]]; then
  echo "APK not found: $APK" >&2
  echo "Build and sign the release APK first." >&2
  exit 1
fi
mkdir -p "$OUT"
TARGET="$OUT/VirtualCompanion-v${VERSION}.apk"
cp "$APK" "$TARGET"
( cd "$OUT" && sha256sum "$(basename "$TARGET")" > SHA256SUMS.txt )
echo "Prepared $TARGET"
echo "Checksum: $OUT/SHA256SUMS.txt"
