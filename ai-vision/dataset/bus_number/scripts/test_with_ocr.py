from ultralytics import YOLO
from pathlib import Path
import cv2
import numpy as np

# Google Vision 또는 Tesseract
try:
    from google.cloud import vision
    USE_GOOGLE = True
except:
    import pytesseract
    USE_GOOGLE = False
    pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

def preprocess_image(img_path):
    """기본 전처리 (YOLO용)"""
    img = cv2.imread(str(img_path))
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8,8))
    enhanced = clahe.apply(gray)
    return enhanced, img

def ocr_google_vision(cropped):
    """Google Vision API"""
    client = vision.ImageAnnotatorClient()
    _, encoded = cv2.imencode('.jpg', cropped)
    image = vision.Image(content=encoded.tobytes())
    response = client.text_detection(image=image)
    
    if response.text_annotations:
        return response.text_annotations[0].description.strip()
    return ""

def ocr_tesseract(cropped, is_number_only=False):
    """Tesseract OCR"""
    if is_number_only:
        config = r'--oem 3 --psm 7 digits'
        text = pytesseract.image_to_string(cropped, config=config)
    else:
        config = r'--oem 3 --psm 7'
        text = pytesseract.image_to_string(cropped, lang='kor+eng', config=config)
    
    return text.strip().replace('\n', '').replace(' ', '')

def test_model_with_ocr():
    """YOLO + 스마트 OCR"""
    
    model_path = Path(__file__).parent.parent / "runs" / "bus_number_model" / "weights" / "best.pt"
    model = YOLO(str(model_path))
    
    print(f"✅ YOLO 모델 로드")
    print(f"✅ OCR: {'Google Vision' if USE_GOOGLE else 'Tesseract'}")
    
    test_dir = Path(__file__).parent.parent / "test_images"
    output_dir = Path(__file__).parent.parent / "test_results_smart_ocr"
    output_dir.mkdir(exist_ok=True)
    
    test_images = list(test_dir.glob("*.jpg")) + \
                  list(test_dir.glob("*.png")) + \
                  list(test_dir.glob("*.webp"))
    
    if not test_images:
        print(f"❌ 이미지 없음")
        return
    
    print(f"\n📸 총 {len(test_images)}장 테스트\n" + "=" * 60)
    
    for i, img_path in enumerate(test_images):
        print(f"\n🔍 [{i+1}/{len(test_images)}] {img_path.name}")
        print("-" * 60)
        
        # 전처리 + 원본 둘 다 준비
        preprocessed, original = preprocess_image(img_path)
        preprocessed_3ch = cv2.cvtColor(preprocessed, cv2.COLOR_GRAY2BGR)
        
        # YOLO 추론 (전처리된 이미지로)
        results = model(preprocessed_3ch)
        
        # 결과 표시용 (원본 컬러 사용!)
        display_img = original.copy()
        
        for r in results:
            boxes = r.boxes
            
            if len(boxes) == 0:
                print("  ⚠️  감지 없음")
                continue
            
            for idx, box in enumerate(boxes):
                cls = int(box.cls[0])
                conf = float(box.conf[0])
                class_name = model.names[cls]
                x1, y1, x2, y2 = map(int, box.xyxy[0])
                
                print(f"\n  📦 박스 {idx+1}: {class_name} ({conf:.2%})")
                
                # Crop (원본에서!)
                padding = 10
                y1_crop = max(0, y1 - padding)
                y2_crop = min(original.shape[0], y2 + padding)
                x1_crop = max(0, x1 - padding)
                x2_crop = min(original.shape[1], x2 + padding)
                
                cropped_original = original[y1_crop:y2_crop, x1_crop:x2_crop]
                
                if cropped_original.size == 0:
                    continue
                
                # OCR
                try:
                    if USE_GOOGLE:
                        text = ocr_google_vision(cropped_original)
                    else:
                        is_number = (class_name == "bus_number")
                        text = ocr_tesseract(cropped_original, is_number)
                    
                    if text:
                        print(f"     ✅ OCR: '{text}'")
                        
                        color = (0, 255, 0) if class_name == "bus_number" else (255, 0, 255)
                        cv2.rectangle(display_img, (x1, y1), (x2, y2), color, 2)
                        
                        label = f"{class_name}: {text}"
                        (text_w, text_h), _ = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, 0.8, 2)
                        cv2.rectangle(display_img, (x1, y1-text_h-10), (x1+text_w, y1), color, -1)
                        cv2.putText(display_img, label, (x1, y1-5), 
                                  cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255,255,255), 2)
                    else:
                        print(f"     ⚠️  OCR 결과 없음")
                        color = (0, 255, 0) if class_name == "bus_number" else (255, 0, 255)
                        cv2.rectangle(display_img, (x1, y1), (x2, y2), color, 2)
                        
                except Exception as e:
                    print(f"     ❌ OCR 오류: {e}")
                    color = (0, 255, 0) if class_name == "bus_number" else (255, 0, 255)
                    cv2.rectangle(display_img, (x1, y1), (x2, y2), color, 2)
        
        output_path = output_dir / f"smart_{img_path.name}"
        cv2.imwrite(str(output_path), display_img)
        print(f"\n  💾 저장: {output_path.name}")
    
    print("\n" + "=" * 60 + f"\n🎉 완료!\n📂 {output_dir}")

if __name__ == "__main__":
    test_model_with_ocr()