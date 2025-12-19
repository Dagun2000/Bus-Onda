# xpt2046.py  (깔끔 버전)
import spidev
import RPi.GPIO as GPIO
import time

class XPT2046:
    def __init__(self, irq_pin=23, spi_bus=0, spi_dev=1, max_speed=2000000, rotate=0):
        self.irq = irq_pin
        self.rotate = rotate
        self.width, self.height = 320, 240

        GPIO.setmode(GPIO.BCM)
        GPIO.setwarnings(False)
        # IRQ 없으면 주석처리해도 됨
        GPIO.setup(self.irq, GPIO.IN, pull_up_down=GPIO.PUD_UP)

        self.spi = spidev.SpiDev()
        self.spi.open(spi_bus, spi_dev)   # CE1 = /dev/spidev0.1
        self.spi.max_speed_hz = max_speed
        self.spi.mode = 0b00

        # 초기 대충 값(캘리브레이션으로 조정 예정)
        self.x_min, self.x_max = 200, 3900
        self.y_min, self.y_max = 200, 3900

    def _xfer(self, cmd):
        # 일부 보드는 첫 프레임이 더미일 수 있어 2번 읽기
        self.spi.xfer2([cmd, 0x00, 0x00])
        r = self.spi.xfer2([cmd, 0x00, 0x00])
        return r

    def touched(self):
        # IRQ 없으면 항상 True로 두고 폴링해도 됨 (return True)
        return GPIO.input(self.irq) == GPIO.LOW

    def read_raw(self):
        xs, ys = [], []
        for _ in range(5):
            r = self._xfer(0x90)  # Y
            y = ((r[1] << 8) | r[2]) >> 3
            r = self._xfer(0xD0)  # X
            x = ((r[1] << 8) | r[2]) >> 3
            xs.append(x); ys.append(y)
            time.sleep(0.001)
        xs.sort(); ys.sort()
        return xs[len(xs)//2], ys[len(ys)//2]

    def read(self):
        if not self.touched():
            return None
        x_raw, y_raw = self.read_raw()

        x = (x_raw - self.x_min) / float(self.x_max - self.x_min)
        y = (y_raw - self.y_min) / float(self.y_max - self.y_min)
        x = min(max(x, 0.0), 1.0)
        y = min(max(y, 0.0), 1.0)

        if self.rotate == 0:
            px, py = int(x * self.width), int((1.0 - y) * self.height)
        elif self.rotate == 90:
            px, py = int(y * self.width), int(x * self.height)
        elif self.rotate == 180:
            px, py = int((1.0 - x) * self.width), int(y * self.height)
        else:  # 270
            px, py = int((1.0 - y) * self.width), int((1.0 - x) * self.height)

        return px, py
