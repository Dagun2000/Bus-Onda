import os
import cv2
import numpy as np
from pathlib import Path
import shutil
from ultralytics import YOLO

class BusNumberPreprocessor:
    """전광판 이미지 전처리 클래스"""
    
    @staticmethod
    def preprocess_led_display(img):
        """
        전광판 이미지를 가볍게 전처리 (거의 원본 유지)
        """
        # 1. 그레이스케일 변환만 수행
        if len(img.shape) == 3:
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        else:
            gray = img.copy()
        
        # 2. 매우 약한 대비 향상만 적용
        clahe = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8,8))
        enhanced = clahe.apply(gray)
        
        # 그냥 이대로 반환 (추가 처리 없음)
        return enhanced

def prepare_yolo_dataset(source_dir, output_dir, train_ratio=0.8):
    """
    Label Studio에서 export한 YOLO 데이터를 전처리하여 학습용으로 준비
    
    Args:
        source_dir: YOLO export 폴더 경로
        output_dir: 출력 폴더 경로
        train_ratio: train/val 분할 비율
    """
    preprocessor = BusNumberPreprocessor()
    
    source_path = Path(source_dir).resolve()  # 절대 경로
    output_path = Path(output_dir).resolve()  # 절대 경로
    
    # 출력 폴더 구조 생성
    (output_path / 'images' / 'train').mkdir(parents=True, exist_ok=True)
    (output_path / 'images' / 'val').mkdir(parents=True, exist_ok=True)
    (output_path / 'labels' / 'train').mkdir(parents=True, exist_ok=True)
    (output_path / 'labels' / 'val').mkdir(parents=True, exist_ok=True)
    
    # 원본 이미지와 라벨 파일 목록
    images_dir = source_path / 'images'
    labels_dir = source_path / 'labels'
    
    image_files = sorted([f for f in images_dir.glob('*') if f.suffix.lower() in ['.jpg', '.jpeg', '.png', '.webp']])
    
    print(f"총 {len(image_files)}개 이미지 발견")
    
    # train/val 분할
    np.random.seed(42)
    image_files_shuffled = image_files.copy()
    np.random.shuffle(image_files_shuffled)
    split_idx = int(len(image_files_shuffled) * train_ratio)
    train_files = image_files_shuffled[:split_idx]
    val_files = image_files_shuffled[split_idx:]
    
    print(f"Train: {len(train_files)}, Val: {len(val_files)}")
    
    # 전처리 및 복사
    for split_name, files in [('train', train_files), ('val', val_files)]:
        print(f"\n{split_name} 데이터 처리 중...")
        
        for idx, img_file in enumerate(files):
            # 이미지 읽기
            img = cv2.imread(str(img_file))
            if img is None:
                print(f"Warning: {img_file.name} 읽기 실패")
                continue
            
            # 전처리 적용
            processed_img = preprocessor.preprocess_led_display(img)
            
            # 저장
            output_img_path = output_path / 'images' / split_name / img_file.name
            cv2.imwrite(str(output_img_path), processed_img)
            
            # 라벨 파일 복사
            label_file = labels_dir / f"{img_file.stem}.txt"
            if label_file.exists():
                output_label_path = output_path / 'labels' / split_name / label_file.name
                shutil.copy(label_file, output_label_path)
            
            if (idx + 1) % 10 == 0:
                print(f"  {idx + 1}/{len(files)} 완료")
    
    # classes.txt 복사
    classes_file = source_path / 'classes.txt'
    if classes_file.exists():
        shutil.copy(classes_file, output_path / 'classes.txt')
        with open(classes_file, 'r', encoding='utf-8') as f:
            classes = [line.strip() for line in f.readlines()]
    else:
        classes = ['bus_number']
        with open(output_path / 'classes.txt', 'w', encoding='utf-8') as f:
            f.write('bus_number\n')
    
    # data.yaml 생성 (절대 경로, 백슬래시를 슬래시로)
    yaml_content = f"""path: {str(output_path).replace(chr(92), '/')}
train: images/train
val: images/val

nc: {len(classes)}
names: {classes}
"""
    
    yaml_path = output_path / 'data.yaml'
    with open(yaml_path, 'w', encoding='utf-8') as f:
        f.write(yaml_content)
    
    print(f"\n✓ 데이터 준비 완료!")
    print(f"  출력 경로: {output_path}")
    print(f"  data.yaml: {yaml_path}")
    
    return str(yaml_path)

def train_yolo_model(data_yaml, model_size='n', epochs=100, img_size=640, batch_size=16):
    """
    YOLO 모델 학습
    
    Args:
        data_yaml: data.yaml 파일 경로
        model_size: 모델 크기 ('n', 's', 'm', 'l', 'x')
        epochs: 학습 에폭 수
        img_size: 입력 이미지 크기
        batch_size: 배치 크기
    """
    print(f"\n=== YOLO 모델 학습 시작 ===")
    print(f"모델: YOLOv8{model_size}")
    print(f"Epochs: {epochs}")
    print(f"Image size: {img_size}")
    print(f"Batch size: {batch_size}\n")
    
    # 모델 로드
    model = YOLO(f'yolov8{model_size}.pt')
    
    # 학습 실행
    results = model.train(
        data=data_yaml,
        epochs=epochs,
        imgsz=img_size,
        batch=batch_size,
        name='bus_number_detector',
        project=r'C:\Users\ben61\Bus-Onda\ai-vision\runs',  # runs 폴더 위치 지정
        patience=0,  # EarlyStopping 비활성화
        save=True,
        device=0,
        workers=4,
        optimizer='AdamW',
        lr0=0.01,
    )
    
    # 검증
    print("\n=== 모델 검증 ===")
    metrics = model.val()
    print(f"mAP50: {metrics.box.map50:.4f}")
    print(f"mAP50-95: {metrics.box.map:.4f}")
    
    # 실제 저장된 경로 찾기 (여러 경로 확인)
    import glob
    search_paths = [
        r"C:\Users\ben61\Bus-Onda\ai-vision\runs\detect\bus_number_detector*/weights/best.pt",
        r"C:\Users\ben61\Bus-Onda\runs\detect\bus_number_detector*/weights/best.pt",
        "runs/detect/bus_number_detector*/weights/best.pt"
    ]
    
    best_model = None
    for path in search_paths:
        best_models = glob.glob(path)
        if best_models:
            best_model = best_models[-1]
            break
    
    if best_model:
        print(f"\n✓ 학습 완료!")
        print(f"  최고 모델: {best_model}")
    else:
        print("경고: best.pt 파일을 찾을 수 없습니다")
    
    return best_model

# ==================== 사용 예시 ====================

if __name__ == "__main__":
    # 1. 데이터 준비 (전처리 포함)
    source_directory = r"C:\Users\ben61\Bus-Onda\ai-vision\dataset\final"
    output_directory = r"C:\Users\ben61\Bus-Onda\ai-vision\dataset\processed"
    
    data_yaml_path = prepare_yolo_dataset(
        source_dir=source_directory,
        output_dir=output_directory,
        train_ratio=0.8  # 80% train, 20% val
    )
    
    # 2. 모델 학습
    best_model_path = train_yolo_model(
        data_yaml=data_yaml_path,
        model_size='n',  # nano 모델 (빠름, 가벼움)
        epochs=100,
        img_size=640,
        batch_size=16
    )
    
    # 3. 테스트 (선택사항)
    print("\n=== 테스트 이미지로 추론 ===")
    if best_model_path and os.path.exists(best_model_path):
        model = YOLO(best_model_path)
        
        # 테스트 이미지 경로
        test_image = "test_bus.jpg"
        if os.path.exists(test_image):
            results = model.predict(test_image, save=True, conf=0.5)
            print(f"결과 저장됨: runs/detect/predict/")