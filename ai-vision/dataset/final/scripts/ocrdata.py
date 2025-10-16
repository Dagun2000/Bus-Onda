import json
import cv2
import numpy as np
from pathlib import Path

def preprocess_image(img):
    """YOLO와 동일한 전처리"""
    if len(img.shape) == 3:
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    else:
        gray = img.copy()
    
    clahe = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8,8))
    enhanced = clahe.apply(gray)
    
    return enhanced

def create_ocr_dataset():
    """Label Studio JSON으로 PaddleOCR 학습 데이터 생성"""
    
    # 경로 설정
    json_path = r"C:\Users\ben61\Bus-Onda\ai-vision\dataset\final\ocr\ocr.json"
    images_dir = r"C:\Users\ben61\Bus-Onda\ai-vision\dataset\final\ocr\images"
    output_dir = r"C:\Users\ben61\Bus-Onda\ai-vision\dataset\ocr_data"
    
    print("=== PaddleOCR 데이터 생성 시작 ===\n")
    
    # JSON 읽기
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print(f"총 {len(data)}개 항목 발견")
    
    # 출력 폴더 생성
    output_path = Path(output_dir)
    crop_dir = output_path / 'crop_images'
    crop_dir.mkdir(parents=True, exist_ok=True)
    
    train_list = []
    crop_count = 0
    
    # 각 항목 처리
    for idx, item in enumerate(data):
        # 이미지 파일명
        img_file = item['data']['image']
        img_stem = Path(img_file).stem
        
        # 이미지 찾기 (webp, jpg, png)
        img_path = None
        for ext in ['.webp', '.jpg', '.png']:
            test = Path(images_dir) / (img_stem + ext)
            if test.exists():
                img_path = test
                break
        
        if not img_path:
            continue
        
        # 이미지 읽기
        img = cv2.imread(str(img_path))
        if img is None:
            continue
        
        h, w = img.shape[:2]
        
        # annotation
        if not item.get('annotations'):
            continue
        
        results = item['annotations'][0]['result']
        
        # id별로 그룹화 (rectangle, labels, textarea가 같은 id를 가짐)
        grouped = {}
        for r in results:
            rid = r.get('id')
            if rid not in grouped:
                grouped[rid] = {}
            grouped[rid][r['type']] = r
        
        # 각 그룹 처리
        for rid, group in grouped.items():
            # rectangle bbox 추출
            if 'rectangle' not in group and 'labels' not in group:
                continue
            
            # bbox는 rectangle 또는 labels에서 가져옴
            rect = group.get('rectangle') or group.get('labels')
            if not rect:
                continue
            
            v = rect['value']
            x = int(v['x'] * w / 100)
            y = int(v['y'] * h / 100)
            bw = int(v['width'] * w / 100)
            bh = int(v['height'] * h / 100)
            
            # 텍스트 추출
            if 'textarea' not in group:
                continue
            
            text = group['textarea']['value']['text'][0]
            if not text:
                continue
            
            # crop
            crop = img[y:y+bh, x:x+bw]
            
            if crop.size == 0:
                continue
            
            # 전처리
            processed = preprocess_image(crop)
            
            # 저장
            save_name = f"crop_{crop_count:05d}.jpg"
            save_path = crop_dir / save_name
            cv2.imwrite(str(save_path), processed)
            
            train_list.append(f"{save_path}\t{text}\n")
            crop_count += 1
        
        if (idx + 1) % 50 == 0:
            print(f"{idx + 1}/{len(data)} 처리 완료 - crop {crop_count}개")
    
    # train_list.txt 저장
    list_file = output_path / 'train_list.txt'
    with open(list_file, 'w', encoding='utf-8') as f:
        f.writelines(train_list)
    
    print(f"\n✓ 완료!")
    print(f"Crop 이미지: {crop_count}개")
    print(f"저장 위치: {crop_dir}")
    print(f"라벨 파일: {list_file}")

if __name__ == "__main__":
    create_ocr_dataset()