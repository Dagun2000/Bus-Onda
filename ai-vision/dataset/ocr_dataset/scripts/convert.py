import json
import pandas as pd
from pathlib import Path
import cv2

def convert_labelstudio_json(json_path, raw_images_dir, output_dir):
    """Label Studio JSON → OCR 데이터셋"""
    
    json_path = Path(json_path)
    raw_images_dir = Path(raw_images_dir)
    output_dir = Path(output_dir)
    
    # JSON 로드
    print(f"📄 JSON 로드: {json_path}")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    print(f"✅ 총 {len(data)}개 항목\n")
    
    # 출력 폴더
    images_dir = output_dir / 'images'
    images_dir.mkdir(parents=True, exist_ok=True)
    
    dataset = []
    count = 0
    errors = []
    
    print(f"🔄 변환 시작...\n")
    
    for idx, item in enumerate(data):
        # 파일명
        filename = Path(item['file_upload']).name
        image_path = raw_images_dir / filename
        
        print(f"[{idx+1}/{len(data)}] {filename[:40]}...", end=" ")
        
        if not image_path.exists():
            print("❌ 파일 없음")
            errors.append(f"파일 없음: {filename}")
            continue
        
        # 이미지 로드
        img = cv2.imread(str(image_path))
        if img is None:
            print("❌ 로드 실패")
            errors.append(f"로드 실패: {filename}")
            continue
        
        h, w = img.shape[:2]
        
        # 어노테이션
        if 'annotations' not in item or len(item['annotations']) == 0:
            print("⚠️  라벨 없음")
            continue
        
        annotation = item['annotations'][0]
        
        if 'result' not in annotation:
            print("⚠️  result 없음")
            continue
        
        results = annotation['result']
        
        # id별로 그룹화
        groups = {}
        for r in results:
            r_id = r['id']
            if r_id not in groups:
                groups[r_id] = {
                    'rectangle': None,
                    'labels': None,
                    'textarea': None
                }
            groups[r_id][r['type']] = r
        
        # 각 그룹 처리
        saved = 0
        for group_id, group in groups.items():
            rect = group.get('rectangle')
            label_obj = group.get('labels')
            text_obj = group.get('textarea')
            
            # 세 가지 모두 있어야 함
            if not rect or not label_obj or not text_obj:
                continue
            
            # 라벨과 텍스트 추출
            label = label_obj['value']['labels'][0]
            text = text_obj['value']['text'][0].strip()
            
            if not text:
                continue
            
            # 좌표 변환
            rect_value = rect['value']
            x = rect_value['x']
            y = rect_value['y']
            width = rect_value['width']
            height = rect_value['height']
            
            x1 = int(x * w / 100)
            y1 = int(y * h / 100)
            x2 = int((x + width) * w / 100)
            y2 = int((y + height) * h / 100)
            
            x1, y1 = max(0, x1), max(0, y1)
            x2, y2 = min(w, x2), min(h, y2)
            
            # 크롭
            cropped = img[y1:y2, x1:x2]
            
            if cropped.size == 0:
                continue
            
            # 저장
            crop_filename = f"{count:04d}_{Path(filename).stem[:20]}_{label}.jpg"
            crop_path = images_dir / crop_filename
            
            cv2.imwrite(str(crop_path), cropped)
            
            dataset.append({
                'image': crop_filename,
                'text': text,
                'class': label,
                'source': filename
            })
            
            count += 1
            saved += 1
        
        print(f"✅ {saved}개")
    
    # CSV 저장
    print(f"\n{'='*60}")
    
    if len(dataset) > 0:
        df = pd.DataFrame(dataset)
        csv_path = output_dir / 'labels.csv'
        df.to_csv(csv_path, index=False, encoding='utf-8')
        
        print(f"\n🎉 변환 완료!")
        print(f"📊 크롭 이미지: {count}개")
        print(f"📂 저장 위치: {images_dir}")
        print(f"📝 라벨 CSV: {csv_path}")
        
        print(f"\n📊 클래스별:")
        print(df['class'].value_counts())
        
        print(f"\n📝 샘플:")
        print(df[['image', 'text', 'class']].head(20))
        
        return df
    else:
        print("\n❌ 변환된 데이터 없음!")
    
    if errors:
        print(f"\n⚠️  에러 {len(errors)}개:")
        for err in errors[:10]:
            print(f"  {err}")
    
    return None

if __name__ == "__main__":
    json_path = "../export.json"
    raw_images_dir = "../images"
    output_dir = "../processed"
    
    df = convert_labelstudio_json(json_path, raw_images_dir, output_dir)