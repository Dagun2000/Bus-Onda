import cv2
from ultralytics import YOLO
import easyocr
import re
import numpy as np
from collections import defaultdict

def extract_bus_number(text):
    """버스 번호 패턴 추출"""
    text = text.replace(' ', '').replace('번', '')
    
    patterns = [
        r'[가-힣]+\d{1,4}[A-Z]?',
        r'\d{1,4}[A-Z]?',
        r'[A-Z]\d{3,4}',
    ]
    
    for pattern in patterns:
        match = re.search(pattern, text)
        if match:
            number = match.group()
            if 2 <= len(number) <= 10:
                return number
    return None


def find_bus_number_visual(image_path):
    print("=" * 50)
    print("버스 번호 인식 (시각화)")
    print("=" * 50)
    
    reader = easyocr.Reader(['ko', 'en'], gpu=True)
    yolo = YOLO('yolov8n.pt')
    
    image = cv2.imread(image_path)
    h_img, w_img = image.shape[:2]
    center_x, center_y = w_img // 2, h_img // 2
    
    # 버스 찾기
    print("\n버스 감지 중...")
    results = yolo.predict(image, device='cpu', verbose=False)
    
    closest_bus = None
    min_dist = float('inf')
    
    for result in results:
        for box in result.boxes:
            if int(box.cls[0]) in [2, 5, 7]:
                x1, y1, x2, y2 = map(int, box.xyxy[0])
                cx, cy = (x1+x2)//2, (y1+y2)//2
                dist = ((cx-center_x)**2 + (cy-center_y)**2)**0.5
                
                if dist < min_dist:
                    min_dist = dist
                    closest_bus = (x1, y1, x2, y2)
    
    if closest_bus is None:
        print("버스를 찾지 못했습니다.")
        return None
    
    x1, y1, x2, y2 = closest_bus
    bus_crop = image[y1:y2, x1:x2]
    
    # 전체 OCR
    print("\nOCR 수행 중...")
    gray = cv2.cvtColor(bus_crop, cv2.COLOR_BGR2GRAY)
    ocr_results = reader.readtext(gray)
    
    # 원본 이미지에 시각화
    result_image = image.copy()
    
    # 버스 박스
    cv2.rectangle(result_image, (x1, y1), (x2, y2), (255, 0, 0), 3)
    
    print("\n인식된 텍스트:")
    bus_numbers = []
    
    for (bbox, text, prob) in ocr_results:
        # bbox를 numpy 배열로 변환
        box_points = np.array([[int(x1 + p[0]), int(y1 + p[1])] for p in bbox], dtype=np.int32)
        
        # 텍스트 영역 그리기
        cv2.polylines(result_image, [box_points], True, (0, 255, 0), 2)
        
        # 텍스트와 정확도 표시
        text_x = int(x1 + bbox[0][0])
        text_y = int(y1 + bbox[0][1]) - 10
        
        display_text = f"{text} ({prob*100:.0f}%)"
        cv2.putText(result_image, display_text, (text_x, text_y),
                   cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
        
        print(f"  '{text}' ({prob*100:.1f}%)")
        
        # 번호 추출
        bus_num = extract_bus_number(text)
        
        if bus_num and prob > 0.3:
            bus_numbers.append({
                'number': bus_num,
                'confidence': prob,
                'bbox': bbox
            })
            
            # 버스 번호로 인식된 것은 파란색으로 강조
            cv2.polylines(result_image, [box_points], True, (255, 0, 0), 3)
    
    if not bus_numbers:
        print("\n버스 번호를 찾지 못했습니다.")
        cv2.imwrite('test_images/visual_result.jpg', result_image)
        return None
    
    # 통계
    number_stats = defaultdict(lambda: {'count': 0, 'conf_sum': 0, 'confs': []})
    
    for item in bus_numbers:
        num = item['number']
        conf = item['confidence']
        
        number_stats[num]['count'] += 1
        number_stats[num]['conf_sum'] += conf
        number_stats[num]['confs'].append(conf)
    
    # 점수
    print("\n후보 번호:")
    scores = {}
    
    for num, stats in number_stats.items():
        count = stats['count']
        avg_conf = stats['conf_sum'] / count
        score = count * avg_conf
        
        scores[num] = score
        print(f"  {num}: {count}번 출현, 평균 {avg_conf*100:.1f}%, 점수 {score:.2f}")
    
    # 최종
    best_num = max(scores, key=scores.get)
    best_score = scores[best_num]
    best_count = number_stats[best_num]['count']
    best_avg = number_stats[best_num]['conf_sum'] / best_count
    
    print(f"\n최종 버스 번호: {best_num}")
    print(f"  출현: {best_count}번")
    print(f"  평균 정확도: {best_avg*100:.1f}%")
    print(f"  최종 점수: {best_score:.2f}")
    
    # 최종 결과 표시
    cv2.putText(result_image, f"Bus Number: {best_num}", 
               (x1, y2+50), cv2.FONT_HERSHEY_SIMPLEX, 
               2, (0, 0, 255), 4)
    
    cv2.putText(result_image, f"Confidence: {best_avg*100:.1f}% ({best_count} detections)", 
               (x1, y2+100), cv2.FONT_HERSHEY_SIMPLEX, 
               1, (0, 0, 255), 2)
    
    # 저장
    cv2.imwrite('test_images/visual_result.jpg', result_image)
    print("\n결과 저장: test_images/visual_result.jpg")
    
    return best_num


# 실행
find_bus_number_visual('test_images/0.webp')