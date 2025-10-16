import os
import json
import cv2
import random
from tqdm import tqdm

def preprocess_led_display(img):
    """
    전광판 이미지를 전처리하는 함수 (YOLO 학습 시 사용한 코드)
    1. 그레이스케일 변환
    2. CLAHE를 이용한 약한 대비 향상
    """
    # 1. 그레이스케일 변환
    if len(img.shape) == 3:
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    else:
        gray = img.copy()
    
    # 2. 매우 약한 대비 향상 적용
    clahe = cv2.createCLAHE(clipLimit=1.5, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)
    
    return enhanced

def create_dataset(json_path, image_source_dir, output_dir='data', train_ratio=0.9):
    """
    JSON 주석 파일을 기반으로 EasyOCR 학습용 데이터셋을 생성합니다.

    :param json_path: Label Studio에서 내보낸 JSON 파일 경로
    :param image_source_dir: 원본 이미지가 저장된 디렉토리
    :param output_dir: 결과물이 저장될 최상위 디렉토리
    :param train_ratio: 전체 데이터 중 학습 데이터로 사용할 비율
    """
    
    # 1. 출력 디렉토리 생성
    train_dir = os.path.join(output_dir, 'train')
    validation_dir = os.path.join(output_dir, 'validation')
    os.makedirs(train_dir, exist_ok=True)
    os.makedirs(validation_dir, exist_ok=True)
    
    # 2. JSON 파일 로드
    try:
        with open(json_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
    except FileNotFoundError:
        print(f"오류: JSON 파일을 찾을 수 없습니다. 경로: {json_path}")
        return
    except json.JSONDecodeError:
        print(f"오류: JSON 파일 형식이 올바르지 않습니다.")
        return

    all_labeled_data = []
    crop_counter = 0

    print("JSON 파일을 파싱하고 이미지를 크롭 및 전처리하는 중...")
    # 3. 데이터 파싱 및 이미지 전처리
    for item in tqdm(data):
        try:
            image_filename = os.path.basename(item['data']['image'])
            image_path = os.path.join(image_source_dir, image_filename)
            
            if not os.path.exists(image_path):
                print(f"경고: 이미지를 찾을 수 없습니다. 건너뜁니다: {image_path}")
                continue

            img = cv2.imread(image_path)
            if img is None:
                print(f"경고: 이미지를 읽을 수 없습니다. 건너뜁니다: {image_path}")
                continue
                
            original_height, original_width, _ = img.shape

            # result 항목들을 id를 기준으로 그룹화
            results_by_id = {}
            for result in item['annotations'][0]['result']:
                result_id = result.get('id')
                if result_id not in results_by_id:
                    results_by_id[result_id] = {}
                results_by_id[result_id][result['type']] = result['value']

            # 그룹화된 데이터를 기반으로 크롭 및 라벨 생성
            for result_id, values in results_by_id.items():
                if 'rectangle' in values and 'textarea' in values:
                    bbox_data = values['rectangle']
                    text_data = values['textarea']['text'][0]

                    # Bounding box 좌표 계산 (퍼센트 -> 픽셀)
                    x = (bbox_data['x'] / 100) * original_width
                    y = (bbox_data['y'] / 100) * original_height
                    w = (bbox_data['width'] / 100) * original_width
                    h = (bbox_data['height'] / 100) * original_height

                    x1, y1 = int(x), int(y)
                    x2, y2 = int(x + w), int(y + h)

                    # 이미지 크롭
                    cropped_img = img[y1:y2, x1:x2]

                    if cropped_img.size == 0:
                        print(f"경고: 크롭된 이미지가 비어있습니다. 건너뜁니다: {image_filename}")
                        continue
                    
                    # 요청하신 전처리 함수 적용
                    processed_img = preprocess_led_display(cropped_img)
                    
                    # 파일명 및 라벨 저장
                    new_filename = f"crop_{crop_counter:06d}.png"
                    all_labeled_data.append((new_filename, processed_img, text_data))
                    crop_counter += 1

        except KeyError as e:
            print(f"경고: JSON 데이터 구조가 예상과 다릅니다. 누락된 키: {e}. 해당 항목을 건너뜁니다.")
        except Exception as e:
            print(f"처리 중 오류 발생: {image_filename}, 오류: {e}")

    # 4. 데이터 분할 (Train / Validation)
    random.shuffle(all_labeled_data)
    split_index = int(len(all_labeled_data) * train_ratio)
    train_data = all_labeled_data[:split_index]
    validation_data = all_labeled_data[split_index:]

    print(f"\n총 {len(all_labeled_data)}개의 크롭된 이미지 생성 완료.")
    print(f"학습 데이터: {len(train_data)}개, 검증 데이터: {len(validation_data)}개")

    # 5. 파일 저장
    def save_files(dataset, target_dir):
        label_file_path = os.path.join(target_dir, 'gt.txt')
        with open(label_file_path, 'w', encoding='utf-8') as f:
            for filename, proc_img, text in tqdm(dataset, desc=f"{os.path.basename(target_dir)} 데이터 저장 중"):
                img_path = os.path.join(target_dir, filename)
                cv2.imwrite(img_path, proc_img)
                f.write(f"{filename}\t{text}\n")

    save_files(train_data, train_dir)
    save_files(validation_data, validation_dir)
    
    print(f"\n데이터셋 생성이 완료되었습니다. 결과는 '{output_dir}' 폴더에 저장되었습니다.")


if __name__ == '__main__':
    # --- 설정 ---
    json_file = 'ocr.json'
    source_images_folder = 'images'
    output_folder = 'data_for_easyocr' # 결과물이 저장될 폴더 이름
    
    create_dataset(json_file, source_images_folder, output_folder)