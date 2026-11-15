#!/usr/bin/env python3
"""Build-time only. Downloads Apache-2.0 Vosk small Chinese model for inclusion in APK.
The Android app has NO INTERNET permission and will never download any model.
"""
import io
import pathlib
import shutil
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEST = ROOT / 'app' / 'src' / 'main' / 'assets' / 'model-cn'
URL = 'https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip'
MODEL_FOLDER = 'vosk-model-small-cn-0.22/'


def main():
    if (DEST / 'am' / 'final.mdl').is_file():
        print(f'已存在中文模型：{DEST}')
        return
    print('下载模型（仅在构建电脑联网，App运行完全离线）：', URL)
    with urllib.request.urlopen(URL, timeout=90) as response:
        payload = response.read()
    with zipfile.ZipFile(io.BytesIO(payload)) as z:
        entries = [item for item in z.infolist() if not item.is_dir()]
        for item in entries:
            name = item.filename.replace('\\', '/')
            if not name.startswith(MODEL_FOLDER):
                raise RuntimeError(f'不在预期模型目录中的文件：{name}')
            rel = pathlib.PurePosixPath(name[len(MODEL_FOLDER):])
            if not rel.parts or any(p in ('.', '..', '') for p in rel.parts):
                raise RuntimeError(f'非法模型压缩路径：{name}')
        if DEST.exists():
            shutil.rmtree(DEST)
        DEST.mkdir(parents=True)
        for item in entries:
            rel = pathlib.PurePosixPath(item.filename[len(MODEL_FOLDER):])
            target = DEST.joinpath(*rel.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            with z.open(item) as f, target.open('wb') as dst:
                shutil.copyfileobj(f, dst)
    if not (DEST/'am'/'final.mdl').is_file():
        raise RuntimeError('模型下载内容不完整：未找到am/final.mdl')
    print('模型就绪，构建APK后可完全离线工作：', DEST)


if __name__ == '__main__':
    main()
