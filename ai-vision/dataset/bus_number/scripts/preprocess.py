import cv2
import os
from pathlib import Path

def preprocess_image(input_path, output_path):
    """흑백 변환 + CLAHE 대비 증강"""
    # 이미지 읽기
    img = cv2.imread(str(input_path))
    
    if img is None:
        print(f"❌ Error: {input_path}")
        return False
    
    # 1. 흑백 변환
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # 2. CLAHE (Contrast Limited Adaptive Histogram Equalization)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8,8))
    enhanced = clahe.apply(gray)
    
    # 3. 저장
    cv2.imwrite(str(output_path), enhanced)
    return True

def main():
    # 경로 설정 (현재 위치: bus_number/scripts/)
    current_dir = Path(__file__).parent
    raw_dir = current_dir.parent / "raw_images"
    output_dir = current_dir.parent / "preprocessed"
    
    # 출력 폴더 생성
    output_dir.mkdir(exist_ok=True)
    
    # 지원 확장자
    extensions = ['.jpg', '.jpeg', '.png', '.bmp', '.webp']
    
    # 모든 이미지 처리
    count = 0
    for img_path in raw_dir.iterdir():
        if img_path.suffix.lower() in extensions:
            output_path = output_dir / img_path.name
            if preprocess_image(img_path, output_path):
                count += 1
                print(f"✅ {count}. {img_path.name}")
    
    print(f"\n🎉 완료! 총 {count}개 이미지 처리됨")
    print(f"📂 저장 위치: {output_dir}")

if __name__ == "__main__":
    main()