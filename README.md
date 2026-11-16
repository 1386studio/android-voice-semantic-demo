# Android Voice Semantic Demo V0.4 — Sherpa-ONNX + SenseVoice INT8

此版本将Android离线中文识别引擎从Vosk改为[Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) `2.0.1` + [SenseVoice INT8](https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html)。保持原有**RNNoise实时降噪、双WAV回放和导出、CER、Silero神经VAD、JSON词典管理、MediaPipe本地LLM Agent及规则后备**。

## 核心数据通路

```
AudioRecord 48kHz PCM16
     ├─ capture.wav
     └─ RNNoise(可选；480样本/10ms) → enhanced.wav
                                  └─ FIR重采样至16kHz → Silero VAD语音端点（默认开启，最长8秒）
                                     → sherpa-onnx SenseVoice INT8 离线非流式解码
                                     → 文本 → 本机LLM Agent或规则后备 → 语义JSON预览
```

### 非流式说明

SenseVoice **不是Vosk式流式解码器**。此工程的采音/降噪始终按10ms运行，但识别在句末静音或者达到9秒上限时才提交一段进行后台解码并显示；停止录音会处理尾段。不能据此声称10ms识别延迟。

短指令词典只是**候选短语提示**：SenseVoice不会读取Vosk grammar，也不会对JSON词典产生识别时热词偏置。对短指令输出只显示候选，原ASR文本保持不变，交给Agent解析之前不静默替换。将来若需要真正识别阶段热词，应换用明确支持hotwords的模型并评估准确率。

## GitHub Actions自动生成APK

1. 将[V0.4增量补丁](/)中的所有路径覆盖到现有GitHub仓库；保留已经修好的 `scripts/fetch_rnnoise.sh`，补丁不覆盖该文件。
2. 提交并推送至main，触发 `.github/workflows/build-apk.yml`。
3. Workflow在Linux runner中安装Java 21、Android SDK35、NDK、CMake，拉取官方Sherpa-ONNX AAR（固定SHA-256）和SenseVoice INT8模型，构建arm64 APK。
4. 成功后在Actions → Artifacts下载`voice-semantic-v04-sherpa-debug-apk`。

原Vosk下载脚本路径 `scripts/fetch_model.py` 为兼容别名，已不会下载Vosk。

## APK安装与测试

- 预留至少800MB可用存储；模型228MB左右，首次启动时需要从APK assets复制到应用私有目录。
- 初始化可能较慢，若提示`Sherpa-ONNX模型无法加载`，检查存储空间、ARM64架构和模型文件打包情况。
- 录音前可开启RNNoise并关闭系统NS，比较原始/增强WAV。ASR首次出句会有分段等待时间及CPU推理时间。
- 对相同口述语句设置人工校对文本，测量两路CER，记录推理延迟、运行内存和丢帧率。
- 语义Agent仍需额外导入兼容MediaPipe的`.task`模型，失败时后备规则Agent，且**仅展示JSON、不执行动作**。
- 词典JSON文件为 `app/src/main/assets/asr/domain_lexicon.v1.json`；支持从手机导入/恢复。

## 运行与许可

App不声明INTERNET权限，无云端识别。官方资源：
- [Sherpa-ONNX v2.0.1](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1) AAR 47.2MB，SHA-256在`scripts/fetch_sherpa_onnx.py`中固定。
- [SenseVoiceSmall INT8](https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html) 模型228MB，模型原始LICENSE由构建脚本一同提取到APK assets。
- [RNNoise](https://github.com/xiph/rnnoise)与[MediaPipe LLM](https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android)保持原有集成。

本源代码补丁本地完成语法、JSON/XML和纯Java单测；由于本环境没有Android SDK及网络二进制资产，**尚未完成Sherpa-ONNX AAR全量编译、APK构建和真机验证**。

## Silero VAD模型与开关（V0.4）

VAD默认开启，使用Sherpa-ONNX `v2.0.1` 的 `Vad` 接口和官方 `silero_vad.onnx`（16kHz/512样本，每次神经推理32ms窗口）。这不是声音幅度阈值伪装的VAD；启用时按神经语音端点切分。界面显示“语音中/静音”及已完成语音段计数；可关掉VAD使用原能量切分，仅供对比。两种模式及当次参数快照在开始录音时固定。

启动音频前必须准备VAD模型：`python3 scripts/fetch_silero_vad.py`。脚本下载官方Sherpa-ONNX发布文件并做 SHA-256 校验；GitHub Actions自动执行该步骤。安装后首次运行将VAD和SenseVoice模型复制到App私有目录，运行过程中不访问网络。

VAD默认参数见 `SherpaVadSegmenter.java`：阈值0.5、静音持续0.5秒、最短语音0.25秒、最长语音段8秒。停止录音后调用 `vad.flush()`，但特别短的未完成语音（不足最短语音阈值）仍可能被舍弃。

更多操作及验收标准：`V04_UPGRADE_NOTES.md`。
