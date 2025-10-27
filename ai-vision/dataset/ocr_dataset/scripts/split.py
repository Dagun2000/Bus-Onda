import pandas as pd
from pathlib import Path
from sklearn.model_selection import train_test_split

def split_dataset(csv_path):
    """Train/Val 분할"""
    
    csv_path = Path(csv_path)
    df = pd.read_csv(csv_path)
    
    print(f"📊 전체 데이터: {len(df)}개")
    print(f"\n클래스별:")
    print(df['class'].value_counts())
    
    # 80/20 분할
    train_df, val_df = train_test_split(df, test_size=0.2, random_state=42, stratify=df['class'])
    
    base_dir = csv_path.parent
    train_csv = base_dir / 'train.csv'
    val_csv = base_dir / 'val.csv'
    
    train_df.to_csv(train_csv, index=False, encoding='utf-8')
    val_df.to_csv(val_csv, index=False, encoding='utf-8')
    
    print(f"\n✅ Train: {len(train_df)}개")
    print(f"   - bus_number: {len(train_df[train_df['class']=='bus_number'])}개")
    print(f"   - license: {len(train_df[train_df['class']=='license'])}개")
    
    print(f"\n✅ Val: {len(val_df)}개")
    print(f"   - bus_number: {len(val_df[val_df['class']=='bus_number'])}개")
    print(f"   - license: {len(val_df[val_df['class']=='license'])}개")
    
    print(f"\n📁 저장:")
    print(f"   {train_csv}")
    print(f"   {val_csv}")
    
    return train_df, val_df

if __name__ == "__main__":
    # 상대 경로로 수정
    csv_path = "../processed/labels.csv"
    
    train_df, val_df = split_dataset(csv_path)