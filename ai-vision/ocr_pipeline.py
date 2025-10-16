# ocr_pipeline.py
# -*- coding: utf-8 -*-
import argparse
import io
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

PNG_RE   = re.compile(r"^.+\.png$", re.IGNORECASE)
DIGIT_RE = re.compile(r"^\d+$")

def log(msg: str):
    print(f"[INFO] {msg}", flush=True)

def warn(msg: str):
    print(f"[WARN] {msg}", flush=True)

def err(msg: str):
    print(f"[ERR]  {msg}", flush=True)

def clean_gt(dirpath: Path):
    """gt.txt 정리: 탭 보장, 숫자라벨만, CRLF 저장, 존재/중복 체크 리포트"""
    gt_path = dirpath / "gt.txt"
    if not gt_path.is_file():
        warn(f"gt.txt not found: {gt_path}")
        return False

    bak = gt_path.with_suffix(".txt.bak")
    shutil.copyfile(str(gt_path), str(bak))
    log(f"BACKUP -> {bak}")

    raw = gt_path.read_text(encoding="utf-8", errors="ignore")
    lines = raw.replace("\r\n", "\n").replace("\r", "\n").split("\n")

    out = []
    issues, missing, dup = [], [], []
    seen = set()

    for i, line in enumerate(lines, start=1):
        s = line.strip()
        if not s:
            continue

        # 공백/탭 혼용 → 하나의 탭으로 강제
        if "\t" in s:
            parts = s.split("\t", 1)
        else:
            parts = re.split(r"\s+", s, maxsplit=1)

        if len(parts) != 2:
            issues.append(f"LINE {i}: BAD FORMAT -> {line!r}")
            continue

        fname, label = parts[0].strip(), parts[1].strip()
        if not PNG_RE.match(fname):
            issues.append(f"LINE {i}: BAD FILENAME -> {fname!r}")
            continue
        if not DIGIT_RE.match(label):
            issues.append(f"LINE {i}: NON-DIGIT LABEL -> {label!r}")
            continue

        out.append((fname, label))

    filenames = set()
    for fname, _ in out:
        img_path = dirpath / fname
        if not img_path.is_file():
            missing.append(fname)
        if fname in filenames:
            dup.append(fname)
        filenames.add(fname)

    # 저장 (CRLF)
    cleaned = "\r\n".join([f"{f}\t{l}" for f, l in out])
    if cleaned and not cleaned.endswith("\r\n"):
        cleaned += "\r\n"
    gt_path.write_text(cleaned, encoding="utf-8")

    if issues:
        (dirpath / "gt_issues.txt").write_text("\r\n".join(issues) + "\r\n", encoding="utf-8")
        warn(f"Issues -> {dirpath/'gt_issues.txt'} ({len(issues)})")
    if missing:
        (dirpath / "gt_missing.txt").write_text("\r\n".join(sorted(set(missing))) + "\r\n", encoding="utf-8")
        warn(f"Missing images -> {dirpath/'gt_missing.txt'} ({len(set(missing))})")
    if dup:
        (dirpath / "gt_duplicates.txt").write_text("\r\n".join(sorted(set(dup))) + "\r\n", encoding="utf-8")
        warn(f"Duplicates -> {dirpath/'gt_duplicates.txt'} ({len(set(dup))})")

    log(f"DONE clean_gt: {dirpath} | valid lines = {len(out)}")
    return True

def auto_find_dtb(start_from: Path) -> Path | None:
    """start_from 하위에서 deep-text-recognition-benchmark 폴더 자동 탐색"""
    target_name = "deep-text-recognition-benchmark"
    # 먼저 바로 하위에서 확인
    cand = start_from / target_name
    if cand.is_dir():
        return cand

    # 재귀 탐색(너무 깊지 않게 제한)
    for p in start_from.rglob("deep-text-recognition-benchmark"):
        if p.is_dir():
            return p
    return None

def run_py(script: Path, args: list[str]):
    """현재 파이썬 인터프리터로 다른 파이썬 스크립트 실행"""
    cmd = [sys.executable, str(script), *args]
    log("RUN: " + " ".join([f'"{c}"' if " " in c else c for c in cmd]))
    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if proc.returncode != 0:
        err(f"returncode={proc.returncode}")
        if proc.stdout:
            print(proc.stdout)
        if proc.stderr:
            print(proc.stderr)
        raise SystemExit(proc.returncode)
    else:
        if proc.stdout:
            print(proc.stdout)
        if proc.stderr:
            print(proc.stderr)

def ensure_dir(p: Path):
    p.mkdir(parents=True, exist_ok=True)

def main():
    ap = argparse.ArgumentParser(description="GT 정리 → LMDB 생성 → (옵션) 훈련 파이프라인")
    ap.add_argument("--base", required=True, help="data_for_easyocr 경로 (train / val|validation 이 들어있는 상위)")
    ap.add_argument("--dtb",  default="",   help="deep-text-recognition-benchmark 폴더 경로 (미지정 시 자동 탐색)")
    ap.add_argument("--ckpt", default="",   help="사전학습 체크포인트 .pth (지정 시 훈련 수행)")
    ap.add_argument("--imgH", type=int, default=32)
    ap.add_argument("--imgW", type=int, default=100)
    ap.add_argument("--batch_size", type=int, default=128)
    ap.add_argument("--num_iter", type=int, default=60000)
    ap.add_argument("--valInterval", type=int, default=1000)
    args = ap.parse_args()

    base = Path(args.base).expanduser().resolve()
    if not base.is_dir():
        err(f"--base 폴더가 없습니다: {base}")
        raise SystemExit(1)

    # train / val(or validation) 감지
    train_dir = base / "train"
    val_dir   = base / "val"
    if not val_dir.is_dir():
        alt = base / "validation"
        if alt.is_dir():
            val_dir = alt
        else:
            err("검증 폴더를 찾을 수 없습니다. 'val' 또는 'validation' 중 하나가 있어야 합니다.")
            raise SystemExit(1)

    # 0) GT 정리/검증
    ok_tr = clean_gt(train_dir)
    ok_vl = clean_gt(val_dir)
    if not ok_tr or not ok_vl:
        err("gt.txt 정리 실패. 위 경고 파일(gt_issues.txt 등) 확인 후 수정해주세요.")
        raise SystemExit(1)

    # DTB 폴더 확보
    dtb = Path(args.dtb).expanduser().resolve() if args.dtb else None
    if not dtb or not dtb.is_dir():
        # base 상위에서 자동 탐색(프로젝트 루트 쪽에 있을 확률이 높음)
        dtb = auto_find_dtb(base.parent)
    if not dtb or not dtb.is_dir():
        err("deep-text-recognition-benchmark 폴더를 찾을 수 없습니다. --dtb 로 지정하거나 레포를 클론하세요.")
        print("예) git clone https://github.com/clovaai/deep-text-recognition-benchmark.git")
        raise SystemExit(1)

    # 스크립트 존재 확인
    create_lmdb = dtb / "create_lmdb_dataset.py"
    train_py    = dtb / "train.py"
    if not create_lmdb.is_file():
        err(f"파일 없음: {create_lmdb}")
        raise SystemExit(1)
    if not train_py.is_file():
        err(f"파일 없음: {train_py}")
        raise SystemExit(1)

    # 1) LMDB 생성
    lmdb_train = base / "lmdb" / "train"
    lmdb_val   = base / "lmdb" / "val"
    ensure_dir(lmdb_train.parent)

    run_py(create_lmdb, [
        "--inputPath", str(train_dir),
        "--gtFile",    str(train_dir / "gt.txt"),
        "--outputPath",str(lmdb_train)
    ])
    run_py(create_lmdb, [
        "--inputPath", str(val_dir),
        "--gtFile",    str(val_dir / "gt.txt"),
        "--outputPath",str(lmdb_val)
    ])

    # 2) (옵션) 훈련
    if args.ckpt:
        ckpt = Path(args.ckpt).expanduser().resolve()
        if not ckpt.is_file():
            err(f"체크포인트 파일이 없습니다: {ckpt}")
            raise SystemExit(1)

        # 숫자만 문자집합
        characters = "0123456789"

        train_args = [
            "--train_data", str(lmdb_train),
            "--valid_data", str(lmdb_val),
            "--Transformation", "TPS",
            "--FeatureExtraction", "ResNet",
            "--SequenceModeling", "BiLSTM",
            "--Prediction", "CTC",
            "--saved_model", str(ckpt),
            "--character", characters,
            "--batch_size", str(args.batch_size),
            "--imgH", str(args.imgH),
            "--imgW", str(args.imgW),
            "--num_iter", str(args.num_iter),
            "--valInterval", str(args.valInterval),
            "--workers", "4",
            "--sensitive", "False",
        ]
        run_py(train_py, train_args)
        log("TRAINING finished.")
    else:
        log("체크포인트(--ckpt) 미지정: 훈련은 건너뜁니다. LMDB까지 완료.")

if __name__ == "__main__":
    main()
