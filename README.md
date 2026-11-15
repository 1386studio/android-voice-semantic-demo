# VoiceSemanticAndroid V0.2 (Android ARM64, offline)

面向 **手机麦克风 → 实时降噪 → 本地ASR → 小模型Agent → 标准化语义JSON** 的研究原型。不联网、不传输、不执行业务工具。

> 注意：此包是源代码和 GitHub Actions 自动构建配置，不是已经在真机验证的APK。本环境没有Android SDK/NDK，无法对V0.2进行完整Android编译验证。V0.1版本曾在该GitHub仓库成功构建。

## 四项需求的实现状态

| 功能 | V0.2源码实现 | 边界条件 |
|---|---|---|
| 1. 同录两轨、回放/导出 | 48kHz单声道PCM16，`*-capture.wav`与`*-enhanced.wav`；MediaPlayer回放、SAF导出 | “capture”是Android AudioRecord读取到的音频，不保证完全绕过厂商硬件DSP；录音默认保留在App私有目录，卸载即删除 |
| 2. ASR精度增强 | Vosk领域短指令grammar可切换、采集/降噪两路ASR复核、人工真值CER评测；48→16kHz带抗混叠 | **没有在本版替换成SenseVoice等更强ASR**；领域grammar对开放式口述不一定有利，需真机量化 |
| 3. 端侧小模型Agent | MediaPipe `tasks-genai:0.10.27` 真正本地模型推理 + JSON解析 + 操作类型白名单 + 规则后备 | **不打包LLM权重**，必须单独导入兼容的 `.task` 文件，机型/内存/模型格式须实测；无模型时明确显示规则后备模式 |
| 4. RNNoise实时AI降噪 | RNNoise v0.2 C源码+Android NDK CMake+JNI，每480样本(10ms/48kHz)处理；可与Android系统NS切换 | 依赖构建时Git下载RNNoise和校验过的权重；未经实机CPU占用/延迟/ASR精度测量 |

## 快速构建

在有 JDK 17、Android SDK 35、NDK `27.2.12479018`、CMake `3.22.1`、Gradle 8.9 的机器运行：

```bash
bash scripts/fetch_rnnoise.sh
python3 scripts/fetch_model.py
bash tests/run_semantic_test.sh
gradle --no-daemon :app:assembleDebug
```

**推荐GitHub Actions：** 将文件合并到仓库根目录，提交到 `main`，进入 Actions → **Build offline Android APK**，待运行成功后从 **voice-semantic-v02-debug-apk** 下载 `app-debug.apk`。构建时需要外网下载 Vosk、RNNoise和Gradle/Maven依赖，但 APK 运行时没有 `INTERNET` 权限。

RNNoise的`download_model.sh`校验下载模型的SHA-256。项目仅保存自动拉取脚本，不将第三方模型与C源码打包进本ZIP；模型权重的再分发/商业使用条款需要单独核查。

## 手机测试流程

1. 安装升级版APK，允许麦克风权限。
2. 保持**系统降噪关闭、RNNoise开启**，录制同一句语音；停止后会保存两段WAV。可切换回放并导出到Downloads。
3. 再关闭RNNoise录制相同口令；观察听感和ASR；真正的A/B对照应尽量保持设备位置、噪声和说话内容一致。
4. App的“采集流ASR复核”会对保存的capture WAV重新识别。输入**人工校对的真实句子**并点“计算双路ASR字符错误率（CER）”。越低越好。
5. “领域短指令词表”只适用于本工程有限口令集合；长句、自由口述时建议关闭。
6. 离线语义化无需LLM就可以使用规则后备。需启用真正小模型时，在联网电脑下载**MediaPipe兼容**的Gemma3 1B 4-bit `.task` 文件复制到手机，在App选择“导入本地小模型”；实际运行全程离线。
7. 解析结果中`agent.engine`为`mediapipe_on_device_llm`且`llm_used:true`时表示真正运行LLM；若无法加载或输出不合法则标注规则型后备。

官方已准备的模型可参考 [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT)，需要获取**对应MediaPipe的`.task`**，不能直接导入GGUF或`.litertlm`文件。部分模型有许可与访问条件。MediaPipe LLM Inference已进入维护阶段，新项目后续应评估向LiteRT-LM迁移。

## 录音存储及隐私

录音保存在：`context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)/voice-sessions/`（手机应用专用音乐目录），命名如：

```
20261115-101500-000-capture.wav
20261115-101500-000-enhanced.wav
```

每次开始录音生成新的同名配对，不覆盖旧文件。测试版单次录制最长180秒，以控制存储占用。使用“导出WAV”可以经Android系统文件选择器保存到共享目录。无云同步、无MCP发送、无业务执行。请避免在公用设备留下敏感测试录音，测试完成后在系统App存储中清除或卸载。

### 音频注意事项

- 捕获源尽量请求Android `UNPROCESSED`，设备不支持时回退`VOICE_RECOGNITION`。系统/厂商底层算法可能仍生效，因此“采集流”并非物理ADC原始信号。
- 系统NS与RNNoise做为对照开关互斥，防止双重强降噪造成漏字。
- 录音保存48kHz/16bit；转换为Vosk需要的16kHz格式时，使用原有FIR抗混叠和高通预处理。
- 帧长10ms**不等于算法延迟为10ms**，请通过测试仪记录采音至ASR出词时间、RNNoise处理的P95/P99、音频丢帧率。
- 当前没有实现多麦波束形成、AEC、唤醒词、端到端ASR训练，也没有增加模型或工具的远端调用。

## 升级现有GitHub仓库

不要在ZIP根目录外再包一层文件夹。建议使用Git提交：

```bash
git clone https://github.com/1386studio/android-voice-semantic-demo.git
cd android-voice-semantic-demo
unzip -o ../VoiceSemanticAndroid_V02_Full.zip -d .
git status
git add .
git commit -m "Add V0.2 RNNoise recording and on-device semantic agent"
git push origin main
```

GitHub网页上传时容易遗漏隐藏目录`.github`；也可以只把本ZIP中有改动的文件覆盖仓库，并单独编辑 `.github/workflows/build-apk.yml`。不要把`app/src/main/cpp/vendor/rnnoise`、本地大模型、Android构建产物提交到GitHub。

## 进一步提高ASR精度的正确路线

V0.2的Vosk语法约束和CER评测旨在建立可靠基线，不等于模型本身升级。测出实际噪声下CER后，如果仍不足以使用，下一阶段推荐接入 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 的 **SenseVoice-Small INT8** 作为第二路ASR，对同一段增强WAV进行复核；再针对专业实体词、设备编号做领域适配。重模型是否能实时，必须实测手机SoC。

## 开源与模型来源

- RNNoise: https://github.com/xiph/rnnoise (BSD-3-Clause代码；模型权重另行核实)
- Vosk Android: https://alphacephei.com/vosk/ (包含小中文模型)
- MediaPipe LLM: https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android
- LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM
- sherpa-onnx/SenseVoice: https://k2-fsa.github.io/sherpa/onnx/sense-voice/
