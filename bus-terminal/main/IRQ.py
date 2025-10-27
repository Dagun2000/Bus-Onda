# quick_irq_check.py
import RPi.GPIO as GPIO, time
GPIO.setmode(GPIO.BCM)
GPIO.setup(23, GPIO.IN, pull_up_down=GPIO.PUD_UP)
try:
    while True:
        print('IRQ=', GPIO.input(23))  # 안누름=1, 누르면=0 이어야 정상
        time.sleep(0.2)
except KeyboardInterrupt:
    pass
