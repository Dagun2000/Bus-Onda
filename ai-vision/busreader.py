import os
import cv2
import numpy as np
import torch
from pathlib import Path
from ultralytics import YOLO
import string

# EasyOCR 모델 import (deep-text-recognition-benchmark 구조)
import sys
sys.path.append(r'C:\Users\ben61\Bus-Onda\ai-vision\my_ocr_project\deep-text-recognition-benchmark')
from model import Model
from utils import AttnLabelConverter

class TwoStageRecognizer:
    """2단계 버스 번호 인식기: 버스 검출 → 번호판 검출 → 텍스트 인식"""
    
    def __init__(self, bus_yolo_model, number_yolo_model, ocr_model_path, ocr_opt):
        """
        Args:
            bus_yolo_model: 버스 검출용 YOLO 모델 (사전학습된 일반 YOLO)
            number_yolo_model: 번호판 검출용 YOLO 모델 (커스텀 학습 모델)
            ocr_model_path: EasyOCR 모델 경로
            ocr_opt: EasyOCR 모델 설정
        """
        # 1단계: 버스 검출 YOLO (사전학습 모델)
        print("1단계 YOLO 모델 로딩 중 (버스 검출)...")
        if isinstance(bus_yolo_model, str):
            self.bus_detector = YOLO(bus_yolo_model)
        else:
            self.bus_detector = bus_yolo_model
        print("✓ 버스 검출 모델 로드 완료")
        
        # 2단계: 번호판 검출 YOLO (커스텀 학습 모델)
        print("2단계 YOLO 모델 로딩 중 (번호판 검출)...")
        if isinstance(number_yolo_model, str):
            self.number_detector = YOLO(number_yolo_model)
        else:
            self.number_detector = number_yolo_model
        print("✓ 번호판 검출 모델 로드 완료")
        
        # 3단계: OCR 모델
        print("EasyOCR 모델 로딩 중...")
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        
        # Converter 설정
        self.converter = AttnLabelConverter(ocr_opt.character)
        ocr_opt.num_class = len(self.converter.character)
        
        # 모델 생성 및 로드
        self.ocr_model = Model(ocr_opt)
        self.ocr_model = torch.nn.DataParallel(self.ocr_model).to(self.device)
        self.ocr_model.load_state_dict(torch.load(ocr_model_path, map_location=self.device))
        self.ocr_model.eval()
        print("✓ EasyOCR 모델 로드 완료\n")
        
        self.ocr_opt = ocr_opt
    
    def preprocess_image(self, img):
        """
        전처리: 그레이스케일 + CLAHE
        """
        if len(img.shape) == 3:
            gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        else:
            gray = img.copy()
        
        clahe = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8,8))
        enhanced = clahe.apply(gray)
        
        return enhanced
    
    def preprocess_for_ocr(self, img):
        """
        EasyOCR 입력용 전처리
        """
        h, w = img.shape
        ratio = w / float(h)
        
        if self.ocr_opt.PAD:
            if ratio > self.ocr_opt.imgW / self.ocr_opt.imgH:
                resized_w = self.ocr_opt.imgW
                resized_h = int(self.ocr_opt.imgW / ratio)
            else:
                resized_h = self.ocr_opt.imgH
                resized_w = int(self.ocr_opt.imgH * ratio)
            
            resized_img = cv2.resize(img, (resized_w, resized_h))
            canvas = np.zeros((self.ocr_opt.imgH, self.ocr_opt.imgW), dtype=np.uint8)
            canvas[:resized_h, :resized_w] = resized_img
            img = canvas
        else:
            img = cv2.resize(img, (self.ocr_opt.imgW, self.ocr_opt.imgH))
        
        img = img.astype(np.float32) / 255.0
        img = torch.from_numpy(img).unsqueeze(0).unsqueeze(0)
        
        return img
    
    def recognize_text(self, img_tensor):
        """
        EasyOCR로 텍스트 인식
        """
        with torch.no_grad():
            img_tensor = img_tensor.to(self.device)
            
            if 'CTC' in self.ocr_opt.Prediction:
                preds = self.ocr_model(img_tensor, None)
                preds_size = torch.IntTensor([preds.size(1)])
                _, preds_index = preds.max(2)
                preds_str = self.converter.decode(preds_index, preds_size)
            else:  # Attn
                length_for_pred = torch.IntTensor([self.ocr_opt.batch_max_length])
                text_for_pred = torch.LongTensor(1, self.ocr_opt.batch_max_length + 1).fill_(0).to(self.device)
                
                preds = self.ocr_model(img_tensor, text_for_pred, is_train=False)
                _, preds_index = preds.max(2)
                preds_str = self.converter.decode(preds_index, length_for_pred)
            
            if isinstance(preds_str, list):
                preds_str = preds_str[0]
            
            if '[s]' in preds_str:
                preds_str = preds_str[:preds_str.find('[s]')]
            
            return preds_str
    
    def predict(self, image_path, bus_conf=0.5, number_conf=0.1, visualize=True):
        """
        2단계 버스 번호 인식 파이프라인
        
        Args:
            image_path: 입력 이미지 경로
            bus_conf: 버스 검출 신뢰도 임계값
            number_conf: 번호판 검출 신뢰도 임계값
            visualize: 결과 시각화 여부
            
        Returns:
            results: [{'bus_box': [...], 'number_box': [...], 'text': '1002', ...}, ...]
        """
        # 이미지 읽기
        original_img = cv2.imread(str(image_path))
        if original_img is None:
            raise ValueError(f"이미지를 읽을 수 없습니다: {image_path}")
        
        print(f"\n{'='*60}")
        print(f"이미지 처리 중: {Path(image_path).name}")
        print(f"{'='*60}")
        
        # ===== 1단계: 버스 검출 =====
        print("\n[1단계] 사전학습 YOLO로 버스 검출 중...")
        bus_results = self.bus_detector.predict(
            original_img,
            conf=bus_conf,
            classes=[5],  # COCO dataset에서 버스는 class 5
            verbose=False
        )
        
        bus_boxes = bus_results[0].boxes
        print(f"  → {len(bus_boxes)} 개의 버스 검출됨")
        
        if len(bus_boxes) == 0:
            print("  ⚠️ 버스가 검출되지 않았습니다!")
            return []
        
        all_results = []
        
        # 각 버스마다 처리
        for bus_idx, bus_box in enumerate(bus_boxes):
            bus_x1, bus_y1, bus_x2, bus_y2 = bus_box.xyxy[0].cpu().numpy().astype(int)
            bus_conf_score = float(bus_box.conf[0])
            
            print(f"\n  버스 #{bus_idx + 1}")
            print(f"    위치: [{bus_x1}, {bus_y1}, {bus_x2}, {bus_y2}]")
            print(f"    신뢰도: {bus_conf_score:.3f}")
            
            # 버스 영역 크롭
            bus_cropped = original_img[bus_y1:bus_y2, bus_x1:bus_x2]
            
            if bus_cropped.size == 0:
                print("    ⚠️ 크롭 실패 (빈 이미지)")
                continue
            
            # ===== 2단계: 크롭된 버스 이미지에서 번호판 검출 =====
            print(f"\n[2단계] 버스 #{bus_idx + 1}에서 번호판 검출 중...")
            
            # 전처리 적용
            preprocessed_bus = self.preprocess_image(bus_cropped)
            preprocessed_bus_rgb = cv2.cvtColor(preprocessed_bus, cv2.COLOR_GRAY2BGR)
            
            # 번호판 검출
            number_results = self.number_detector.predict(
                preprocessed_bus_rgb,
                conf=number_conf,
                verbose=False
            )
            
            number_boxes = number_results[0].boxes
            print(f"  → {len(number_boxes)} 개의 번호판 검출됨")
            
            # 신뢰도가 가장 높은 것만 선택 (여러 개 검출 시)
            if len(number_boxes) > 1:
                number_boxes = [max(number_boxes, key=lambda b: b.conf[0])]
                print(f"  → 신뢰도 최고값만 선택: {number_boxes[0].conf[0]:.3f}")
            
            # ===== 3단계: 각 번호판에 대해 OCR 수행 =====
            for num_idx, num_box in enumerate(number_boxes):
                # 번호판 상대 좌표
                rel_x1, rel_y1, rel_x2, rel_y2 = num_box.xyxy[0].cpu().numpy().astype(int)
                num_conf_score = float(num_box.conf[0])
                
                # 원본 이미지 기준 절대 좌표
                abs_x1 = bus_x1 + rel_x1
                abs_y1 = bus_y1 + rel_y1
                abs_x2 = bus_x1 + rel_x2
                abs_y2 = bus_y1 + rel_y2
                
                print(f"\n  번호판 #{num_idx + 1}")
                print(f"    상대 위치: [{rel_x1}, {rel_y1}, {rel_x2}, {rel_y2}]")
                print(f"    절대 위치: [{abs_x1}, {abs_y1}, {abs_x2}, {abs_y2}]")
                print(f"    신뢰도: {num_conf_score:.3f}")
                
                # 번호판 크롭 (전처리된 이미지에서)
                number_cropped = preprocessed_bus[rel_y1:rel_y2, rel_x1:rel_x2]
                
                if number_cropped.size == 0:
                    print("    ⚠️ 번호판 크롭 실패")
                    continue
                
                # OCR 전처리 및 인식
                ocr_input = self.preprocess_for_ocr(number_cropped)
                recognized_text = self.recognize_text(ocr_input)
                
                print(f"    ✓ 인식 결과: '{recognized_text}'")
                
                all_results.append({
                    'bus_box': [bus_x1, bus_y1, bus_x2, bus_y2],
                    'bus_confidence': bus_conf_score,
                    'number_box': [abs_x1, abs_y1, abs_x2, abs_y2],
                    'number_confidence': num_conf_score,
                    'text': recognized_text
                })
        
        # ===== 시각화 =====
        if visualize:
            vis_img = original_img.copy()
            
            if len(all_results) > 0:
                for res in all_results:
                    # 버스 박스 (파란색)
                    bus_x1, bus_y1, bus_x2, bus_y2 = res['bus_box']
                    cv2.rectangle(vis_img, (bus_x1, bus_y1), (bus_x2, bus_y2), (255, 0, 0), 2)
                    cv2.putText(vis_img, "BUS", (bus_x1, bus_y1 - 10),
                               cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 0, 0), 2)
                    
                    # 번호판 박스 (초록색)
                    num_x1, num_y1, num_x2, num_y2 = res['number_box']
                    cv2.rectangle(vis_img, (num_x1, num_y1), (num_x2, num_y2), (0, 255, 0), 3)
                    
                    # 인식된 텍스트
                    text = res['text']
                    display_text = f"{text}"
                    
                    font = cv2.FONT_HERSHEY_DUPLEX
                    font_scale = 1.2
                    font_thickness = 2
                    (text_w, text_h), baseline = cv2.getTextSize(
                        display_text, font, font_scale, font_thickness
                    )
                    
                    # 텍스트 위치 (번호판 위)
                    text_y = num_y1 - 10
                    if text_y - text_h - 10 < 0:
                        text_y = num_y2 + text_h + 10
                    
                    # 배경
                    cv2.rectangle(
                        vis_img,
                        (num_x1, text_y - text_h - 8),
                        (num_x1 + text_w + 10, text_y + 5),
                        (0, 255, 0),
                        -1
                    )
                    
                    # 텍스트
                    cv2.putText(
                        vis_img,
                        display_text,
                        (num_x1 + 5, text_y - 5),
                        font,
                        font_scale,
                        (0, 0, 0),
                        font_thickness
                    )
            else:
                # 검출 실패
                cv2.putText(
                    vis_img,
                    "No bus number detected",
                    (10, 40),
                    cv2.FONT_HERSHEY_DUPLEX,
                    1.0,
                    (0, 0, 255),
                    2
                )
            
            # 저장
            output_dir = Path('output_results')
            output_dir.mkdir(exist_ok=True)
            output_path = output_dir / f"result_{Path(image_path).name}"
            cv2.imwrite(str(output_path), vis_img)
            print(f"\n✓ 시각화 결과 저장됨: {output_path}")
        
        return all_results


# ==================== 사용 예시 ====================

if __name__ == "__main__":
    # EasyOCR 모델 설정
    class OCROptions:
        def __init__(self):
            self.Transformation = 'TPS'
            self.FeatureExtraction = 'ResNet'
            self.SequenceModeling = 'BiLSTM'
            self.Prediction = 'Attn'
            
            self.imgH = 32
            self.imgW = 100
            self.rgb = False
            self.input_channel = 1
            
            # 학습 시와 동일 (맨 앞에 공백 포함!)
            self.character = ' 0123456789AN대도동등로마봉북서성송양영자작전종천초파포'
            
            self.batch_max_length = 25
            self.num_fiducial = 20
            self.output_channel = 512
            self.hidden_size = 256
            self.PAD = True
    
    ocr_opt = OCROptions()
    
    # 모델 경로
    BUS_YOLO = 'yolov8n.pt'  # 사전학습된 일반 YOLO (버스 검출용)
    NUMBER_YOLO = r"C:\Users\ben61\Bus-Onda\ai-vision\runs\bus_number_detector\weights\best.pt"  # 커스텀 학습 모델 (번호판 검출용)
    OCR_MODEL = r"C:\Users\ben61\Bus-Onda\ai-vision\my_ocr_project\deep-text-recognition-benchmark\saved_models\TPS-ResNet-BiLSTM-Attn-Seed1111\best_accuracy.pth"
    
    # 인식기 초기화
    recognizer = TwoStageRecognizer(
        bus_yolo_model=BUS_YOLO,
        number_yolo_model=NUMBER_YOLO,
        ocr_model_path=OCR_MODEL,
        ocr_opt=ocr_opt
    )
    
    # 테스트 폴더
    test_folder = r"C:\Users\ben61\Bus-Onda\ai-vision\test_images"
    
    if os.path.exists(test_folder):
        image_files = list(Path(test_folder).glob('*.jpg')) + \
                      list(Path(test_folder).glob('*.png')) + \
                      list(Path(test_folder).glob('*.jpeg'))
        
        if len(image_files) == 0:
            print(f"테스트 폴더에 이미지가 없습니다: {test_folder}")
        else:
            print(f"\n총 {len(image_files)}개의 이미지를 처리합니다.\n")
            
            for img_file in image_files:
                try:
                    results = recognizer.predict(
                        image_path=str(img_file),
                        bus_conf=0.5,  # 버스 검출 신뢰도
                        number_conf=0.05,  # 번호판 검출 신뢰도 (매우 낮춤)
                        visualize=True
                    )
                    
                    print(f"\n{'='*60}")
                    print(f"파일: {img_file.name}")
                    print("최종 결과:")
                    print(f"{'='*60}")
                    if results:
                        for i, res in enumerate(results, 1):
                            print(f"  {i}. 버스 번호: {res['text']}")
                            print(f"     (버스 신뢰도: {res['bus_confidence']:.3f}, 번호판 신뢰도: {res['number_confidence']:.3f})")
                    else:
                        print("  검출된 버스 번호 없음")
                    print()
                    
                except Exception as e:
                    print(f"❌ {img_file.name} 처리 실패: {str(e)}\n")
                    import traceback
                    traceback.print_exc()
    else:
        print(f"테스트 폴더를 찾을 수 없습니다: {test_folder}")