#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="${TMPDIR:-/tmp}/virtual-companion-core-check"
rm -rf "$TMP"
mkdir -p "$TMP"

command -v kotlinc >/dev/null || {
  echo "kotlinc not found; skipping pure Kotlin compilation."
  exit 2
}

kotlinc   "$ROOT"/app/src/main/java/com/example/virtualcompanion/model/*.kt   "$ROOT"/app/src/main/java/com/example/virtualcompanion/engine/DialogueEngine.kt   "$ROOT"/app/src/main/java/com/example/virtualcompanion/engine/EmotionEngine.kt   "$ROOT"/app/src/main/java/com/example/virtualcompanion/engine/GroupDialogueEngine.kt   "$ROOT"/app/src/main/java/com/example/virtualcompanion/engine/LongTermMemoryEngine.kt   "$ROOT"/app/src/main/java/com/example/virtualcompanion/engine/MemoryRecallEngine.kt   "$ROOT"/app/src/main/java/com/example/virtualcompanion/engine/SocialDynamicsEngine.kt   "$ROOT"/app/src/main/java/com/example/virtualcompanion/engine/TimeEngine.kt   -d "$TMP/core.jar"

python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys, xml.etree.ElementTree as ET
root = Path(sys.argv[1])
for xml in (root / "app/src/main").rglob("*.xml"):
    ET.parse(xml)

pairs = {'(': ')', '[': ']', '{': '}'}
for f in (root / "app/src/main/java").rglob("*.kt"):
    text = f.read_text(encoding='utf-8')
    stack = []
    in_str = None
    esc = False
    i = 0
    while i < len(text):
        c = text[i]
        if in_str:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == in_str:
                in_str = None
            i += 1
            continue
        if text.startswith('"""', i):
            end = text.find('"""', i+3)
            if end < 0:
                raise SystemExit(f"Unclosed triple string: {f}")
            i = end + 3
            continue
        if c in ('"', "'"):
            in_str = c
        elif c in pairs:
            stack.append((c, i))
        elif c in pairs.values():
            if not stack or pairs[stack[-1][0]] != c:
                raise SystemExit(f"Delimiter mismatch: {f}:{i}")
            stack.pop()
        i += 1
    if stack:
        raise SystemExit(f"Unclosed delimiter: {f}:{stack[-1]}")
print("XML_OK")
print("KOTLIN_DELIMITER_SCAN_OK")
PY

echo "CORE_KOTLIN_OK"
