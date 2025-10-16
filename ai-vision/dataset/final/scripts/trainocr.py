import os
import shutil
from pathlib import Path
import random

def prepare_paddleocr_data():
    """
    PaddleOCR 학습을 위한 데이터 준비
    - Train/Val 분할 (80:20)
    - 문자 딕셔너리 생성
    """
    
    data_dir = Path(r"C:\Users\ben61\Bus-Onda\ai-vision\dataset\ocr_data")
    train_list_file = data_dir / 'train_list.txt'
    
    print("=== PaddleOCR 학습 데이터 준비 ===\n")
    
    # train_list.txt 읽기
    with open(train_list_file, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    print(f"총 {len(lines)}개 데이터")
    
    # 셔플
    random.seed(42)
    random.shuffle(lines)
    
    # Train/Val 분할 (80:20)
    split_idx = int(len(lines) * 0.8)
    train_lines = lines[:split_idx]
    val_lines = lines[split_idx:]
    
    print(f"Train: {len(train_lines)}개")
    print(f"Val: {len(val_lines)}개")
    
    # 저장
    train_file = data_dir / 'train_list.txt'
    val_file = data_dir / 'val_list.txt'
    
    with open(train_file, 'w', encoding='utf-8') as f:
        f.writelines(train_lines)
    
    with open(val_file, 'w', encoding='utf-8') as f:
        f.writelines(val_lines)
    
    # 문자 딕셔너리 생성
    all_chars = set()
    for line in lines:
        text = line.split('\t')[1].strip()
        all_chars.update(text)
    
    # 정렬
    chars = sorted(list(all_chars))
    
    # 딕셔너리 파일 생성
    dict_file = data_dir / 'bus_number_dict.txt'
    with open(dict_file, 'w', encoding='utf-8') as f:
        for char in chars:
            f.write(char + '\n')
    
    print(f"\n문자 종류: {len(chars)}개")
    print(f"문자: {chars}")
    
    print(f"\n✓ 준비 완료!")
    print(f"Train: {train_file}")
    print(f"Val: {val_file}")
    print(f"Dict: {dict_file}")
    
    return str(data_dir), str(dict_file)

def create_paddleocr_config(data_dir, dict_file):
    config_content = f"""Global:
  use_gpu: true
  epoch_num: 300
  log_smooth_window: 20
  print_batch_step: 10
  save_model_dir: C:/Users/ben61/Bus-Onda/ai-vision/paddleocr_models/bus_number
  save_epoch_step: 10
  eval_batch_step: 200
  cal_metric_during_train: true
  pretrained_model: C:/Users/ben61/Bus-Onda/ai-vision/PaddleOCR/pretrain_models/ch_PP-OCRv4_rec_train/student
  checkpoints: null
  use_visualdl: false
  infer_img: null
  character_dict_path: {dict_file.replace(chr(92), '/')}
  max_text_length: 10
  infer_mode: false
  use_space_char: false
  save_res_path: ./output/rec/predicts_bus_number.txt
  ignore_params: ['head.fc.weight', 'head.fc.bias']  # ← 이 줄 추가!

Optimizer:
  name: Adam
  beta1: 0.9
  beta2: 0.999
  lr:
    name: Cosine
    learning_rate: 0.0005  # pretrain 사용하므로 lr 낮춤
    warmup_epoch: 2
  regularizer:
    name: L2
    factor: 0.00001

Architecture:
  model_type: rec
  algorithm: SVTR_LCNet
  Transform:
  Backbone:
    name: MobileNetV1Enhance
    scale: 0.5
    last_conv_stride: [1, 2]
    last_pool_type: avg
  Head:
    name: CTCHead
    fc_decay: 0.00001

Loss:
  name: CTCLoss

PostProcess:
  name: CTCLabelDecode

Metric:
  name: RecMetric
  main_indicator: acc

Train:
  dataset:
    name: SimpleDataSet
    data_dir: {data_dir.replace(chr(92), '/')}
    label_file_list:
      - {data_dir.replace(chr(92), '/')}/train_list.txt
    transforms:
      - DecodeImage:
          img_mode: BGR
          channel_first: false
      - CTCLabelEncode:
      - RecResizeImg:
          image_shape: [3, 32, 320]
      - KeepKeys:
          keep_keys: ['image', 'label', 'length']
  loader:
    shuffle: true
    batch_size_per_card: 128
    drop_last: true
    num_workers: 4

Eval:
  dataset:
    name: SimpleDataSet
    data_dir: {data_dir.replace(chr(92), '/')}
    label_file_list:
      - {data_dir.replace(chr(92), '/')}/val_list.txt
    transforms:
      - DecodeImage:
          img_mode: BGR
          channel_first: false
      - CTCLabelEncode:
      - RecResizeImg:
          image_shape: [3, 32, 320]
      - KeepKeys:
          keep_keys: ['image', 'label', 'length']
  loader:
    shuffle: false
    batch_size_per_card: 128
    drop_last: false
    num_workers: 4
"""
    
    with open(config_path, 'w', encoding='utf-8') as f:
        f.write(config_content)
    
    print(f"\n✓ 설정 파일 생성 완료: {config_path}")
    
    return str(config_path)

def download_pretrained_model():
    """
    PaddleOCR pretrained 모델 다운로드 안내
    """
    print("\n=== Pretrained 모델 다운로드 ===")
    print("다음 명령어로 pretrained 모델을 다운로드하세요:")
    print("\nwget https://paddleocr.bj.bcebos.com/PP-OCRv3/chinese/ch_PP-OCRv3_rec_train.tar")
    print("tar -xf ch_PP-OCRv3_rec_train.tar")
    print("\n또는 PaddleOCR GitHub에서 다운로드:")
    print("https://github.com/PaddlePaddle/PaddleOCR/blob/main/doc/doc_en/models_list_en.md")

def train_paddleocr(config_path):
    """
    PaddleOCR 학습 시작
    """
    print("\n=== PaddleOCR 학습 시작 ===")
    print("다음 명령어로 학습을 시작하세요:\n")
    print(f"python -m paddle.distributed.launch --gpus '0' tools/train.py -c {config_path}")
    print("\nPaddleOCR 저장소가 필요합니다:")
    print("git clone https://github.com/PaddlePaddle/PaddleOCR.git")
    print("cd PaddleOCR")
    print("pip install -r requirements.txt")

# 실행
if __name__ == "__main__":
    # 1. 데이터 준비
    data_dir, dict_file = prepare_paddleocr_data()
    
    # 2. 설정 파일 생성
    config_path = create_paddleocr_config(data_dir, dict_file)
    
    # 3. 학습 안내
    download_pretrained_model()
    train_paddleocr(config_path)