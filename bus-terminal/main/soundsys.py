# soundsys.py
import os
import threading
from gtts import gTTS
from pygame import mixer

class SoundSystem:
    def __init__(self, lang="ko"):
        self.lang = lang
        self.lock = threading.Lock()
        mixer.init()

    def speak(self, text: str):
        """텍스트를 음성으로 출력"""
        with self.lock:
            try:
                tts = gTTS(text=text, lang=self.lang)
                tmp_path = "/tmp/speak.mp3"
                tts.save(tmp_path)
                mixer.music.load(tmp_path)
                mixer.music.play()
                while mixer.music.get_busy():
                    pass
            except Exception as e:
                print(f"[SoundSys] 오류: {e}")

    def announce_bus(self, bus_no: str):
        """버스 번호를 음성으로 안내"""
        self.speak(f"{bus_no}, {bus_no}번 버스.")
