#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v java >/dev/null 2>&1; then
    echo '错误：请先安装JDK 17或21。' >&2; exit 2
fi
if [[ -z "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]]; then
    echo '错误：请先安装Android SDK，并设置ANDROID_HOME。' >&2; exit 2
fi
if command -v gradle >/dev/null 2>&1; then
    GRADLE=(gradle)
elif [[ -f gradle/wrapper/gradle-wrapper.jar ]]; then
    GRADLE=(./gradlew)
else
    echo '错误：请先安装Gradle 8.9，或在Android Studio生成Gradle Wrapper JAR。' >&2
    echo '也可以使用.github/workflows/build-apk.yml在GitHub Actions上构建。' >&2
    exit 2
fi

bash scripts/fetch_rnnoise.sh
python3 scripts/fetch_model.py
test -s app/src/main/assets/model-cn/am/final.mdl
bash tests/run_semantic_test.sh
"${GRADLE[@]}" --no-daemon :app:assembleDebug
APK=app/build/outputs/apk/debug/app-debug.apk
test -s "$APK"
sha256sum "$APK"
echo "已生成：$(realpath "$APK")"
