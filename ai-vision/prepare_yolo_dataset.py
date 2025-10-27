import argparse
import shutil
from pathlib import Path
import numpy as np
import sys
import cv2


IMG_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def discover_images(images_dir: Path):
    return sorted([p for p in images_dir.glob("*") if p.suffix.lower() in IMG_EXTS])


def read_classes(source_dir: Path):
    classes_file = source_dir / "classes.txt"
    if classes_file.exists():
        try:
            names = [line.strip() for line in classes_file.read_text(encoding="utf-8").splitlines() if line.strip()]
            if names:
                return names
        except Exception:
            pass
    # Fallback: single class
    return ["object"]


def write_yaml(output_dir: Path, names: list[str]):
    yaml_path = output_dir / "data.yaml"
    root_path = str(output_dir.resolve()).replace("\\", "/")
    content = (
        f"path: {root_path}\n"
        f"train: images/train\n"
        f"val: images/val\n\n"
        f"nc: {len(names)}\n"
        f"names: {names}\n"
    )
    yaml_path.write_text(content, encoding="utf-8")
    return yaml_path


def prepare(source: Path, out: Path, train_ratio: float = 0.8, seed: int = 42):
    source = source.resolve()
    out = out.resolve()

    images_dir = source / "images"
    labels_dir = source / "labels"
    if not images_dir.exists() or not labels_dir.exists():
        raise FileNotFoundError(f"이미지/라벨 폴더를 찾을 수 없습니다: {images_dir}, {labels_dir}")

    (out / "images" / "train").mkdir(parents=True, exist_ok=True)
    (out / "images" / "val").mkdir(parents=True, exist_ok=True)
    (out / "labels" / "train").mkdir(parents=True, exist_ok=True)
    (out / "labels" / "val").mkdir(parents=True, exist_ok=True)

    images = discover_images(images_dir)
    if not images:
        raise RuntimeError(f"이미지 파일이 없습니다: {images_dir}")

    # Shuffle and split
    rng = np.random.default_rng(seed)
    indices = np.arange(len(images))
    rng.shuffle(indices)
    split = int(len(indices) * train_ratio)
    train_idx, val_idx = indices[:split], indices[split:]

    def preprocess_led_display(img):
        """
        전광판 이미지를 전처리하는 함수 (YOLO 학습 시 사용한 코드)
        1. 그레이스케일 변환
        2. CLAHE를 이용한 약한 대비 향상
        """
        if len(img.shape) == 3:
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        else:
            gray = img.copy()
        clahe = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8, 8))
        enhanced = clahe.apply(gray)
        return enhanced

    def copy_pair(img_path: Path, split_name: str):
        dst_img = out / "images" / split_name / img_path.name
        # 전처리 적용 저장 (읽기 실패 시 원본 복사)
        img = cv2.imread(str(img_path))
        if img is None:
            shutil.copy2(img_path, dst_img)
        else:
            proc = preprocess_led_display(img)
            cv2.imwrite(str(dst_img), proc)
        lbl = labels_dir / f"{img_path.stem}.txt"
        if lbl.exists():
            dst_lbl = out / "labels" / split_name / lbl.name
            shutil.copy2(lbl, dst_lbl)
        else:
            # No label file; create empty one so dataloader doesn't crash
            (out / "labels" / split_name / f"{img_path.stem}.txt").write_text("", encoding="utf-8")

    for i in train_idx:
        copy_pair(images[i], "train")
    for i in val_idx:
        copy_pair(images[i], "val")

    # classes and yaml
    names = read_classes(source)
    (out / "classes.txt").write_text("\n".join(names) + "\n", encoding="utf-8")
    yaml_path = write_yaml(out, names)

    return yaml_path


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="Prepare YOLO dataset (split + data.yaml)")
    p.add_argument("--source", required=True, help="LabelStudio YOLO export 폴더 (images/, labels/ 포함)")
    p.add_argument("--out", required=True, help="출력 폴더 (새로운 train/val 구조 생성)")
    p.add_argument("--train-ratio", type=float, default=0.8)
    p.add_argument("--seed", type=int, default=42)
    return p.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    src = Path(args.source)
    out = Path(args.out)
    yaml_path = prepare(src, out, train_ratio=args.train_ratio, seed=args.seed)
    print(f"\n✓ 데이터 준비 완료: {yaml_path}")


if __name__ == "__main__":
    main()
