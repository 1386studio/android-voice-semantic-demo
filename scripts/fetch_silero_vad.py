#!/usr/bin/env python3
"""Fetch the official Sherpa-ONNX Silero VAD model with a pinned SHA256."""
from pathlib import Path
import hashlib
import urllib.request
import sys
import time

URL = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx'
# The 16kHz/512-sample Sherpa export, 643854 bytes, independently mirrored with this digest.
SHA256 = '9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6'
DEST = Path(__file__).resolve().parents[1] / 'app/src/main/assets/asr/vad/silero_vad.onnx'

def sha256(file):
    h = hashlib.sha256()
    with file.open('rb') as stream:
        for block in iter(lambda:stream.read(1<<20),b''):
            h.update(block)
    return h.hexdigest()

def fetch():
    DEST.parent.mkdir(parents=True,exist_ok=True)
    if DEST.exists() and sha256(DEST) == SHA256:
        print('Verified Silero VAD model:', DEST)
        return
    tmp = DEST.with_suffix('.onnx.part')
    for attempt in range(1,5):
        try:
            req = urllib.request.Request(URL, headers={'User-Agent':'VoiceSemanticAndroid-VAD/0.4'})
            with urllib.request.urlopen(req, timeout=90) as source, tmp.open('wb') as out:
                while True:
                    b = source.read(1<<20)
                    if not b: break
                    out.write(b)
            actual = sha256(tmp)
            if actual != SHA256:
                raise ValueError('Silero VAD SHA256校验失败：'+actual)
            tmp.replace(DEST)
            print('Verified Silero VAD model:', DEST)
            return
        except Exception as exc:
            tmp.unlink(missing_ok=True)
            if attempt == 4:
                raise RuntimeError('官方Silero VAD模型下载失败') from exc
            print('Retrying VAD model:', exc, file=sys.stderr)
            time.sleep(attempt*3)

if __name__ == '__main__':
    fetch()
