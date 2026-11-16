#!/usr/bin/env python3
"""Fetch the pinned, SHA256-verified sherpa-onnx Android AAR for offline CI packaging."""
from pathlib import Path
import hashlib
import time
import urllib.request
import sys

VERSION='2.0.1'
URL=f'https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/sherpa-onnx-{VERSION}.aar'
SHA256='a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7'
TARGET=Path(__file__).resolve().parents[1]/'app'/'libs'/f'sherpa-onnx-{VERSION}.aar'

def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()

def fetch():
 TARGET.parent.mkdir(parents=True,exist_ok=True)
 if TARGET.is_file() and digest(TARGET)==SHA256:
  print('Verified Sherpa-ONNX AAR:',TARGET);return
 tmp=TARGET.with_suffix('.aar.part')
 for attempt in range(1,5):
  try:
   req=urllib.request.Request(URL,headers={'User-Agent':'VoiceSemanticAndroid/0.4 sherpa-onnx artifact fetch'})
   with urllib.request.urlopen(req,timeout=75) as src,tmp.open('wb') as dst:
    while True:
     block=src.read(1024*1024)
     if not block:break
     dst.write(block)
   got=digest(tmp)
   if got!=SHA256:raise ValueError('AAR SHA256 mismatch: '+got)
   tmp.replace(TARGET)
   print('Verified Sherpa-ONNX AAR:',TARGET);return
  except Exception as ex:
   if tmp.exists():tmp.unlink()
   if attempt==4:raise RuntimeError('Unable to fetch official Sherpa-ONNX AAR') from ex
   print(f'Retrying AAR {attempt}/4: {ex}',file=sys.stderr);time.sleep(attempt*3)
if __name__=='__main__':fetch()
