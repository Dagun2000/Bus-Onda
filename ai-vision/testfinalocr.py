# testfinalocr_tta.py
import os, sys
from types import SimpleNamespace
import torch, torch.nn.functional as F
import numpy as np
from PIL import Image
import cv2
from collections import defaultdict

# ===== 경로 설정 =====
DTB = r"C:\Users\ben61\Bus-Onda\ai-vision\my_ocr_project\deep-text-recognition-benchmark"
IMAGE_DIR = r"C:\Users\ben61\Bus-Onda\ai-vision\test_images2\gray_preview"
CKPT = r"C:\Users\ben61\Bus-Onda\ai-vision\saved_models\BusDigits-TPSResNet-CTC\best_accuracy.pth"
# =====================

sys.path.insert(0, DTB)
from dataset import AlignCollate
from utils import CTCLabelConverter
from model import Model

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

# 모델 옵션 (학습과 동일)
opt = SimpleNamespace(
    Transformation='TPS', FeatureExtraction='ResNet',
    SequenceModeling='BiLSTM', Prediction='CTC',
    num_fiducial=20, input_channel=1, output_channel=512, hidden_size=256,
    imgH=32, imgW=120, PAD=True,
    character="0123456789", rgb=False, batch_max_length=5,
)
converter = CTCLabelConverter(opt.character)
opt.num_class = len(converter.character)

# 모델 로드
model = Model(opt)
model = torch.nn.DataParallel(model).to(device)
ckpt = torch.load(CKPT, map_location='cpu')
model.load_state_dict(ckpt, strict=False)
model.eval()

# ---------- TTA 설정 (적당히 가벼움) ----------
ROT_DEGS  = [0.0, -2.5, -1.5, +1.5, +2.5]
IMGW_LIST = [100, 120, 140]
# -------------------------------------------
    
def pil_to_cv_gray(pil_img):
    if pil_img.mode != 'L':
        pil_img = pil_img.convert('L')
    return np.array(pil_img)

def slight_rotate(img, deg):
    h, w = img.shape[:2]
    M = cv2.getRotationMatrix2D((w/2, h/2), deg, 1.0)
    return cv2.warpAffine(img, M, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)

def postprocess_digits(s: str):
    """숫자만 남기고, 과도 반복 축약, 길이 1~5 허용(6 이상이면 앞 5자리만)."""
    s = ''.join(ch for ch in s if ch.isdigit())
    if not s:
        return s
    # 과도 반복 축약(같은 숫자 3연속 이상 → 2개로 축약)
    out = []
    for ch in s:
        if len(out) >= 2 and out[-1] == ch and out[-2] == ch:
            continue
        out.append(ch)
    s = ''.join(out)
    # 최대 길이 5로 제한(1~5 허용)
    if len(s) > 5:
        s = s[:5]
    return s

def score_len_bias(s: str):
    L = len(s)
    if L == 4: return 0.27   # 0.40 → 0.28 (완화)
    if L == 3: return 0.27   # 0.30 → 0.26 (거의 동등)
    if L == 2: return 0.14   # 2자리는 여전히 5보다 우대
    if L == 5: return 0.06
    if L == 1: return 0.02
    return -0.10
def decode_ctc(preds):
    preds_log_softmax = F.log_softmax(preds, dim=2)
    probs = preds_log_softmax.exp()                 # [B, T, C]
    max_probs, idxs = probs.max(2)                  # [B, T], [B, T]
    preds_size = torch.IntTensor([preds.size(1)] * preds.size(0))
    texts = converter.decode(idxs, preds_size)      # list[str]
    conf = max_probs.mean(dim=1).cpu().numpy().tolist()  # 간단 confidence
    return texts, conf

def edit_distance(s1, s2):
    m, n = len(s1), len(s2)
    dp = [[0]*(n+1) for _ in range(m+1)]
    for i in range(m+1): dp[i][0] = i
    for j in range(n+1): dp[0][j] = j
    for i in range(1, m+1):
        for j in range(1, n+1):
            cost = 0 if s1[i-1]==s2[j-1] else 1
            dp[i][j] = min(dp[i-1][j]+1, dp[i][j-1]+1, dp[i-1][j-1]+cost)
    return dp[m][n]

def pick_best(votes):
    """
    votes: List[(pp, conf, raw)]
      pp   : 후처리(숫자만/반복축약/≤5) 문자열
      conf : 모델 원시 conf(타임스텝 최대확률 평균)
      raw  : 원 디코드 문자열
    """
    v2 = [v for v in votes if v[0] != ""]
    if not v2:
        best = max(votes, key=lambda x: x[1])
        return postprocess_digits(best[2]), best[1], best[2]

    # 길이 가중 포함 점수
    scored = [(pp, (conf + score_len_bias(pp)), raw) for (pp, conf, raw) in v2]

    # (a) 동일 문자열 다수결, (b) 그 그룹 평균 점수로 tie-break
    grp = defaultdict(list)
    for pp, sc, raw in scored:
        grp[pp].append(sc)

    best_str, best_count, best_avg = None, -1, -1e9
    for k, arr in grp.items():
        c = len(arr)
        avg = sum(arr)/len(arr)
        if c > best_count or (c == best_count and avg > best_avg):
            best_str, best_count, best_avg = k, c, avg

    # 3자리 보정: 다수결이 3자리이고, 4자리 후보 중 편집거리 1 이내가 있으면 4자리로 승격
    if len(best_str) == 3:
        four_cands = [pp for pp in grp.keys() if len(pp) == 4]
        near4 = [(c, sum(grp[c])/len(grp[c])) for c in four_cands if edit_distance(best_str, c) <= 1]
        if near4:
            near4.sort(key=lambda x: x[1], reverse=True)
            best_str, best_avg = near4[0][0], near4[0][1]

    raw_sample = ""
    for pp, sc, raw in scored:
        if pp == best_str:
            raw_sample = raw
            break
    return best_str, best_avg, raw_sample

def run_single(path):
    base_pil = Image.open(path).convert('L')
    votes = []  # (pp, conf, raw)
    with torch.no_grad():
        for iw in IMGW_LIST:
            collate = AlignCollate(imgH=opt.imgH, imgW=iw, keep_ratio_with_pad=opt.PAD)
            for deg in ROT_DEGS:
                g = pil_to_cv_gray(base_pil)
                if abs(deg) > 1e-6:
                    g = slight_rotate(g, deg)
                pil = Image.fromarray(g)

                # batch=1
                imgs, _ = collate([(pil, "dummy")])
                imgs = imgs.to(device)
                preds = model(imgs, None)
                txts, confs = decode_ctc(preds)
                raw = txts[0]
                pp = postprocess_digits(raw)
                votes.append((pp, confs[0], raw))

    return pick_best(votes)

def main():
    names = [n for n in os.listdir(IMAGE_DIR) if n.lower().endswith(('.jpg','.jpeg','.png','.bmp','.webp'))]
    names.sort()
    if not names:
        print(f"[INFO] 이미지 없음: {IMAGE_DIR}")
        return
    for n in names:
        p = os.path.join(IMAGE_DIR, n)
        s, c, raw = run_single(p)
        print(f"{n}\t=>\t{s}\t(conf~{c:.3f})\t(raw:{raw})")

if __name__ == "__main__":
    main()
