# V0.4：以 Sherpa-ONNX SenseVoice INT8 替换 Vosk

## 已变更

- 移除Gradle `vosk-android`依赖；用官方 `sherpa-onnx-2.0.1.aar`（SHA256固定校验）及配套Kotlin标准库。
- 从官方`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17`模型包提取`model.int8.onnx`及`tokens.txt`至APK assets；安装后首次启动复制到私有目录并加载。无需运行期网络。
- 本版新增**Silero神经VAD**：从官方Sherpa-ONNX发布下载经SHA-256校验的16kHz Silero ONNX模型（643854字节），使用 `Vad` 接口在手机CPU上判断语音起止。默认开启，可通过界面开关关闭作能量切分对照。每路音频独立创建VAD实例，不共享内部状态；开启时由VAD决定分段，识别线程处理VAD已结束的语音段；停止后 `flush()` 处理未完成末段。阈值0.50、最短静音0.50秒、最短语音0.25秒、最长语音8秒、16kHz/512采样窗，参数集中在 `SherpaVadSegmenter`。
- Sherpa-ONNX SenseVoice INT8为**离线非流式**识别，不提供Vosk式的真流式partial与受限grammar。音频仍由AudioRecord和RNNoise按10ms实时处理，ASR在后台分段识别，每段完成后展示文字，录音停止时处理末段。
- 原始WAV及RNNoise增强WAV保存/回放/导出不变；两条音轨复核使用一致的16kHz重采样及Silero VAD配置（或一致的能量分段配置），保持CER比较可复现。
- 可从V0.3导入/恢复6类JSON短指令词表，但SenseVoice模型**不支持Vosk式硬语法约束**；只显示词典候选，绝不静默替换ASR文本，也不会执行语义动作。开放式识别和短指令提示共用相同基础模型。
- `SemanticAgent`及MediaPipe本地模型继续保留；语义输出仍需动作白名单验证。

## 一次性替换方法

先在本地仓库 Pull origin，再将本ZIP中的app、scripts、tests、schemas及.github按相对路径覆盖到仓库根目录。**不要提交app/libs中的AAR文件或app/src/main/assets/asr/sensevoice中的大模型**；构建脚本会在Actions中自动下载。

`git add -A && git commit -m "Replace Vosk with Sherpa-ONNX SenseVoice INT8" && git push origin main`

构建完成后，从GitHub Actions的Artifacts获取 `voice-semantic-v04-sherpa-vad-debug-apk`。

## 资源、性能与限制

官方INT8模型文件约228MiB；AAR约47MB。APK体积可能超过250MB。第一次启动需要把模型复制到应用私有数据目录，因此应预留至少800MB可用空间。实际CPU利用率、首字延迟、CER及语音分段效果需要手机实测。

模型下载链接：
- https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2
- https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar
- https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx

可用性：新方案只支持Android arm64-v8a；运行期无INTERNET权限。构建需要GitHub Actions环境访问上游发布包。

## 检验

构建前可运行 `bash tests/run_semantic_test.sh`。真正的Android AAR链接、JNI动态加载和完整APK构建需以GitHub Actions为准；只有构建成功不等于真机验证通过。

## VAD功能验证

1. 安装后确认状态显示“SenseVoice INT8 + Silero VAD就绪”。
2. 保持Silero VAD开启，先静音3秒：界面应为“静音/等待语音”，不应产生语义指令。
3. 读出1秒短语，停顿约1秒：界面应变为“检测到语音”并在结束后增加分段数，SenseVoice输出最终文本。
4. 连续说话超过8秒：应由Silero执行最长语音分段限制，不会无界积累音频。
5. 在语句结尾立即按停止：VAD flush应提交最后一段（短于最短语音0.25秒的片段可能被丢弃）。
6. 分别导出采集流与RNNoise增强流并计算CER；对比不同VAD开关设置，注意两路信号可能形成不同边界。
7. 关闭VAD后使用旧能量分段，界面明确标记为“对照”，不能宣称仍在运行神经VAD。

注意：仅提供源码、构建脚本及本地静态/纯Java测试；完整AAR/NDK编译、真机语音端点、VAD精度及性能仍须GitHub Actions和Android真机验证。
