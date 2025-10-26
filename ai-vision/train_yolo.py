import argparse
import os
from pathlib import Path
import sys
import glob

try:
    from ultralytics import YOLO
except Exception as e:
    YOLO = None


def train(
    data: str,
    model: str = "yolov8n.pt",
    epochs: int = 100,
    imgsz: int = 640,
    batch: int = 16,
    device: str = "0",
    name: str = "bus_detector",
    project: Path | None = None,
    workers: int = 4,
    patience: int = 50,
    optimizer: str = "auto",
    lr0: float | None = None,
    resume: bool = False,
):
    """
    YOLOv8 학습 실행 함수

    Args:
        data: data.yaml 경로
        model: 사전학습 가중치(.pt) 또는 모델 구성(.yaml), 기본값 'yolov8n.pt'
        epochs: 학습 에폭 수
        imgsz: 입력 이미지 크기 (한 변)
        batch: 배치 크기
        device: 장치 지정 ('cpu', '0', '0,1' 등)
        name: 실험 이름 (runs 하위 디렉토리명)
        project: 결과 저장 기본 디렉토리 (기본: ai-vision/runs)
        workers: 데이터 로딩 워커 수
        patience: 조기 종료(EarlyStopping) patience
        optimizer: 옵티마이저 (예: 'Adam', 'AdamW', 'SGD', 'auto')
        lr0: 초기 학습률 (None이면 라이브러리 기본값)
        resume: 이전 학습 재개 여부
    """

    if YOLO is None:
        raise RuntimeError(
            "ultralytics 패키지를 찾을 수 없습니다. requirements에 따라 설치 후 다시 시도하세요."
        )

    data_path = Path(data).resolve()
    if not data_path.exists():
        raise FileNotFoundError(f"data.yaml을 찾을 수 없습니다: {data}")

    # 기본 project 경로: 현재 파일(ai-vision) 기준 runs
    if project is None:
        project = Path(__file__).resolve().parent / "runs"
    project = Path(project)
    project.mkdir(parents=True, exist_ok=True)

    print("=== YOLOv8 Train Start ===")
    print(f"data     : {data_path}")
    print(f"model    : {model}")
    print(f"epochs   : {epochs}")
    print(f"imgsz    : {imgsz}")
    print(f"batch    : {batch}")
    print(f"device   : {device}")
    print(f"project  : {project}")
    print(f"name     : {name}")
    print(f"workers  : {workers}")
    print(f"patience : {patience}")
    print(f"optimizer: {optimizer}")
    if lr0 is not None:
        print(f"lr0      : {lr0}")
    if resume:
        print("resume   : True")

    # 모델 로드 (사전학습 .pt 또는 구조 .yaml)
    model_obj = YOLO(model)

    train_kwargs = dict(
        data=str(data_path),
        epochs=int(epochs),
        imgsz=int(imgsz),
        batch=int(batch),
        device=str(device),
        name=str(name),
        project=str(project),
        workers=int(workers),
        patience=int(patience),
        optimizer=str(optimizer),
        save=True,
        verbose=True,
        resume=bool(resume),
    )
    if lr0 is not None:
        train_kwargs["lr0"] = float(lr0)

    results = model_obj.train(**train_kwargs)

    # 검증 (선택적으로 data 명시)
    try:
        metrics = model_obj.val(data=str(data_path))
        if hasattr(metrics, "box") and hasattr(metrics.box, "map50"):
            print(f"mAP50     : {metrics.box.map50:.4f}")
        if hasattr(metrics, "box") and hasattr(metrics.box, "map"):
            print(f"mAP50-95  : {metrics.box.map:.4f}")
    except Exception as e:
        print(f"검증 중 예외 발생(무시): {e}")

    # best.pt 경로 탐색
    best_path = None
    # 우선적으로 라이브러리가 알려주는 경로 시도
    for candidate in [
        getattr(getattr(model_obj, "trainer", None), "best", None),
        getattr(results, "save_dir", None),
    ]:
        if candidate:
            cpath = Path(candidate)
            if cpath.is_file() and cpath.name == "best.pt":
                best_path = cpath
                break
            # save_dir일 경우 weights/best.pt 확인
            if cpath.is_dir():
                bp = cpath / "weights" / "best.pt"
                if bp.exists():
                    best_path = bp
                    break

    if best_path is None:
        # 일반적인 기본 위치 검색
        patterns = [
            project / "detect" / f"{name}*" / "weights" / "best.pt",
            project / "detect" / "train*" / "weights" / "best.pt",
            project / f"{name}*" / "weights" / "best.pt",
        ]
        for pat in patterns:
            matches = sorted(glob.glob(str(pat)))
            if matches:
                best_path = Path(matches[-1])
                break

    if best_path and best_path.exists():
        print(f"\n✓ 학습 완료! best.pt: {best_path}")
    else:
        print("\n경고: best.pt 파일을 찾지 못했습니다. runs 폴더를 확인하세요.")

    return best_path


def parse_args(argv=None):
    p = argparse.ArgumentParser(description="YOLOv8 Training Script (ai-vision)")
    p.add_argument("--data", required=True, help="data.yaml 경로")
    p.add_argument("--model", default="yolov8n.pt", help="사전학습 가중치(.pt) 또는 모델 구성(.yaml)")
    p.add_argument("--epochs", type=int, default=100)
    p.add_argument("--imgsz", type=int, default=640)
    p.add_argument("--batch", type=int, default=16)
    p.add_argument("--device", default="0", help="'cpu', '0', '0,1' 등")
    p.add_argument("--name", default="bus_detector")
    p.add_argument(
        "--project",
        default=str(Path(__file__).resolve().parent / "runs"),
        help="결과 저장 디렉토리",
    )
    p.add_argument("--workers", type=int, default=4)
    p.add_argument("--patience", type=int, default=50)
    p.add_argument("--optimizer", default="auto")
    p.add_argument("--lr0", type=float, default=None)
    p.add_argument("--resume", action="store_true")
    return p.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    best = train(
        data=args.data,
        model=args.model,
        epochs=args.epochs,
        imgsz=args.imgsz,
        batch=args.batch,
        device=args.device,
        name=args.name,
        project=Path(args.project),
        workers=args.workers,
        patience=args.patience,
        optimizer=args.optimizer,
        lr0=args.lr0,
        resume=args.resume,
    )
    if best:
        print(f"Best weights: {best}")


if __name__ == "__main__":
    main()
