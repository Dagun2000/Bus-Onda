import cv2
import pandas as pd
from pathlib import Path

def simple_ocr_labeling():
    """간단한 OCR 라벨링 도구"""
    
    csv_path = Path('ocr_dataset/labels_empty.csv')
    df = pd.read_csv(csv_path)
    
    current_idx = 0
    
    while current_idx < len(df):
        row = df.iloc[current_idx]
        
        # 이미지 로드
        img_path = Path('ocr_dataset') / row['image']
        img = cv2.imread(str(img_path))
        
        if img is None:
            current_idx += 1
            continue
        
        # 크게 보여주기
        h, w = img.shape[:2]
        scale = 800 / w
        display = cv2.resize(img, None, fx=scale, fy=scale)
        
        # 현재 텍스트 표시
        cv2.putText(display, f"{current_idx+1}/{len(df)}", (10, 30),
                   cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
        
        if pd.notna(row['text']) and row['text']:
            cv2.putText(display, f"Current: {row['text']}", (10, 70),
                       cv2.FONT_HERSHEY_SIMPLEX, 1, (0, 255, 0), 2)
        
        cv2.imshow('OCR Labeling', display)
        
        print(f"\n[{current_idx+1}/{len(df)}] {row['image']}")
        print(f"Class: {row['class']}")
        print(f"Current text: {row['text']}")
        
        key = cv2.waitKey(0) & 0xFF
        
        if key == ord('q'):  # 종료
            break
        elif key == ord('d'):  # 다음
            current_idx += 1
        elif key == ord('a'):  # 이전
            current_idx = max(0, current_idx - 1)
        elif key == ord('s'):  # 저장
            text = input("텍스트 입력: ").strip()
            df.at[current_idx, 'text'] = text
            df.to_csv(csv_path.parent / 'labels.csv', index=False)
            print(f"✅ 저장됨: {text}")
            current_idx += 1
        elif key == ord('x'):  # 스킵
            current_idx += 1
    
    cv2.destroyAllWindows()
    print(f"\n✅ 라벨링 완료!")
    print(f"📁 저장: {csv_path.parent / 'labels.csv'}")

if __name__ == "__main__":
    simple_ocr_labeling()