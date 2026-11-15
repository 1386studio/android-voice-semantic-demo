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
git clone --branch v0.2 --depth 1 https://github.com/xiph/rnnoise.git "$DEST"
(cd "$DEST" && ./download_model.sh)
test -s "$DEST/src/rnnoise_data.c"
test -s "$DEST/src/nnet_default.c"
echo 'RNNoise v0.2 sources ready for Android NDK CMake build'
