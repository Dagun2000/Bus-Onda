# touch_diag.py
from xpt2046 import XPT2046
import time

def test(dev):
    print(f"== Test CE{dev} (/dev/spidev0.{dev}) ==")
    t = XPT2046(irq_pin=23, spi_bus=0, spi_dev=dev)
    print("Press and hold the screen...")
    for _ in range(40):
        if t.touched():
            print("raw:", t.read_raw())
        time.sleep(0.1)
    print()

for dev in (1, 0):  # CE1 먼저, 안 되면 CE0
    test(dev)
