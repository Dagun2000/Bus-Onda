# busapi.py — Raspberry Pi ↔ Server WebSocket 통신 모듈
import json, time, socket, random, threading
import websocket

class BusAPI(threading.Thread):
    """
    버스 단말 ↔ 서버 간 WebSocket 통신 스레드
    - 500ms마다 telemetry 송신
    - 서버로부터 제어/명령 수신 처리
    """

    def __init__(self, device_id, bus_no, vehicle_no, direction="상행", server_ip="127.0.0.1", device_type=2):
        super().__init__(daemon=True)
        self.device_id   = device_id
        self.bus_no      = bus_no
        self.vehicle_no  = vehicle_no
        self.direction   = direction      # '상행' 또는 '하행'
        self.server_ip   = server_ip
        self.device_type = device_type    # 1=휴대폰, 2=버스, 3=정류장
        self.stop_flag   = threading.Event()
        self.ws          = None
        self.rtt_ms      = None
        self.current_stop_name = None
        self.last_send   = 0
        self.listeners   = {}             # event:callback
        self.connected   = False

    # -------------------- 유틸 --------------------
    def local_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))  # 외부로 나갈 수 있는 인터페이스 찾기
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            return "0.0.0.0"


    def on(self, event, callback):
        """서버→장치 명령 핸들러 등록"""
        self.listeners[event] = callback

    def emit(self, event, data=None):
        """서버 명령 콜백 실행"""
        cb = self.listeners.get(event)
        if cb:
            try: cb(data)
            except Exception as e: print(f"[BusAPI] 콜백 오류: {e}")

    # -------------------- 송신 --------------------
    def send(self, obj):
        """안전한 송신 함수 (WebSocket 연결 확인 후 전송)"""
        try:
            if self.ws and getattr(self.ws, "connected", False):
                self.ws.send(json.dumps(obj))
            else:
                # 연결 끊김 상태 로그
                if not getattr(self.ws, "connected", False):
                    print("[BusAPI] 송신 실패: WebSocket 연결 끊김")
        except Exception as e:
            print(f"[BusAPI] send 실패: {e}")
        

    def send_hello(self):
        msg = {
            "type": "hello",
            "device": {
                "id": self.device_id,
                "ip": self.local_ip(),
                "device_type": self.device_type
            },
            "payload": {
                "bus_number": self.bus_no,
                "vehicle_number": self.vehicle_no,
                "direction": self.direction
            }
        }
        self.send(msg)

    def send_telem(self):
        """500 ms마다 GPS, 상태 등 송신"""
        now = time.time()
        if now - self.last_send < 0.5:  # TPS=2
            return
        self.last_send = now
        msg_id = f"t-{int(now*1000)}-{random.randint(0,999)}"
        payload = {
            "gps": getattr(self, "gps_data", None) or {},
            "status": getattr(self, "status", "idle"),
            "bus_number": self.bus_no,
            "vehicle_number": self.vehicle_no,
            "direction": self.direction
        }
        msg = {
            "type": "telemetry",
            "msg_id": msg_id,
            "ts": int(now * 1000),
            "device": {
                "id": self.device_id,
                "ip": self.local_ip(),
                "device_type": self.device_type
            },
            "payload": payload
        }
        self.send(msg)

    # -------------------- 수신 처리 --------------------
    def handle_message(self, obj):
        print("[BusAPI] RAW 수신됨:", obj)
        t = obj.get("type")

        # info / command / event → 공통 명령 처리
        if t in ("command", "event", "info"):
            payload = obj.get("payload", {})
            cmd = obj.get("cmd") or payload.get("command")
            print(f"[BusAPI] 명령 수신: {cmd}")
            self.emit(cmd, payload)
            return

        # ack 처리
        if t == "ack" and "ts" in obj:
            self.rtt_ms = int((time.time() * 1000) - obj["ts"])
            return

        # 승차 요청 (ride_request)
        if t == "ride_request":
            payload = obj.get("payload", {})
            self.emit("ride_request", payload)
            return

        # 하차 요청 (alight_request)
        if t == "alight_request":
            payload = obj.get("payload", {})
            self.emit("drop_request", payload)   # drop_request로 통일
            return




    # -------------------- 스레드 실행 --------------------
    def run(self):
        url = f"ws://{self.server_ip}:3000/device-ws"
        print(f"[BusAPI] 연결 시도: {url}")
        try:
            self.ws = websocket.create_connection(url, timeout=3, header=["User-Agent: buson-device"])
            self.connected = True
            print("[BusAPI] 연결 성공")
            self.send_hello()
            self.ws.settimeout(0.2)
        except Exception as e:
            print(f"[BusAPI] 연결 실패: {e}")
            return

        try:
            while not self.stop_flag.is_set():
                # 주기적 송신
                self.send_telem()

                # 서버 수신
                try:
                    raw = self.ws.recv()
                    if not raw: continue
                    obj = json.loads(raw)
                    print("[BusAPI] RAW 수신됨:", obj)
                    self.handle_message(obj)
                except websocket.WebSocketTimeoutException:
                    pass
                except Exception as e:
                    print(f"[BusAPI] 수신 오류: {e}")

            # 종료 시점
            print("[BusAPI] 종료 요청됨")
        finally:
            try: self.ws.close()
            except: pass
            self.connected = False

    def stop(self):
        self.stop_flag.set()
        try:
            if self.ws: self.ws.close()
        except: pass
