@echo off
setlocal
set ROOT=%~dp0
if exist "%ROOT%gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%ROOT%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
) else (
  where gradle >nul 2>nul
  if errorlevel 1 (
    echo 缺少官方Gradle Wrapper JAR和系统Gradle。请先用本机Gradle生成官方Wrapper。
    exit /b 1
  ) else (
    gradle %*
  )
)
