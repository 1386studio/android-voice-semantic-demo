#!/usr/bin/env python3
"""Fetch only INT8 SenseVoice ONNX and tokens from official sherpa-onnx model archive."""
from pathlib import Path
import tarfile
import time
import urllib.request
import sys
import shutil

NAME='sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17'
URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/'+NAME+'.tar.bz2'
DEST=Path(__file__).resolve().parents[1]/'app'/'src'/'main'/'assets'/'asr'/'sensevoice'
WANTED={'model.int8.onnx':100_000_000,'tokens.txt':10_000, 'LICENSE':1}

def ready():
 return all((DEST/f).is_file() and (DEST/f).stat().st_size>=sz for f,sz in WANTED.items())

def extract():
 if ready():print('SenseVoice model already present');return
 DEST.mkdir(parents=True,exist_ok=True)
 # Stream the compressed tar to avoid keeping a 230MB tarball and a second copy on the CI runner.
 for attempt in range(1,4):
  tmps=[]
  try:
   req=urllib.request.Request(URL,headers={'User-Agent':'VoiceSemanticAndroid/0.4 model asset fetch'})
   with urllib.request.urlopen(req,timeout=150) as resp:
    with tarfile.open(fileobj=resp,mode='r|bz2') as archive:
     found=set()
     for info in archive:
      if not info.isfile() or not info.name.startswith(NAME+'/'):continue
      short=info.name[len(NAME)+1:]
      if short not in WANTED or short in found:continue
      tmp=DEST/(short+'.part')
      tmps.append(tmp)
      source=archive.extractfile(info)
      if source is None:raise RuntimeError('Missing tar stream for '+short)
      with source,tmp.open('wb') as target:shutil.copyfileobj(source,target,1024*1024)
      if tmp.stat().st_size<WANTED[short]:raise RuntimeError('Model member too small: '+short)
      found.add(short)
     if found!=set(WANTED):raise RuntimeError('Missing model members: '+str(set(WANTED)-found))
   for name in WANTED:(DEST/(name+'.part')).replace(DEST/name)
   print('SenseVoice INT8 ready:',DEST);return
  except Exception as ex:
   for f in tmps:
    if f.exists():f.unlink()
   if attempt==3:raise RuntimeError('Official SenseVoice model fetch failed') from ex
   print(f'Retrying model {attempt}/3: {ex}',file=sys.stderr);time.sleep(attempt*4)
if __name__=='__main__':extract()
