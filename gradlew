#!/bin/sh
# Thin launcher. Use verified official wrapper files when they are present.
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$ROOT/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo '缺少官方Gradle Wrapper JAR和系统Gradle。请先用本机Gradle运行 gradle wrapper --gradle-version 8.9' >&2
exit 1
