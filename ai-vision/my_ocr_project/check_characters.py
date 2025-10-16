# check_characters.py
import os

def get_all_characters(gt_files):
    all_chars = set()
    
    for gt_file in gt_files:
        with open(gt_file, 'r', encoding='utf-8') as f:
            for line in f:
                parts = line.strip().split('\t')
                if len(parts) == 2:
                    label = parts[1]
                    all_chars.update(label)
    
    return sorted(all_chars)

if __name__ == '__main__':
    gt_files = [
        'data_for_easyocr/train/gt.txt',
        'data_for_easyocr/validation/gt.txt'
    ]
    
    characters = get_all_characters(gt_files)
    char_string = ''.join(characters)
    
    print(f"총 문자 종류: {len(characters)}개")
    print(f"문자 목록: {characters}")
    print(f"\n학습에 사용할 문자열:")
    print(f"'{char_string}'")