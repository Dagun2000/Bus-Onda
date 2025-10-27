from pathlib import Path

p = Path(r"C:\Users\ben61\Bus-Onda\ai-vision\my_ocr_project\deep-text-recognition-benchmark\saved_models\TPS-ResNet-BiLSTM-Attn-Seed1111\opt.txt")

data = p.read_bytes()
last_err = None
for enc in ("utf-8", "utf-8-sig", "cp949", "ms949", "euc-kr", "utf-16", "utf-16le", "utf-16be"):
    try:
        text = data.decode(enc)
        print(f"[OK] decoded with {enc}")
        break
    except Exception as e:
        last_err = e
else:
    raise last_err

line = next(x for x in text.splitlines() if x.startswith("character: "))
charset = line.split("character:",1)[1].lstrip()  # 콜론 뒤 공백 제거(실제 문자셋 선두 공백이 따로 있을 수 있으니 아래로 확인)
print("=== TRAIN CHARACTER ===")
print("|" + charset + "|")
print("length =", len(charset))
