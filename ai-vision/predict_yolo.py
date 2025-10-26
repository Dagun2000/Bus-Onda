import argparse
import glob
import os
import sys
from pathlib import Path
from typing import Optional, List, Tuple

import cv2
import numpy as np
from PIL import Image
import torch
import torch.nn.functional as F

try:
    from ultralytics import YOLO
except Exception as e:
    YOLO = None


IMG_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def preprocess_led_display(img):
    """
    전광판 이미지 전처리 (그레이스케일 + 약한 CLAHE 대비 향상)
    """
    if len(img.shape) == 3:
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    else:
        gray = img.copy()
    clahe = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)
    return enhanced


def discover_images(src: Path) -> List[Path]:
    if src.is_dir():
        return sorted([p for p in src.iterdir() if p.suffix.lower() in IMG_EXTS])
    if src.is_file() and src.suffix.lower() in IMG_EXTS:
        return [src]
    return []


def latest_best_weights(project_dir: Path) -> Optional[Path]:
    patterns = [
        project_dir / "detect" / "**" / "weights" / "best.pt",
        project_dir / "**" / "weights" / "best.pt",
    ]
    candidates: List[Path] = []
    for pat in patterns:
        for m in glob.glob(str(pat), recursive=True):
            candidates.append(Path(m))
    if not candidates:
        return None
    # 가장 최근 수정 기준 정렬
    candidates.sort(key=lambda p: p.stat().st_mtime)
    return candidates[-1]

def ensure_color(img):
    if len(img.shape) == 2 or (len(img.shape) == 3 and img.shape[2] == 1):
        return cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
    return img


def select_box(boxes, classes: List[int], image_shape: Tuple[int, int], strategy: str = "largest") -> Optional[Tuple[int, int, int, int]]:
    h, w = image_shape[:2]
    cx_img, cy_img = w // 2, h // 2
    best = None
    best_metric = -1.0
    for b in boxes:
        cls_id = int(b.cls[0])
        if cls_id not in classes:
            continue
        x1, y1, x2, y2 = map(int, b.xyxy[0])
        if strategy == "center":
            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
            dist = ((cx - cx_img) ** 2 + (cy - cy_img) ** 2) ** 0.5
            metric = -dist
        else:
            metric = max(0, x2 - x1) * max(0, y2 - y1)
        if metric > best_metric:
            best_metric = metric
            best = (x1, y1, x2, y2)
    return best


def pad_box(x1, y1, x2, y2, pad_ratio: float, w: int, h: int):
    pw = int((x2 - x1) * pad_ratio)
    ph = int((y2 - y1) * pad_ratio)
    nx1 = max(0, x1 - pw)
    ny1 = max(0, y1 - ph)
    nx2 = min(w - 1, x2 + pw)
    ny2 = min(h - 1, y2 + ph)
    return nx1, ny1, nx2, ny2


def run_predict(
    source: Path,
    out_dir: Path,
    weights: Optional[Path] = None,
    imgsz: int = 640,
    conf: float = 0.25,
    device: str = "0",
    name: str = "predict",
    project: Optional[Path] = None,
    # two-stage options
    coarse: str = "yolov8n.pt",
    coarse_classes: List[int] = [5, 2, 7],
    strategy: str = "largest",
    pad_ratio: float = 0.05,
    imgsz1: int = 640,
    imgsz2: int = 640,
    fine_conf: float = 0.25,
    # OCR options
    ocr_dtb: Optional[Path] = None,
    ocr_ckpt: Optional[Path] = None,
):
    if YOLO is None:
        raise RuntimeError("ultralytics가 설치되어 있지 않습니다. 'pip install ultralytics' 후 다시 시도하세요.")

    source = Path(source)
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # fine weights 자동 탐색
    if weights is None:
        default_runs = Path(__file__).resolve().parent / "runs"
        weights = latest_best_weights(default_runs)
        if weights is None:
            raise FileNotFoundError("best.pt를 찾지 못했습니다. --weights로 명시하거나 ai-vision/runs를 확인하세요.")
    weights = Path(weights)

    print("=== Two-stage Predict (coarse -> fine) ===")
    print(f"source     : {source}")
    print(f"coarse     : {coarse}")
    print(f"fine weight: {weights}")
    print(f"device     : {device}")

    images = discover_images(source)
    if not images:
        raise FileNotFoundError(f"이미지 파일을 찾지 못했습니다: {source}")

    vis_dir = out_dir / name
    crop_dir = vis_dir / "crops"
    proc_dir = vis_dir / "processed"
    vis_dir.mkdir(parents=True, exist_ok=True)
    crop_dir.mkdir(parents=True, exist_ok=True)
    proc_dir.mkdir(parents=True, exist_ok=True)

    coarse_model = YOLO(coarse)
    fine_model = YOLO(str(weights))

    # ---------- OCR init (DTRB) ----------
    ocr_model = None
    AlignCollate = None
    CTCLabelConverter = None
    OCR_DEVICE = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    try:
        if ocr_dtb is None:
            ocr_dtb = Path(__file__).resolve().parent / "my_ocr_project" / "deep-text-recognition-benchmark"
        if ocr_ckpt is None:
            ocr_ckpt = Path(__file__).resolve().parent / "saved_models" / "BusDigits-TPSResNet-CTC" / "best_accuracy.pth"
        ocr_dtb = Path(ocr_dtb)
        ocr_ckpt = Path(ocr_ckpt)
        if not ocr_dtb.exists() or not ocr_ckpt.exists():
            raise FileNotFoundError("OCR 경로(코드/체크포인트)를 찾을 수 없습니다.")
        sys.path.insert(0, str(ocr_dtb))
        from dataset import AlignCollate as _AlignCollate
        from utils import CTCLabelConverter as _CTCLabelConverter
        from model import Model as _Model

        class Opt:
            Transformation = 'TPS'
            FeatureExtraction = 'ResNet'
            SequenceModeling = 'BiLSTM'
            Prediction = 'CTC'
            num_fiducial = 20
            input_channel = 1
            output_channel = 512
            hidden_size = 256
            imgH = 32
            imgW = 120
            PAD = True
            character = "0123456789"
            rgb = False
            batch_max_length = 5

        converter = _CTCLabelConverter(Opt.character)
        Opt.num_class = len(converter.character)
        model = _Model(Opt)
        model = torch.nn.DataParallel(model).to(OCR_DEVICE)
        ckpt = torch.load(str(ocr_ckpt), map_location='cpu')
        model.load_state_dict(ckpt, strict=False)
        model.eval()

        ocr_model = model
        AlignCollate = _AlignCollate
        CTCLabelConverter = converter

        ROT_DEGS = [0.0, -2.5, -1.5, +1.5, +2.5]
        IMGW_LIST = [100, 120, 140]

        def _pil_to_cv_gray(pil_img):
            if pil_img.mode != 'L':
                pil_img = pil_img.convert('L')
            return np.array(pil_img)

        def _slight_rotate(img, deg):
            h, w = img.shape[:2]
            M = cv2.getRotationMatrix2D((w/2, h/2), deg, 1.0)
            return cv2.warpAffine(img, M, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)

        def _postprocess_digits(s: str):
            s = ''.join(ch for ch in s if ch.isdigit())
            if not s:
                return s
            out = []
            for ch in s:
                if len(out) >= 2 and out[-1] == ch and out[-2] == ch:
                    continue
                out.append(ch)
            s = ''.join(out)
            return s[:5] if len(s) > 5 else s

        def _score_len_bias(s: str):
            L = len(s)
            if L == 4: return 0.27
            if L == 3: return 0.27
            if L == 2: return 0.14
            if L == 5: return 0.06
            if L == 1: return 0.02
            return -0.10

        def _decode_ctc(preds):
            preds_log_softmax = F.log_softmax(preds, dim=2)
            probs = preds_log_softmax.exp()
            max_probs, idxs = probs.max(2)
            preds_size = torch.IntTensor([preds.size(1)] * preds.size(0))
            texts = CTCLabelConverter.decode(idxs, preds_size)
            conf = max_probs.mean(dim=1).cpu().numpy().tolist()
            return texts, conf

        def _pick_best(votes):
            v2 = [v for v in votes if v[0] != ""]
            if not v2:
                best = max(votes, key=lambda x: x[1])
                return _postprocess_digits(best[2]), best[1], best[2]
            scored = [(pp, (conf + _score_len_bias(pp)), raw) for (pp, conf, raw) in v2]
            from collections import defaultdict
            grp = defaultdict(list)
            for pp, sc, raw in scored:
                grp[pp].append(sc)
            best_str, best_count, best_avg = None, -1, -1e9
            for k, arr in grp.items():
                c = len(arr)
                avg = sum(arr)/len(arr)
                if c > best_count or (c == best_count and avg > best_avg):
                    best_str, best_count, best_avg = k, c, avg
            raw_sample = ""
            for pp, sc, raw in scored:
                if pp == best_str:
                    raw_sample = raw
                    break
            return best_str, best_avg, raw_sample

        def _ocr_run(gray_np):
            pil = Image.fromarray(gray_np)
            votes = []
            with torch.no_grad():
                for iw in IMGW_LIST:
                    collate = AlignCollate(imgH=32, imgW=iw, keep_ratio_with_pad=True)
                    for deg in ROT_DEGS:
                        g = _pil_to_cv_gray(pil)
                        if abs(deg) > 1e-6:
                            g = _slight_rotate(g, deg)
                        pil_g = Image.fromarray(g)
                        imgs, _ = collate([(pil_g, "dummy")])
                        imgs = imgs.to(OCR_DEVICE)
                        preds = ocr_model(imgs, None)
                        txts, confs = _decode_ctc(preds)
                        raw = txts[0]
                        pp = _postprocess_digits(raw)
                        votes.append((pp, confs[0], raw))
            return _pick_best(votes)

    except Exception as e:
        print(f"[경고] OCR 초기화 실패: {e}. OCR 단계를 건너뜁니다.")
        ocr_model = None

    for img_path in images:
        img0 = cv2.imread(str(img_path), cv2.IMREAD_UNCHANGED)
        if img0 is None:
            print(f"경고: 이미지를 읽을 수 없습니다: {img_path}")
            continue
        h, w = img0.shape[:2]
        img_for_coarse = ensure_color(img0)

        # Stage 1: coarse detection
        r1 = coarse_model.predict(
            source=img_for_coarse,
            imgsz=int(imgsz1),
            conf=float(conf),
            device=str(device),
            verbose=False,
        )[0]
        box = select_box(r1.boxes, classes=coarse_classes, image_shape=img_for_coarse.shape, strategy=strategy)
        if box is None:
            x1, y1, x2, y2 = 0, 0, w - 1, h - 1
            print(f"버스 후보가 없어 전체 이미지 사용: {img_path.name}")
        else:
            x1, y1, x2, y2 = pad_box(*box, pad_ratio=pad_ratio, w=w, h=h)

        roi = img_for_coarse[y1:y2, x1:x2].copy()
        if roi.size == 0:
            print(f"경고: ROI가 비어 세부 탐지 불가: {img_path.name}")
            continue

        # Stage 2: preprocess ROI and fine detection
        proc = preprocess_led_display(roi)
        proc_bgr = cv2.cvtColor(proc, cv2.COLOR_GRAY2BGR)
        cv2.imwrite(str(proc_dir / img_path.name), proc_bgr)

        r2 = fine_model.predict(
            source=proc_bgr,
            imgsz=int(imgsz2),
            conf=float(fine_conf),
            device=str(device),
            verbose=False,
        )[0]

        # Visualization on full image
        vis = ensure_color(img0).copy()
        cv2.rectangle(vis, (x1, y1), (x2, y2), (255, 0, 0), 3)  # coarse box
        best_det = None
        best_conf = -1.0
        for b in r2.boxes:
            fx1, fy1, fx2, fy2 = map(int, b.xyxy[0])
            ox1, oy1, ox2, oy2 = x1 + fx1, y1 + fy1, x1 + fx2, y1 + fy2
            cv2.rectangle(vis, (ox1, oy1), (ox2, oy2), (0, 0, 255), 3)
            if hasattr(b, "conf"):
                confv = float(b.conf[0])
                cv2.putText(vis, f"{confv:.2f}", (ox1, max(0, oy1 - 5)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)
                if confv > best_conf:
                    best_conf = confv
                    best_det = (fx1, fy1, fx2, fy2)

        cv2.imwrite(str(crop_dir / img_path.name), roi)
        # OCR: choose highest-confidence fine box, fallback to whole processed ROI
        if ocr_model is not None:
            try:
                if best_det is not None:
                    fx1, fy1, fx2, fy2 = best_det
                    sub = proc[fy1:fy2, fx1:fx2]
                    # If empty due to rounding, fallback
                    if sub.size == 0:
                        sub = proc
                else:
                    sub = proc
                text, score, raw = _ocr_run(sub)
                label = f"OCR:{text} ({score:.2f})" if text else f"OCR:-(raw:{raw})"
                cv2.putText(vis, label, (x1, max(0, y1 - 10)), cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 0), 2)
                print(f"{img_path.name} => OCR: {text} (~{score:.3f}) raw:{raw}")
            except Exception as e:
                print(f"[경고] OCR 실패({img_path.name}): {e}")
        cv2.imwrite(str(vis_dir / img_path.name), vis)

    print(f"\n✓ 예측 완료. 시각화: {vis_dir} | 크롭: {crop_dir} | 전처리: {proc_dir}")
    return vis_dir


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="Two-stage YOLO + OCR predict: coarse(bus) -> fine(number) -> OCR")
    p.add_argument("--source", default=None, help="이미지 파일 또는 디렉토리 (미지정 시 자동 탐색)")
    p.add_argument("--out", default=str(Path(__file__).resolve().parent / "runs" / "infer"), help="출력 기본 디렉토리")
    p.add_argument("--weights", default=None, help="세부 모델 가중치(best.pt). 미지정 시 runs에서 자동 탐색")
    p.add_argument("--device", default="0")
    # coarse stage
    p.add_argument("--coarse", default="yolov8n.pt", help="1차 모델(일반 COCO)")
    p.add_argument("--coarse-classes", nargs="*", type=int, default=[5, 2, 7], help="버스 후보로 간주할 COCO class ids")
    p.add_argument("--imgsz1", type=int, default=640)
    p.add_argument("--conf", type=float, default=0.25, help="1차 모델 confidence")
    p.add_argument("--strategy", choices=["largest", "center"], default="largest")
    p.add_argument("--pad-ratio", type=float, default=0.05)
    # fine stage
    p.add_argument("--imgsz2", type=int, default=640)
    p.add_argument("--fine-conf", type=float, default=0.25)
    # saving
    p.add_argument("--name", default="predict")
    p.add_argument("--project", default=None, help="Ultralytics 결과 저장 디렉토리(사용 안함, 내부 저장)")
    # OCR
    p.add_argument("--ocr-dtb", default=str(Path(__file__).resolve().parent / "my_ocr_project" / "deep-text-recognition-benchmark"))
    p.add_argument("--ocr-ckpt", default=str(Path(__file__).resolve().parent / "saved_models" / "BusDigits-TPSResNet-CTC" / "best_accuracy.pth"))
    return p.parse_args(argv)


def _resolve_default_source() -> Path:
    """Pick a sensible default source directory/file if none given."""
    base = Path(__file__).resolve().parent
    candidates = [
        base / "test_images_yolo",
        base / "test_images2" / "gray_preview",
        base / "test_images2",
        base / "test_images",
        base / "test_images" / "final_result.jpg",
    ]
    for c in candidates:
        if c.exists():
            return c
    # Fallback to base dir; run_predict will error with clear message
    return base


def main(argv=None):
    args = parse_args(argv)
    # Resolve defaults if user provided nothing
    if args.source is None:
        args.source = str(_resolve_default_source())
    # Auto device fallback: if CUDA unavailable, force cpu
    if args.device == "0" or args.device == "cuda":
        try:
            import torch  # local import to avoid hard dep at import-time
            if not torch.cuda.is_available():
                args.device = "cpu"
        except Exception:
            args.device = "cpu"
    run_predict(
        source=Path(args.source),
        out_dir=Path(args.out),
        weights=Path(args.weights) if args.weights else None,
        imgsz=args.imgsz1 if hasattr(args, 'imgsz1') else 640,
        conf=args.conf,
        device=args.device,
        name=args.name,
        project=Path(args.project) if args.project else None,
        coarse=args.coarse,
        coarse_classes=args.coarse_classes,
        strategy=args.strategy,
        pad_ratio=args.pad_ratio,
        imgsz1=args.imgsz1,
        imgsz2=args.imgsz2,
        fine_conf=args.fine_conf,
        ocr_dtb=Path(args.ocr_dtb) if args.ocr_dtb else None,
        ocr_ckpt=Path(args.ocr_ckpt) if args.ocr_ckpt else None,
    )


if __name__ == "__main__":
    main()
