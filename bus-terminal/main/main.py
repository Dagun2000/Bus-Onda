# main.py
from luma.core.interface.serial import spi
from luma.lcd.device import ili9341
from PIL import Image, ImageDraw, ImageFont
import RPi.GPIO as GPIO
import time, json, os, subprocess, socket
from datetime import datetime
from xpt2046 import XPT2046
import logging
import subprocess, signal, time
from lcdsystem import device, touch, draw_status

# ---------- 디스플레이 ----------
serial = spi(port=0, device=0, gpio_DC=24, gpio_RST=25)
device = ili9341(serial, width=320, height=240, rotate=0)

FONT_BIG   = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf", 26)
FONT_MED   = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf", 20)
FONT_SMALL = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothic.ttf", 16)

# ---------- 터치 ----------
#touch = XPT2046(cs_pin=7, irq_pin=23, spi_bus=0, spi_dev=1, rotate=0)
touch = XPT2046(irq_pin=23, spi_bus=0, spi_dev=1, rotate=1)


# ---------- 경로/캐시 ----------
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONF_PATH = os.path.join(BASE_DIR, "config.json")
BUSSYS   = os.path.join(BASE_DIR, "bussys.py")

logging.basicConfig(
    filename='bussys_log.txt',
    level=logging.INFO,
    format='[%(asctime)s] %(message)s',
    encoding='utf-8'
)

def load_conf():
    if os.path.exists(CONF_PATH):
        with open(CONF_PATH, "r") as f: 
            return json.load(f)
    return {
        "device_id": "",
        "server_ip": "",
        "vehicle_no": "",
        "bus_no": ""
    }

def save_conf(cfg):
    with open(CONF_PATH, "w") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)

# ---------- 키패드 ----------
KEYS = [
    ["1","2","3","."],
    ["4","5","6",":"],
    ["7","8","9","="],   # "=" 다음 단계
    ["C","0","⌫",""]     # C=초기화, ⌫=백스페이스, ""=빈칸
]
GRID = (4,4)
PAD_MARGIN = 8
TOP_INFO_H = 70  # 상단 안내/입력창 높이

def draw_keypad(draw):
    w, h = device.size
    grid_w = w - PAD_MARGIN*2
    grid_h = h - TOP_INFO_H - PAD_MARGIN*2
    cell_w = grid_w // GRID[0]
    cell_h = grid_h // GRID[1]
    ox, oy = PAD_MARGIN, TOP_INFO_H + PAD_MARGIN

    for r in range(GRID[1]):
        for c in range(GRID[0]):
            x0 = ox + c*cell_w
            y0 = oy + r*cell_h
            x1 = x0 + cell_w - 4
            y1 = y0 + cell_h - 4
            draw.rounded_rectangle((x0,y0,x1,y1), radius=10, outline="white", width=2)
            label = KEYS[r][c]
            if label:
                tw, th = draw.textsize(label, font=FONT_BIG)
                draw.text((x0 + (cell_w - tw)//2, y0 + (cell_h - th)//2), label, font=FONT_BIG, fill="white")

def hit_test(px, py):
    w, h = device.size
    if py < TOP_INFO_H: 
        return None
    grid_w = w - PAD_MARGIN*2
    grid_h = h - TOP_INFO_H - PAD_MARGIN*2
    cell_w = grid_w // GRID[0]
    cell_h = grid_h // GRID[1]
    ox, oy = PAD_MARGIN, TOP_INFO_H + PAD_MARGIN

    if not (ox <= px <= ox+grid_w and oy <= py <= oy+grid_h): 
        return None
    c = (px - ox) // cell_w
    r = (py - oy) // cell_h
    c = max(0, min(GRID[0]-1, c))
    r = max(0, min(GRID[1]-1, r))
    return KEYS[r][c]

def draw_top(draw, step, prompt, value, cached=False):
    # 상단 박스 + 안내
    draw.rectangle((0,0,320,TOP_INFO_H), fill="black")
    info = f"[{step}/4] {prompt}"
    draw.text((10, 8), info, font=FONT_MED, fill="#9ad0ff" if not cached else "#6effa1")
    draw.rectangle((10, 34, 310, 62), outline="#888", width=2)
    draw.text((16, 38), value if value else "입력 대기…", font=FONT_MED, fill="white" if value else "#aaa")
    if cached:
        draw.text((220, 8), "캐시 사용", font=FONT_SMALL, fill="#6effa1")

def draw_status(line1, line2="", color="white"):
    img = Image.new("RGB", device.size, "black")
    draw = ImageDraw.Draw(img)
    tw1, th1 = draw.textsize(line1, font=FONT_MED)
    draw.text(((320 - tw1)//2, 80), line1, font=FONT_MED, fill=color)
    if line2:
        tw2, th2 = draw.textsize(line2, font=FONT_SMALL)
        draw.text(((320 - tw2)//2, 120), line2, font=FONT_SMALL, fill="#ccc")
    device.display(img)

def connect_server(ip):
    # 서버 IP: ws://<ip>:3000/device-ws 에 접속 시도 (1초 타임아웃)
    import websocket
    url = f"ws://{ip}:3000/device-ws"
    try:
        ws = websocket.create_connection(url, timeout=1.0)
        ws.close()
        return True, None
    except Exception as e:
        return False, str(e)

'''
def run_bussys():
    # bussys.py 실행 (별도 프로세스)
    try:
        subprocess.Popen(["python3", BUSSYS], cwd=BASE_DIR)
        return True
    except Exception as e:
        draw_status("bussys 실행 실패", str(e), color="#ff7070")
        time.sleep(2)
        return False'''
        
        
def run_bussys():
    try:
        # Popen을 전역 변수로 저장
        global bussys_proc
        bussys_proc = subprocess.Popen(
            ["python3", BUSSYS],
            cwd=BASE_DIR,
            start_new_session=False  # 같은 세션에 두면 Ctrl+C 신호 같이 받음
        )
        return True
    except Exception as e:
        draw_status("bussys 실행 실패", str(e), color="#ff7070")
        time.sleep(2)
        return False

def input_loop():
    cfg = load_conf()

    steps = [
        ("단말 UID 입력", "device_id", "숫자/문자 가능. '=' 다음", "alnum"),
        ("서버 IP 입력", "server_ip", "예: 192.168.0.10  '=' 다음", "ip"),
        ("차량 번호(4자리)", "vehicle_no", "숫자4자리. '=' 다음", "4d"),
        ("버스 번호(2~4자리)", "bus_no", "숫자2~4자리. '=' 다음", "2to4d"),
    ]

    step_idx = 0
    buf = cfg.get(steps[step_idx][1], "")

    while True:
        img = Image.new("RGB", device.size, "black")
        draw = ImageDraw.Draw(img)

        key_prompt, key_name, helper, rule = steps[step_idx]
        cached = bool(cfg.get(key_name))
        draw_top(draw, step_idx+1, f"{key_prompt}", buf, cached)
        draw.text((10, 66), helper, font=FONT_SMALL, fill="#bbb")
        draw_keypad(draw)
        device.display(img)

        # 터치 처리
        t0 = time.time()
        while True:
            # 가벼운 폴링
            if touch.touched():
                pos = touch.read()
                if pos:
                    k = hit_test(*pos)
                    if k is None:
                        continue
                    if k == "":  # 빈칸
                        time.sleep(0.15); break
                    if k == "C":
                        buf = ""
                    elif k == "⌫":
                        buf = buf[:-1]
                    elif k == "=":
                        # 검증
                        if validate(rule, buf):
                            cfg[key_name] = buf
                            save_conf(cfg)
                            step_idx += 1
                            if step_idx >= len(steps):
                                return cfg
                            # 다음 단계 준비
                            buf = cfg.get(steps[step_idx][1], "")
                            time.sleep(0.2)
                            break
                        else:
                            # 규칙 불일치
                            draw_status("형식 오류", f"입력 다시 확인: {helper}", color="#ff9f43")
                            time.sleep(1.2)
                    else:
                        # 문자 추가 (길이 제한)
                        buf = append_with_rule(rule, buf, k)

                    time.sleep(0.12)  # 디바운스
            # 캐시가 있고, 사용자가 바로 '다음'을 원하는 경우(상단 아무데나 길게 탭) 등의 UX는 추후
            if time.time() - t0 > 0.03:
                # 빠른 리프레시
                break

def validate(rule, s):
    import re
    if rule == "alnum":
        return len(s) >= 1 and re.fullmatch(r"[A-Za-z0-9\-_\.]+", s) is not None
    if rule == "ip":
        # 0~255.0~255.0~255.0~255 간단 검증
        try:
            socket.inet_aton(s); return True
        except:
            return False
    if rule == "4d":
        return len(s) == 4 and s.isdigit()
    if rule == "2to4d":
        return 2 <= len(s) <= 4 and s.isdigit()
    return False

def append_with_rule(rule, buf, k):
    # 허용 문자만 추가
    if rule in ("4d","2to4d"):
        if k.isdigit():
            # 길이 제한
            maxlen = 4 if rule=="4d" else 4
            if len(buf) < maxlen:
                return buf + k
        return buf
    if rule == "ip":
        # 숫자/점/콜론(미사용)만
        if k.isdigit() or k in ["."]:
            if len(buf) < 21:
                return buf + k
        return buf
    if rule == "alnum":
        if k in ".:":  # UID 에 점/콜론 허용
            if len(buf) < 32: return buf + k
        if k.isdigit() or k.isalpha() or k in "-_":
            if len(buf) < 32: return buf + k
        return buf
    return buf

def main():
    while True:
        cfg = input_loop()

        # 연결 시도 화면
        draw_status("서버와 연결 시도 중입니다...", f"{cfg['server_ip']}", color="#9ad0ff")
        ok, reason = connect_server(cfg["server_ip"])

        if ok:
            draw_status("서버 연결 성공!", "2초 후 시작합니다", color="#6effa1")
            logging.info(f"서버 연결 성공: {cfg['server_ip']}")
            time.sleep(2)
            if run_bussys():
                return
        else:
            # 화면에는 짧게 출력하되
            draw_status("서버 연결 실패", f"사유: \n {reason[:60]}...", color="#ff7070")
            # 전체 로그는 파일에 기록
            logging.error(f"서버 연결 실패 ({cfg['server_ip']}) - 사유 전체: {reason}")
            time.sleep(2)

if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        import traceback
        with open("fatal_error.log", "a") as f:
            f.write(f"[{datetime.now()}] {repr(e)}\n")
            traceback.print_exc(file=f)
        print("프로그램이 예기치 않게 중단되었습니다. fatal_error.log를 확인하세요.")
    finally:
        try:
            if 'bussys_proc' in globals():
                bussys_proc.terminate()
                bussys_proc.wait(timeout=3)
        except Exception:
            pass
        GPIO.cleanup()

