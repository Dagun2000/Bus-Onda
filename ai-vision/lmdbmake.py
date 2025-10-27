# make_lmdb_now.py
import sys, subprocess
from pathlib import Path

BASE = Path(r"C:\Users\ben61\Bus-Onda\ai-vision\my_ocr_project\data_for_easyocr")
DTB  = Path(r"C:\Users\ben61\Bus-Onda\ai-vision\my_ocr_project\deep-text-recognition-benchmark")

train_dir = BASE / "train"
val_dir   = BASE / ("val" if (BASE/"val").is_dir() else "validation")
lmdb_train = BASE / "lmdb" / "train"
lmdb_val   = BASE / "lmdb" / "val"
lmdb_train.mkdir(parents=True, exist_ok=True)
lmdb_val.mkdir(parents=True, exist_ok=True)

def run(script, args):
    cmd = [sys.executable, str(script), *args]
    print("RUN:", " ".join(cmd))
    subprocess.check_call(cmd)

# (create_lmdb_dataset.py에서 map_size를 8GB로 낮춰두셨다면 더 안전)
run(DTB/"create_lmdb_dataset.py", [
    "--inputPath", str(train_dir),
    "--gtFile",    str(train_dir/"gt.txt"),
    "--outputPath",str(lmdb_train),
])
run(DTB/"create_lmdb_dataset.py", [
    "--inputPath", str(val_dir),
    "--gtFile",    str(val_dir/"gt.txt"),
    "--outputPath",str(lmdb_val),
])
print("DONE. Check:", (lmdb_train/"data.mdb").is_file(), (lmdb_val/"data.mdb").is_file())
