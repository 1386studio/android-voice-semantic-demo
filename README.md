# 手机端离线语音 → 规则Agent → 语义JSON（Android V0.1）

这是一个**真实源码Android App工程**，不是UI效果图，不进行网络传输、不调用云端模型、不执行业务动作。

## 功能范围

- **手机采音**：Android AudioRecord，48kHz、PCM16、mono，10ms音频块；在设备支持时使用系统 NoiseSuppressor；RMS音量和削波风险显示。
- **DSP处理**：高通滤波与63 taps FIR抗混叠重采样（48kHz → 16kHz）。不对输入应用激进的噪声门限，避免吞掉短指令。
- **离线中文ASR**：Vosk Android 0.3.75 + `vosk-model-small-cn-0.22`，语音分段转写、增量文本及结束后最终文本。
- **端侧Agent（规则型）**：提取意图、设备编号、仓库编号、物资类型、分步操作、待确认事项。
- **标准化语义JSON**：输出 `voice.semantic.v1` 消息，包含 `source`、`recognition`、`agent`、`semantic`、`validation`、`execution`。
- **无需ASR模型也能测试**：可直接输入中文指令，点击“解析输入文字”。
- **默认保护隐私**：Manifest无 `INTERNET` 权限；采集音频不存储，不接入MCP/Zenoh，不执行工具调用，退出界面停止录音。

## 一键生成可安装APK（更新）

**推荐方式：GitHub Actions云端构建。** 本工程增加了 `.github/workflows/build-apk.yml`，配置完整的JDK、Android SDK、Gradle和离线中文模型，运行 `:app:assembleDebug` 并校验APK签名。

1. 将本项目所有文件推送到你有权限的GitHub仓库（保留 `.github/workflows/build-apk.yml`）。
2. 在仓库的 **Actions → Build offline Android APK → Run workflow** 启动构建；推送到main/master也会自动构建。
3. 工作流成功后，在该次运行的 **Artifacts** 下载 `voice-semantic-debug-apk`，其中包含 `app-debug.apk` 和 `SHA256SUMS`。
4. 将APK复制到Android 8以上ARM64手机安装。首次启动会将打包的中文语音模型复制到App私有目录，因此请预留约200MB以上空间。

**本地构建：** 在Android SDK 35、Gradle 8.9和JDK 17/21就绪的环境中执行 `bash scripts/build_apk.sh`。

**交付状态声明：** 当前会话环境缺少Android SDK/Gradle且无法从外部下载构建依赖，故无法在此环境真实生成或验证APK；该工作流需要在联网的GitHub Actions运行后才产出可安装文件。这不是已经编译好的APK。

## Android Studio构建

前提：Android Studio / Android SDK 35、JDK 17或21、ARM64 Android 8+真机。构建机需能访问Google Maven与Maven Central以解析Gradle依赖。

1. 在**联网的构建电脑**解压本工程，进入项目根目录。
2. 安装离线ASR中文模型（下载量约42MB，实际APK体积与解压大小有所不同）：

   ```bash
   python3 scripts/fetch_model.py
   ```

   模型将放在 `app/src/main/assets/model-cn/`；打包后首次启动复制到App私有目录。该模型由Vosk官方提供，许可为Apache-2.0。

3. 使用Android Studio打开项目根目录，安装SDK Platform 35并同步Gradle。
4. **Gradle Wrapper注意**：本工程包含固定版本的`gradle-wrapper.properties`，但当前生成环境无法下载官方`gradle-wrapper.jar`。若IDE提示缺少Wrapper JAR，请在本机已有Gradle 8.9的环境下运行一次：

   ```bash
   gradle wrapper --gradle-version 8.9
   ```

   然后运行：

   ```bash
   ./gradlew :app:assembleDebug
   ```

   Windows可使用 `gradlew.bat :app:assembleDebug`。如系统原先没有Gradle，可先在Android Studio创建一个空白项目（Gradle 8.9 / AGP 8.7.3），用其官方生成的wrapper脚本和jar替换/补全该工程的 `gradle/` wrapper文件，再同步。

5. APK通常位于 `app/build/outputs/apk/debug/app-debug.apk`。通过Android Studio Run或adb安装至手机。

> **重要：** 本次交付只包含可导入源码与离线规则解析测试，当前环境没有Android SDK/Gradle，也不能下载ASR模型及Maven依赖，故没有真实编译出来的APK；Android UI与真机录音行为尚待实机验证。

## 推荐测试流程

1. 首先不需要麦克风：点击“填入示例指令” → “解析输入文字”。观察标准语义JSON是否含查询与发布两个步骤、`requires_confirmation=true`、`execution.state=not_executed`。
2. 构建时安装Vosk模型，在手机授权麦克风后“开始采音”并朗读指令，点击“停止并解析”。
3. 使用真实嘈杂环境分别测试系统降噪打开/关闭下的转写错误率和结构化意图准确率。
4. 使用耳机或近讲麦克风以改善极端噪声条件下的信噪比。

测试语句：

- 查询三号终端的剩余电量，并把结果发给当前工作组
- 查询WH001仓库的饮用水库存
- 查看最新未完成任务
- 打开地图并显示当前位置
- 查询七号设备最近十分钟的温度变化
- 删除所有任务（应拒绝生成可执行的写操作）

## 技术边界与升级路线

- **Agent不是本地DeepSeek/Qwen LLM。** 这是可审计的纯本地规则型语义引擎，具有意图/实体提取及分步动作规划；它不会创造未定义工具。后续可用 `SemanticAgent` 的接口替换为 llama.cpp / MLC / MediaPipe等本地模型，并要求返回同一JSON Schema，由规则校验器进行验证。
- **实时降噪不是RNNoise。** 目前是Android平台`NoiseSuppressor`（是否启用取决于具体设备）；系统ROM可能包含DSP或神经网络实现，但应用无法保证其内部算法。需要统一自主可控的RNNoise时，可通过JNI在48kHz帧级处理处插入native降噪库。
- **真正高鲁棒语音**需要收集场景录音，对比外接近讲麦与手机麦、动态增益设置、强噪声语料和不同ASR模型。Vosk Small CN只适合轻量离线验证，不代表在-5/-10dB下已经可靠。
- **不包含业务执行**：`group.message.publish`只是一项声明，程序不会访问MCP/Zenoh，也不会发送数据。
- **高噪声实验指标**：CER、设备ID/数字正确率、意图准确率、错误执行率（此版为0，无执行通路）、ASR P95延迟、丢帧次数。
- **音频采样精度**：虽然历史需求提及24-bit/48kHz ADC，但本App以手机内置AudioRecord的普适PCM16/48kHz为默认。真实麦克风ADC字长由手机硬件与驱动决定，不能用AudioRecord的16bit格式代表ADC物理精度。

## 静态单元测试（不需要Android SDK）

```bash
bash tests/run_semantic_test.sh
# 同时运行语义识别6项测试与重采样通带/抗混叠测试
```

## 目录

```text
app/src/main/java/com/example/voicesemantic/
  MainActivity.java          UI与权限、状态管理
  AudioCaptureEngine.java   实时采音、NS、ASR队列与线程
  PcmPreprocessor.java      80Hz高通与48k/16k重采样
  ModelManager.java         App离线模型首次安装
  SemanticAgent.java        独立可测的离线语义Agent
app/src/main/res/layout/activity_main.xml
scripts/fetch_model.py
schemas/voice.semantic.v1.schema.json  自定义语义消息JSON Schema
scripts/
tests/SemanticAgentTest.java
```

## 主要来源

- Vosk Android工程：https://github.com/alphacep/vosk-android-demo
- Vosk中文小模型：https://alphacephei.com/vosk/models
- Android NoiseSuppressor：https://developer.android.com/reference/android/media/audiofx/NoiseSuppressor
- Android AudioRecord：https://developer.android.com/reference/android/media/AudioRecord

## 源码许可

本工程自编源码采用MIT许可。Vosk Android和下载的中文模型分别遵循其上游Apache-2.0许可；对第三方组件不重新授权。
