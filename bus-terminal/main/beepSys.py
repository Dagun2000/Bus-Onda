# beepSys.py — PWM 기반 비프 제어 + 지속 알림 지원
import RPi.GPIO as GPIO
import time
import threading

BUZZER_PIN = 18  # BCM 기준
GPIO.setmode(GPIO.BCM)
GPIO.setup(BUZZER_PIN, GPIO.OUT)

class BeepSys:
    def __init__(self):
        self.lock = threading.Lock()
        #self.pwm = GPIO.PWM(BUZZER_PIN, 1)  # 기본 주파수 placeholder
        self.pwm = None
        self.active = False
        self.thread = None

    def _beep_thread(self, freq, pattern):
        """내부 반복 스레드"""
        while self.active:
            for f, dur in pattern:
                if not self.active:
                    break
                self.pwm.ChangeFrequency(f)
                self.pwm.start(50)  # duty cycle 50%
                time.sleep(dur)
                self.pwm.stop()
                time.sleep(0.1)

    def start_pattern(self, freq, pattern):
        with self.lock:
            if self.pwm is None:
                self.pwm = GPIO.PWM(BUZZER_PIN, freq)
            else:
                self.pwm.ChangeFrequency(freq)

            self.stop()
            self.active = True
            self.thread = threading.Thread(
                target=self._beep_thread, args=(freq, pattern), daemon=True)
            self.thread.start()


    def stop(self):
        """비프 중단"""
        self.active = False
        self.pwm.stop()

    # =====================
    # 알림 패턴 정의
    # =====================
    def alert_ride_request(self):
        """
        승차 요청 알림: 880Hz 빠른 삑삑 반복 (시각장애인 탑승 요청)
        """
        pattern = [(880, 0.2), (880, 0.2), (880, 0.2)]
        self.start_pattern(880, pattern)

    def alert_drop_request(self):
        """
        하차 요청 알림: 600Hz 낮은 톤, 길게 울림 반복
        """
        pattern = [(600, 0.4), (600, 0.4)]
        self.start_pattern(600, pattern)

    def alert_idle(self):
        """요청 종료 시 정지"""
        self.stop()

    def cleanup(self):
        self.stop()
        GPIO.cleanup(BUZZER_PIN)
