# lcdsystem.py — 공용 LCD/터치 관리 모듈
from luma.core.interface.serial import spi
from luma.lcd.device import ili9341
from PIL import Image, ImageDraw, ImageFont
from xpt2046 import XPT2046
import time

# ====== LCD 초기화 ======
serial = spi(port=0, device=0, gpio_DC=24, gpio_RST=25)
#serial = spi(port=0, device=0, gpio=None)

device = ili9341(serial, width=320, height=240, rotate=0)

# ====== 터치 초기화 ======
touch = XPT2046(irq_pin=23, spi_bus=0, spi_dev=1, rotate=1)

# ====== 폰트 로드 ======
FONT_BIG   = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf", 26)
FONT_MED   = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf", 20)
FONT_SMALL = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothic.ttf", 16)
FONT_MONO  = ImageFont.truetype("/usr/share/fonts/truetype/nanum/NanumGothic.ttf", 14)

# ====== 공용 유틸 ======
def clear(color="black"):
    img = Image.new("RGB", device.size, color)
    device.display(img)

def draw_status(line1, line2="", color="white", bg="black"):
    """상태 메시지를 중앙 정렬로 표시"""
    img = Image.new("RGB", device.size, bg)
    draw = ImageDraw.Draw(img)

    bbox1 = draw.textbbox((0, 0), line1, font=FONT_MED)
    tw1, th1 = bbox1[2] - bbox1[0], bbox1[3] - bbox1[1]
    draw.text(((320 - tw1)//2, 80), line1, font=FONT_MED, fill=color)

    if line2:
        bbox2 = draw.textbbox((0, 0), line2, font=FONT_SMALL)
        tw2, th2 = bbox2[2] - bbox2[0], bbox2[3] - bbox2[1]
        draw.text(((320 - tw2)//2, 120), line2, font=FONT_SMALL, fill="#ccc")

    device.display(img)

def wait_touch_exit(timeout=None):
    """화면 탭을 기다림 (디버그용)"""
    t0 = time.time()
    while True:
        if touch.touched():
            pos = touch.read()
            if pos: return pos
        if timeout and (time.time() - t0) > timeout:
            return None
        time.sleep(0.02)
