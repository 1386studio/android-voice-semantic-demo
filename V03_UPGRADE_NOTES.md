# V0.3 双模式ASR + 本地JSON短指令词典升级说明

## 新增功能

- 自然语言模式：Vosk标准开放词表 `new Recognizer(model,16000f)`；不受短语列表限制，适合自由口述；**并未更换ASR基础模型**。
- 短指令模式：使用 `app/src/main/assets/asr/domain_lexicon.v1.json` 的6类短语，构建动态Vosk grammar；默认去重后短语数见JSON，`allow_unknown=true`。
- 在App中通过 Android 文件选择器导入 JSON，校验成功后复制到App私有目录，关闭App后仍保留；可一键恢复内置词典。导入时JSON大小限制为64KB，最多12类/160个不重复短语，每个短语最多48个Unicode字符。
- 模式选择保持在本机首选项；每次录音启动时固定一份grammar快照，增强流实时ASR和采集流WAV复核都使用同一份配置。
- 与规则型Agent、本地LLM、RNNoise、双WAV回放/导出、CER测试兼容；业务动作白名单不变。

## 用户操作

1. 在电脑上修改 `app/src/main/assets/asr/domain_lexicon.v1.json` 的各组 `phrases`；也可以把修改后的JSON复制到手机，点击“导入JSON词典”。
2. 切换“短指令模式（自定义领域词典）”并录音；屏幕应显示已加载类别数与短语数。
3. 对照切换“自然语言模式（开放词表）”，重复相同内容（麦克风位置和噪声条件尽量一致）；输入人工校对文本查看 CER。
4. 出现“导入失败”时，旧词典会保留不变；可“恢复内置词典”。

## JSON格式示例

```json
{
  "schema": "asr.domain_lexicon.v1",
  "version": "0.3.0",
  "name": "终端测试词典",
  "allow_unknown": true,
  "groups": [
    {"id": "devices", "label": "设备", "phrases": ["查询三号终端电量", "查看设备状态"]},
    {"id": "tasks", "label": "任务", "phrases": ["查看当前任务", "显示未完成任务"]}
  ]
}
```

说明：Vosk grammar只能影响识别候选词/短语，不会自动支持新业务意图。新增动作必须独立维护 `LlmSemanticValidator`、Agent指令模板与协议Schema。对于非固定短句，建议自然语言模式；`[unk]`设置可允许模型输出其他词，但无法保证准确度。`Vosk`可能忽略声学词典中没有的词。

## GitHub升级

使用ZIP内相对路径覆盖现有仓库根目录，提交到main，Actions自动构建APK。**仅上传本补丁，不要用旧V0.2完整ZIP覆盖已经修好的Gradle、RNNoise和Java21配置。**

## 检查

- `bash tests/run_semantic_test.sh` 增加 `DomainGrammarTest`。
- 已进行JSON格式校验、Android布局XML解析、Java纯逻辑回归测试（本地无Android SDK，未执行真机/完整APK编译）。
- 原始/增强双路识别必须使用同一模型与grammar，此版本已统一，但音频路径存在处理差异，应仅在明确实验条件下比较CER。
