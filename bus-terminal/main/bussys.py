# bussys.py  — LCD 운행 대시보드 (GPS + WS + 버튼 + 터치 종료 + 콘솔)
from luma.core.interface.serial import spi
from luma.lcd.device import ili9341
from PIL import Image, ImageDraw, ImageFont
import RPi.GPIO as GPIO
import time, json, os, socket, threading, random
from datetime import datetime, timezone, timedelta
from busapi import BusAPI
from lcdsystem import device, touch, draw_status, FONT_BIG, FONT_MED, FONT_SMALL, FONT_MONO
from soundsys import SoundSystem
SOUND = SoundSystem()
from beepSys import BeepSys
BEEP = BeepSys()

# ====== 외부 모듈 ======
try:
    from gpsrx import read_gps
    HAS_GPS = True
    
except Exception:
    HAS_GPS = False

'''

# XPT2046 터치 (CE1, IRQ=23)
from xpt2046 import XPT2046

# ====== 하드웨어 설정 ======
BUTTON_PIN = 17            # 물리 토글 버튼 (LOW=눌림)
GPIO.setmode(GPIO.BCM)
GPIO.setup(BUTTON_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)

# LCD
serial = spi(port=0, device=0, gpio_DC=24, gpio_RST=25)
device = ili9341(serial, width=320, height=240, rotate=0)

# 터치
touch = XPT2046(irq_pin=23, spi_bus=0, spi_dev=1, rotate=1)  # 필요시 rotate 보정

# 폰트
FONT_BIG   = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf", 28)
FONT_MED   = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf", 20)
FONT_SMALL = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothic.ttf", 16)
FONT_MONO  = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothic.ttf", 14)

'''
BUTTON_PIN = 17
GPIO.setmode(GPIO.BCM)
GPIO.setup(BUTTON_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)
DOOR_PIN = 27 
GPIO.setup(DOOR_PIN, GPIO.IN, pull_up_down=GPIO.PUD_UP)


UI_BG = "black"
KST = timezone(timedelta(hours=9))

# ====== 설정 파일 (서버 IP/ID 등) ======
BASE_DIR  = os.path.dirname(os.path.abspath(__file__))
CONF_PATH = os.path.join(BASE_DIR, "config.json")

def load_conf():
    if os.path.exists(CONF_PATH):
        try:
            with open(CONF_PATH, "r") as f:
                return json.load(f)
        except Exception:
            pass
    # 최소한 서버 IP/디바이스ID 없으면 WS는 DISCONNECTED 상태만 보임
    return {"device_id":"", "server_ip":"", "vehicle_no":"", "bus_no":""}

# ====== 상태 ======
is_disabled_mode = False        # False: “노약자 탑승 요청 없음”, True: “시각장애인 탑승”
blink_state = False
last_blink = 0.0
blink_interval = 0.5

# 버튼 디바운스/엣지 검출
_last_btn = 1  # 풀업이므로 평소 1(HIGH)
_last_btn_ts = 0.0
BTN_DEBOUNCE = 0.12

# 미니 콘솔(최근 8줄)
class RingLog:
    def __init__(self, cap=8):
        self.cap = cap
        self.buf = []
        self.lock = threading.Lock()
    def add(self, line):
        with self.lock:
            ts = datetime.now(KST).strftime("%H:%M:%S")
            self.buf.insert(0, f"{ts} · {line}")
            if len(self.buf) > self.cap:
                self.buf.pop()
    def lines(self):
        with self.lock:
            return list(reversed(self.buf))
        
def on_ride_request(d):
    api.status = "ride_pending"        # <- self가 아니라 api
    api.current_stop_name = d.get("stopName") or d.get("stopNo")
    LOG.add(f"승차 요청 수신: {api.current_stop_name}")
    BEEP.alert_ride_request()

LOG = RingLog()

def force_idle(api):
    """버튼 눌러서 강제 대기(요청 없음)로 복귀"""
    if not api:
        return
    api.status = "idle"
    api.current_stop_name = None
    BEEP.stop()
    LOG.add("버튼으로 상태 초기화 → 요청 없음")

# ====== WS 클라이언트 스레드 ======
class WSClient(threading.Thread):
    def __init__(self, device_type=2):  # 1=휴대폰, 2=버스, 3=정류장
        super().__init__(daemon=True)
        self.device_type = device_type
        self.state = "DISCONNECTED"
        self.last_err = ""
        self.rtt_ms = None
        self.stop_flag = threading.Event()
        self.ws = None
        self.api = None  # BusAPI 인스턴스를 외부에서도 접근 가능하게 저장

    
    def run(self):
        import websocket, json
        last_send = 0
        send_interval = 0.5  # 500ms마다 전송

        def on_ride_request(d):
            api.status = "ride_pending"        # <- self가 아니라 api
            api.current_stop_name = d.get("stopName") or d.get("stopNo")
            LOG.add(f"승차 요청 수신: {api.current_stop_name}")
            BEEP.alert_ride_request()

        def on_drop_request(d):
            api.status = "drop_pending"
            api.current_stop_name = d.get("stopName") or d.get("stopNo")
            LOG.add(f"하차 요청: {api.current_stop_name}")
            BEEP.alert_drop_request()


        while not self.stop_flag.is_set():
            cfg = load_conf()
            ip    = (cfg.get("server_ip") or "").strip()
            devid = (cfg.get("device_id") or "").strip()
            busno = (cfg.get("bus_no") or None)
            vehno = (cfg.get("vehicle_no") or None)

            # BusAPI 인스턴스 생성 및 보관
            self.api = BusAPI(
                device_id=cfg["device_id"],
                bus_no=cfg["bus_no"],
                vehicle_no=cfg["vehicle_no"],
                direction="상행",
                server_ip=cfg["server_ip"]
            )
            api = self.api  # alias

            # 상태 기본값 보장
            self.api.status = "idle"

            # 서버 명령 이벤트 등록
            self.api.on("ride_request", on_ride_request)
            self.api.on("drop_request", on_drop_request)
            
            '''
            self.api.on("drop_request", lambda d: (
                setattr(self.api, "status", "drop_pending"),
                LOG.add("시각장애인 하차 요청 수신"),
                BEEP.alert_drop_request()
            ))
            '''

            
            self.api.on("cancel_request", lambda d: (
                setattr(self.api, "status", "idle"),
                LOG.add("요청 취소 수신 (대기 상태로 전환)"),
                BEEP.stop()
            ))

            self.api.on("reset", lambda _: (
                setattr(self.api, "status", "resetting"),
                LOG.add("강제 리셋 명령 수신 — 상태 초기화 중"),
                BEEP.stop()

            ))

            # BusAPI 스레드 시작
            self.api.start()

            # IP / ID 확인
            if not ip or not devid:
                self.state = "DISCONNECTED"
                time.sleep(0.5)
                continue

            url = f"ws://{ip}:3000/device-ws"
            try:
                self.state = "CONNECTING"
                LOG.add(f"WS 연결 시도: {url}")
            #    LOG.add(f"WS 연결 시도: 링크 검열")
                self.ws = websocket.create_connection(
                    url, timeout=3, header=["User-Agent: buson-device"]
                )
                self.state = "CONNECTED"
                self.last_err = ""
                LOG.add("WS 연결됨")

                # hello 패킷에 device_type 추가
                '''
                hello = {
                    "type": "hello",
                    "device": {
                        "id": devid,
                        "ip": local_ip(),
                        "device_type": self.device_type
                    },
                    "payload": {
                        "bus_number": busno,
                        "vehicle_number": vehno
                    }
                }
                self.ws.send(json.dumps(hello))
                LOG.add("hello 전송")
'''
                self.ws.settimeout(0.2)
                pending_ack = None
                sent_ts = 0

                while not self.stop_flag.is_set():
                    now = time.time()
                    '''
                    # 주기적 telemetry 전송
                    if now - last_send >= send_interval:
                        msg_id = f"t-{int(now*1000)}-{random.randint(0,999)}"
                        telem = {
                            "type": "telemetry",
                            "msg_id": msg_id,
                            "ts": int(now*1000),
                            "device": {
                                "id": devid,
                                "ip": local_ip(),
                                "device_type": self.device_type
                            },
                            "payload": {"status": self.api.status}  # 현재 상태 포함
                        }
                        self.ws.send(json.dumps(telem))
                        last_send = now
                        pending_ack = msg_id
                        sent_ts = now
                '''
                    # 수신 처리
                    try:
                        raw = self.ws.recv()
                        if not raw:
                            continue
                        obj = json.loads(raw)
                        if obj.get("type") == "ack" and obj.get("ack_id") == pending_ack:
                            self.rtt_ms = int((time.time() - sent_ts) * 1000.0)
                            pending_ack = None
                            LOG.add(f"ACK {obj.get('ack_id')} ({self.rtt_ms} ms)")
                    except Exception:
                        pass

                try:
                    self.ws.close()
                except:
                    pass
                self.ws = None

            except Exception as e:
                self.state = "ERROR"
                self.last_err = str(e)
                LOG.add(f"WS 오류: {self.last_err}")
                time.sleep(1.2)

    def stop(self):
        self.stop_flag.set()



def local_ip():
    try:
        return socket.gethostbyname(socket.gethostname())
    except Exception:
        return "0.0.0.0"

# ====== GPS 폴링 스레드 ======
class GPSPoller(threading.Thread):
    def __init__(self):
        super().__init__(daemon=True)
        self.status = "NO_MODULE"
        self.info = {}
        self.stop_flag = threading.Event()

    def run(self):
        if not HAS_GPS:
            self.status = "NO_MODULE"; return
        last = 0
        while not self.stop_flag.is_set():
            if time.time() - last < 1.0:
                time.sleep(0.05); continue
            last = time.time()
            try:
                st, info = read_gps()
                self.status = st
                self.info = info or {}
            except Exception:
                self.status = "NO_MODULE"
                self.info = {}

    def stop(self):
        self.stop_flag.set()

# ====== UI 그리기 ======
def draw_dashboard(wscli: WSClient, gps: GPSPoller, api=None):
    global blink_state, last_blink, is_disabled_mode

    # 깜빡임
    now = time.time()
    if now - last_blink >= blink_interval:
        blink_state = not blink_state
        last_blink = now

    img = Image.new("RGB", device.size, UI_BG)
    draw = ImageDraw.Draw(img)

    # 상단 바 + [X]
    draw.rectangle((0, 0, 320, 30), fill="#111")
    draw.rectangle((282, 4, 312, 26), outline="#f66", width=2)
    #tw, th = draw.textsize("X", font=FONT_MED)
    bbox = draw.textbbox((0, 0), "X", font=FONT_MED)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text((297 - tw//2, 15 - th//2), "X", font=FONT_MED, fill="#f66")

    cfg = load_conf()
    draw.text((10, 6), f"ID:{cfg.get('device_id','')}", font=FONT_SMALL, fill="#9ad0ff")

    y = 34

    # GPS 상태
    if HAS_GPS:
        if gps.status == "NO_MODULE":
            gps_line = "GPS 연결되었음"
            gps_col = "#bbb"
        elif gps.status == "NO_FIX":
            gps_line = "GPS 연결 되었음"
            gps_col = "#ff7070"
        else:
            lat = gps.info.get("lat", "-")
            lon = gps.info.get("lon", "-")
            gps_line = f"GPS 연결됨 ({lat}, {lon})"
            gps_col = "white"
    else:
        gps_line = "GPS 미사용"
        gps_col = "#bbb"
    draw.text((10, y), gps_line, font=FONT_SMALL, fill=gps_col)
    y += 18

    # WS 상태
    if wscli.state == "CONNECTED":
        ws_line = f"WS 연결됨 ({wscli.rtt_ms or '-'} ms)"
        ws_col = "#6effa1"
    elif wscli.state == "CONNECTING":
        ws_line = "WS 연결 중…"
        ws_col = "#9ad0ff"
    elif wscli.state == "ERROR":
        ws_line = f"WS 오류: {wscli.last_err[:30]}..."
        ws_col = "#ff9f43"
    else:
        ws_line = "WS 끊김"
        ws_col = "#ff7070"
    draw.text((10, y), ws_line, font=FONT_SMALL, fill=ws_col)
    y += 20
    draw.line((10, y + 10, 310, y + 10), fill="#333", width=1)
    y += 20

    # 콘솔 박스
    draw.text((10, y - 6), "Console", font=FONT_SMALL, fill="#9ad0ff")
    box_y0 = y + 12
    draw.rectangle((10, box_y0, 310, 155), outline="#555", width=1)
    lines = LOG.lines()
    py = 152
    for line in reversed(lines):
        draw.text((14, py), line, font=FONT_MONO, fill="#ddd")
        py -= 16
        if py < box_y0 + 2:
            break



    # ------------------------------
    # 하단 상태 (버스 단말 상태 표시)
    # ------------------------------
    status_text = "대기 중"
    stop_line = ""         # <- 정류장 이름용
    color_fg = "black"
    color_bg = "white"

    if api:
        st = getattr(api, "status", "idle")
        stop_name = getattr(api, "current_stop_name", None)

        if st == "idle":
            status_text = "요청 없음"

        elif st == "ride_pending":
            status_text = "시각장애인 승차 요청"
            color_fg = "red" if blink_state else "white"
            color_bg = "white" if blink_state else "red"
            if stop_name:
                stop_line = stop_name

        elif st == "ride_active":
            status_text = "탑승 중"
            color_fg = "white"
            color_bg = "green"
            if stop_name:
                stop_line = stop_name

        elif st == "drop_pending":
            status_text = "하차 요청"
            color_fg = "yellow" if blink_state else "black"
            color_bg = "black" if blink_state else "yellow"
            if stop_name:
                stop_line = stop_name

        elif st == "resetting":
            status_text = "상태 초기화 중…"
            color_fg = "white"
            color_bg = "#444"

    # 실제 그리기
    draw.rectangle((0, 160, 320, 240), fill=color_bg)
    bbox = draw.textbbox((0, 0), status_text, font=FONT_BIG)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text(((320 - tw)//2, 170), status_text, font=FONT_BIG, fill=color_fg)

    # 정류장 이름 한 줄 더
    if stop_line:
        bbox2 = draw.textbbox((0, 0), stop_line, font=FONT_SMALL)
        tw2, th2 = bbox2[2] - bbox2[0], bbox2[3] - bbox2[1]
        draw.text(((320 - tw2)//2, 170 + th + 6), stop_line, font=FONT_SMALL, fill=color_fg)


    # 하단 시각
    now_str = datetime.now(KST).strftime("%H:%M:%S")
    #tw, th = draw.textsize(now_str, font=FONT_SMALL)
    bbox = draw.textbbox((0, 0), now_str, font=FONT_SMALL)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text((320 - tw - 8, 240 - th - 4), now_str, font=FONT_SMALL, fill="#888")

    device.display(img)


def point_in_exit(px, py):
    # [X] hit-test (282,4)-(312,26)
    return 282 <= px <= 312 and 4 <= py <= 26

# ====== 메인 루프 ======
def main():
    global is_disabled_mode, _last_btn, _last_btn_ts

    # 백그라운드
    wscli = WSClient(); wscli.start()
    gps = GPSPoller(); gps.start()
    last_door = GPIO.input(DOOR_PIN)  # 초기값
    door_state = "close"

    try:
        while True:
            now = time.time()
            val = GPIO.input(BUTTON_PIN)
            door_val = GPIO.input(DOOR_PIN)

            # ----- 문 열림 감지 -----
            if door_val != last_door:
                last_door = door_val
                if door_val == GPIO.LOW:
                    door_state = "open"
                    LOG.add("문 열림 감지됨")
                    # BusAPI에 door 상태 송신
                    if wscli.api and wscli.api.connected:
                        wscli.api.send({
                            "type": "event",
                            "event": "door",
                            "payload": {"state": "open"}
                        })
                    # 버스 번호 음성 안내
                    cfg = load_conf()
                    bus_no = cfg.get("bus_no", "미등록")
                    SOUND.announce_bus(bus_no)
                else:
                    door_state = "close"
                    LOG.add("문 닫힘 감지됨")
                    if wscli.api and wscli.api.connected:
                        wscli.api.send({
                            "type": "event",
                            "event": "door",
                            "payload": {"state": "close"}
                        })

            # ----- 버튼: 누르면 즉시 idle로 -----
            if val != _last_btn:
                # 눌림(HIGH->LOW) 순간만 처리
                if val == GPIO.LOW and (now - _last_btn_ts) > BTN_DEBOUNCE:
                    force_idle(wscli.api)

                _last_btn = val
                _last_btn_ts = now


            # ----- UI 업데이트 -----
            draw_dashboard(wscli, gps, wscli.api)
            time.sleep(0.05)


    except KeyboardInterrupt:
        pass
    finally:
        wscli.stop(); gps.stop()
        GPIO.cleanup()

if __name__ == "__main__":
    main()
