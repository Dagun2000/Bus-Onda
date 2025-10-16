import shutil
import random
from pathlib import Path

def split_dataset(split_ratio=0.8):
    """train/val로 분할 (80/20)"""
    
    # 경로 설정
    current_dir = Path(__file__).parent
    base_dir = current_dir.parent
    images_dir = base_dir / "preprocessed"
    labels_dir = base_dir / "labels"
    output_dir = base_dir / "yolo_dataset"
    
    # 기존 폴더 삭제 (재실행 대비)
    if output_dir.exists():
        shutil.rmtree(output_dir)
    
    # 출력 폴더 생성
    for split in ['train', 'val']:
        (output_dir / split / 'images').mkdir(parents=True, exist_ok=True)
        (output_dir / split / 'labels').mkdir(parents=True, exist_ok=True)
    
    # 이미지 파일 목록 (라벨이 있는 것만)
    images = []
    for img_path in images_dir.glob('*'):
        if img_path.suffix.lower() in ['.jpg', '.jpeg', '.png', '.webp']:
            label_path = labels_dir / f"{img_path.stem}.txt"
            if label_path.exists():  # 라벨이 있는 것만
                images.append(img_path)
    
    if not images:
        print("❌ 라벨링된 이미지가 없습니다!")
        return
    
    print(f"📊 총 라벨링된 이미지: {len(images)}장")
    
    # 섞기
    random.seed(42)  # 재현 가능하도록
    random.shuffle(images)
    
    # 분할
    split_idx = int(len(images) * split_ratio)
    train_images = images[:split_idx]
    val_images = images[split_idx:]
    
    # 복사
    for img_list, split in [(train_images, 'train'), (val_images, 'val')]:
        for img_path in img_list:
            # 이미지 복사
            shutil.copy(img_path, output_dir / split / 'images' / img_path.name)
            
            # 라벨 복사
            label_path = labels_dir / f"{img_path.stem}.txt"
            shutil.copy(label_path, output_dir / split / 'labels' / f"{img_path.stem}.txt")
    
    print(f"\n✅ 분할 완료!")
    print(f"📁 Train: {len(train_images)}장")
    print(f"📁 Val: {len(val_images)}장")
    print(f"📂 저장 위치: {output_dir}")
    
    # classes.txt 복사
    classes_file = base_dir / "classes.txt"
    if classes_file.exists():
        shutil.copy(classes_file, output_dir / "classes.txt")
        print(f"✅ classes.txt 복사 완료")
    else:
        print(f"⚠️  classes.txt 파일이 없습니다. 수동으로 만들어주세요!")

if __name__ == "__main__":
    split_dataset()