from ultralytics import YOLO
from pathlib import Path
import cv2

def preprocess_image(img_path):
    """이미지 전처리 (학습 시와 동일)"""
    img = cv2.imread(str(img_path))
    
    # 흑백 변환
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # CLAHE
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8,8))
    enhanced = clahe.apply(gray)
    
    return enhanced

def test_model():
    """학습된 모델로 테스트"""
    
    # 모델 로드
    model_path = Path(__file__).parent.parent / "runs" / "bus_number_model" / "weights" / "best.pt"
    model = YOLO(str(model_path))
    
    print(f"✅ 모델 로드: {model_path}")
    
    # 테스트 이미지 폴더 (원본 이미지)
    test_dir = Path(__file__).parent.parent / "test_images"
    
    if not test_dir.exists():
        print(f"❌ {test_dir} 폴더가 없습니다!")
        print(f"💡 test_images 폴더를 만들고 버스 사진을 넣어주세요.")
        return
    
    # 출력 폴더
    output_dir = Path(__file__).parent.parent / "test_results"
    output_dir.mkdir(exist_ok=True)
    
    # 전처리된 이미지 임시 저장 폴더
    temp_dir = output_dir / "preprocessed_temp"
    temp_dir.mkdir(exist_ok=True)
    
    # 테스트 이미지 목록
    test_images = list(test_dir.glob("*.jpg")) + \
                  list(test_dir.glob("*.png")) + \
                  list(test_dir.glob("*.webp"))
    
    if not test_images:
        print(f"❌ {test_dir}에 이미지가 없습니다!")
        return
    
    print(f"\n📸 총 {len(test_images)}장 테스트 시작!\n")
    
    for i, img_path in enumerate(test_images):
        print(f"🔍 테스트 {i+1}/{len(test_images)}: {img_path.name}")
        
        # 전처리
        preprocessed = preprocess_image(img_path)
        temp_path = temp_dir / img_path.name
        cv2.imwrite(str(temp_path), preprocessed)
        
        # 추론
        results = model(str(temp_path))
        
        # 결과 출력
        detected = False
        for r in results:
            boxes = r.boxes
            for box in boxes:
                cls = int(box.cls[0])
                conf = float(box.conf[0])
                class_name = model.names[cls]
                print(f"  ✅ {class_name}: {conf:.2%}")
                detected = True
        
        if not detected:
            print(f"  ⚠️  감지된 객체 없음")
        
        # 결과 이미지 저장 (원본에 박스 그린 버전)
        results[0].save(str(output_dir / f"result_{img_path.name}"))
    
    print(f"\n🎉 테스트 완료!")
    print(f"📂 결과 저장: {output_dir}")
    print(f"💡 {output_dir} 폴더를 열어서 결과를 확인하세요!")

if __name__ == "__main__":
    test_model()