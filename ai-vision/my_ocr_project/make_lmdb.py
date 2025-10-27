# -*- coding: utf-8 -*-
import os, sys, subprocess
from pathlib import Path

BASE = Path(r"C:\Users\ben61\Bus-Onda\ai-vision\my_ocr_project\data_for_easyocr")
DTB  = Path(r"C:\Users\ben61\Bus-Onda\ai-vision\my_ocr_project\deep-text-recognition-benchmark")

train = BASE / "train"
val   = BASE / ("val" if (BASE/"val").is_dir() else "validation")
lmdb_train = BASE / "lmdb" / "train"
lmdb_val   = BASE / "lmdb" / "val"

for d in [lmdb_train, lmdb_val]:
    d.parent.mkdir(parents=True, exist_ok=True)

cli = [
    [sys.executable, str(DTB/"create_lmdb_dataset.py"),
     "--inputPath", str(train), "--gtFile", str(train/"gt.txt"), "--outputPath", str(lmdb_train)],
    [sys.executable, str(DTB/"create_lmdb_dataset.py"),
     "--inputPath", str(val),   "--gtFile", str(val/"gt.txt"),   "--outputPath", str(lmdb_val)],
]

for cmd in cli:
    print("RUN:", " ".join(cmd))
    subprocess.check_call(cmd)

print("DONE. 생성된 파일 확인:",
      os.path.isfile(lmdb_train/"data.mdb"), os.path.isfile(lmdb_val/"data.mdb"))
