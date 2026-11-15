# Voice Semantic Android V0.2.1 — local LLM action-protocol repair

This update fixes the common fallback message: `小模型输出无效：非法动作类型：query`.

Changes:
- `LocalLlmAgent.java`: strict action-enumeration prompt and one bounded LLM repair attempt.
- `LlmSemanticValidator.java`: guarded canonicalization of unambiguous `query` actions.
- `ActionCanonicalizer.java`: new deterministic action-alias adapter; never allows arbitrary operations.
- `MainActivity.java`: call repair before fallback to rules.
- JSON schema: accepts metadata from both local LLM and rule-based Agent.
- Tests: eight new canonicalization/rejection tests.

INSTALLATION:
1. Extract ZIP. Copy/overwrite the nested `app`, `schemas`, and `tests` files into the repository root.
2. Commit to `main` and wait for GitHub Actions build `Build offline Android APK`.
3. Install resulting APK. GitHub Actions debug APKs may use different signing keys between runs. If Android refuses an in-place update, export important WAVs and retain the original `.task` model file BEFORE uninstalling the old app; uninstalling deletes app-private recordings and imported model data. Re-import the model after installing.

BEHAVIOR:
- `query` plus an unambiguous `device_id` and `metric` resolves to `device.status.query`.
- `query` plus warehouse and material resolves to `inventory.query`.
- Bare `query`, conflicting targets, destructive commands, unknown actions are rejected, followed by one local model repair attempt.
- If the second output is still invalid, deterministic rules Agent fallback continues. No external actions are executed.

VALIDATION:
- `bash tests/run_semantic_test.sh`: pure-Java suite (6 semantic, 8 canonicalization, audio and CER tests).
- Native Android/MediaPipe integration and real phone inference must be verified by the GitHub Actions build and a device test.
