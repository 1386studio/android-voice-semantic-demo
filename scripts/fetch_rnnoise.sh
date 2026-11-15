#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/cpp/vendor/rnnoise"
if [[ -s "$DEST/src/rnnoise_data.c" && -s "$DEST/src/nnet_default.c" ]]; then
  echo 'RNNoise source and model found'; exit 0
fi
mkdir -p "$(dirname "$DEST")"
rm -rf "$DEST"
# Tagged release, rather than moving main branch. Model archive is SHA256 verified upstream.
# 下载固定版本的RNNoise源码
git clone --branch v0.2 --depth 1 \
  https://github.com/xiph/rnnoise.git "$DEST"

# 应用上游针对ARM64/NEON编译问题的官方修复
PATCH="$(mktemp)"
trap 'rm -f "$PATCH"' EXIT

curl -fsSL --retry 3 \
  "https://github.com/xiph/rnnoise/commit/372f7b4b76cde4ca1ec4605353dd17898a99de38.patch" \
  -o "$PATCH"

git -C "$DEST" apply --check "$PATCH"
git -C "$DEST" apply "$PATCH"

echo "RNNoise upstream ARM64 build fix applied"

# 下载RNNoise预训练模型
(cd "$DEST" && ./download_model.sh)
test -s "$DEST/src/rnnoise_data.c"
test -s "$DEST/src/nnet_default.c"
echo 'RNNoise v0.2 sources ready for Android NDK CMake build'
