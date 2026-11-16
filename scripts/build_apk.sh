#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if ! command -v java >/dev/null 2>&1; then echo '请安装JDK 21' >&2; exit 2; fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then echo '请设置ANDROID_HOME' >&2; exit 2; fi
if command -v gradle >/dev/null 2>&1;then GRADLE=(gradle)
elif [[ -f gradle/wrapper/gradle-wrapper.jar ]];then GRADLE=(./gradlew)
else echo '缺少Gradle 8.9；建议使用GitHub Actions' >&2; exit 2; fi
bash scripts/fetch_rnnoise.sh
python3 scripts/fetch_sherpa_onnx.py
python3 scripts/fetch_sensevoice.py
python3 scripts/fetch_silero_vad.py
bash tests/run_semantic_test.sh
"${GRADLE[@]}" --no-daemon :app:assembleDebug
APK=app/build/outputs/apk/debug/app-debug.apk
test -s "$APK"
sha256sum "$APK"
