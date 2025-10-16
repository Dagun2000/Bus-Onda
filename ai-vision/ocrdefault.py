# easyocr_run.py  (경로만 고정 변경)
import os, re, argparse, glob
import easyocr
import cv2

# ★ 여기만 바꿨습니다
BASE_DIR = r"C:\Users\ben61\Bus-Onda\ai-vision\test_images2\gray_preview"

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", nargs="+", default=["ko","en"], help="언어 코드 목록 (예: ko en)")
    ap.add_argument("--gpu", action="store_true", help="GPU 사용")
    ap.add_argument("--digits_only", action="store_true", help="숫자만 후처리(버스번호 확인용)")
    ap.add_argument("--min_conf", type=float, default=0.3, help="최소 신뢰도")
    ap.add_argument("--save_vis", action="store_true", help="결과 시각화 이미지 저장(out 폴더)")
    args = ap.parse_args()

    # 지정 폴더의 이미지 수집
    exts = ("*.jpg","*.jpeg","*.png","*.bmp","*.webp")
    img_list = []
    for pat in exts:
        img_list.extend(glob.glob(os.path.join(BASE_DIR, pat)))
    img_list = sorted(img_list)

    if not img_list:
        print(f"[INFO] 이미지 없음: {BASE_DIR}")
        return

    print(f"[INFO] 폴더={BASE_DIR}, 이미지 {len(img_list)}장, 언어={args.lang}, GPU={args.gpu}")
    reader = easyocr.Reader(args.lang, gpu=args.gpu)

    if args.save_vis:
        os.makedirs("out", exist_ok=True)

    # 결과 TSV 저장
    tsv_path = "easyocr_results.tsv"
    with open(tsv_path, "w", encoding="utf-8") as f:
        f.write("filename\ttext\tconfidence\n")
        for img_path in img_list:
            result = reader.readtext(img_path, detail=1)  # [(bbox, text, conf), ...]
            lines = []
            for (bbox, text, conf) in result:
                if conf < args.min_conf:
                    continue
                orig = text
                if args.digits_only:
                    text = re.sub(r"[^0-9]", "", text)
                    if text == "":
                        continue
                lines.append((bbox, text, conf, orig))

            print(f"\n=== {os.path.basename(img_path)} ===")
            if not lines:
                print("(no text above min_conf)")
            for _, t, c, o in lines:
                shown = t if not args.digits_only else f"{t} (orig:{o})"
                print(f"{shown}\t(conf={c:.3f})")
                f.write(f"{os.path.basename(img_path)}\t{t}\t{c:.4f}\n")

            if args.save_vis:
                img = cv2.imread(img_path)
                if img is None:
                    continue
                # 간단 사각형 시각화
                for (bbox, text, conf, orig) in lines:
                    xs = [int(x) for x, y in bbox]
                    ys = [int(y) for x, y in bbox]
                    x1, y1, x2, y2 = min(xs), min(ys), max(xs), max(ys)
                    cv2.rectangle(img, (x1,y1), (x2,y2), (0,255,0), 2)
                    label = text if not args.digits_only else f"{text}({conf:.2f})"
                    cv2.putText(img, label, (x1, max(0,y1-5)), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0,255,0), 2)
                out_path = os.path.join("out", os.path.basename(img_path))
                cv2.imwrite(out_path, img)

    print(f"\n[DONE] 결과 TSV: {tsv_path}")
    if args.save_vis:
        print("[DONE] 시각화 이미지: .\\out\\*")

if __name__ == "__main__":
    main()
