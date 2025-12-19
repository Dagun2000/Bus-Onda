# 🚌 버스 온다 (Bus Onda)

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Kotlin-purple)
![AI](https://img.shields.io/badge/Model-YOLOv8n%20%7C%20TFLite-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

> **"시각장애인의 안전하고 편리한 버스 탑승을 위한 All-in-One 솔루션"**

## 📖 소개 (Introduction)
**'버스 온다'**는 시각장애인이 버스를 이용할 때 겪는 어려움(정류장 위치 파악, 탑승 버스 식별, 빈 좌석 확보, 하차 벨 누르기 등)을 해결하기 위해 개발된 **버스 승하차 보조 시스템**입니다.

안드로이드의 기본 접근성 기능인 **TalkBack** 환경에 최적화된 UI를 설계하였으며, 빅스비(Bixby) 등 음성 비서와 연동하여 앱 실행부터 목적지 입력까지 화면을 보지 않고도 제어가 가능합니다.

집에서부터 목적지까지 끊김 없는(Seamless) 이동 경험을 제공하는 것을 목표로 합니다.

## ✨ 주요 기능 (Key Features)

### 1. 📱 사용자 앱 (User App)
* **TalkBack 최적화 UI**: 시각장애인 사용자를 위해 모든 버튼과 텍스트에 명확한 음성 라벨링(Content Description)을 적용하여, 제스처만으로 조작 가능.
* **도보 내비게이션**: TMAP API를 활용하여 정류장까지 보행자 경로 안내 및 경로 이탈 시 교정.
* **최적 경로 탐색**: 환승 부담을 줄이기 위한 '최소 환승' 위주의 경로 알고리즘 적용.
* **AI 사물 인식**:
    * **정류장/좌석 인식**: YOLOv8n 모델을 파인튜닝 및 경량화(TFLite)하여 온디바이스에서 실시간으로 정류장 팻말과 빈 좌석 감지.
    * **버스 번호 인식**: Google ML Kit (OCR)를 활용하여 진입하는 버스 번호를 정확히 식별.

### 2. 🚌 버스 시스템 (Bus Embedded System)
* **승/하차 알림**: Socket 통신을 통해 사용자의 앱에서 버스 기사님 단말기(LCD)로 탑승/하차 요청 실시간 전송.
* **음성 안내 스피커**: 정류장에 버스 도착 시 외부 스피커로 버스 번호 자동 송출.

## 🛠 기술 스택 (Tech Stack)

### 📱 Android (App)
* **Language**: Kotlin
* **AI & Vision**:
    * **Object Detection**: YOLOv8n (Fine-tuned & Converted to TFLite)
    * **OCR**: Google ML Kit (Text Recognition)
* **Maps & Location**: TMAP API
* **Communication**: Socket Communication (TCP/IP)
* **Accessibility**: TalkBack Optimized UI

### 📟 Embedded (Bus Unit)
* **Hardware**: Raspberry Pi
* **Display**: LCD Panel
* **Communication**: Socket Server (Python)

### 💾 Dataset
* **Custom Dataset**: 직접 수집(Web Crawling)한 한국 버스 환경, 정류장, 좌석 이미지 데이터

## 🚀 설치 및 실행 (Installation)

이 프로젝트를 실행하기 위해서는 **TMAP API Key**가 필요합니다. build.gradle.kts(:app) 내의 필드에 입력을 해주세요.

Raspberry Pi 없이 이용을 하기 위해서는, 프로젝트 디렉토리 내  BusStopArrivalScreen.kt, DisembarkationNotificationScreen.kt 내의 isDebugMode를 true로 바꾸고 빌드해주세요.

### 1. 레포지토리 클론 (Clone Repository)
```bash
git clone [https://github.com/username/bus-onda.git](https://github.com/username/bus-onda.git)
```

### 2. API 키 설정 (Setup API Key)
TMAP API 키를 입력합니다.


### 3. 빌드 및 실행 (Build & Run)
1. Android Studio에서 프로젝트를 엽니다.
2. Gradle Sync를 수행합니다.
3. Tmap api 키를 받아서 입력합니다.
4. 실제 안드로이드 기기를 연결하고 실행합니다. (카메라, 위치 권한 허용 필요)
5. *Tip: 시각장애인 사용자 경험을 테스트하려면 기기의 'TalkBack' 기능을 켜고 실행하세요.*

## 📂 폴더 구조 (Directory Structure)
```text
app/src/main
├── assets                  # TFLite 모델 파일 (YOLO, OCR 등)
│   ├── BasicYolo.tflite
│   ├── BusDetector.tflite
│   └── BusStation.tflite
├── java/com/app/busiscoming
│   ├── camera              # CameraX 설정 및 프레임 처리
│   ├── data                # 데이터 계층 (Repository Impl, DataStore, Network)
│   │   ├── datastore       # 로컬 설정 저장
│   │   ├── remote          # API 통신 (Retrofit, WebSocket)
│   │   ├── model           # 외부서버(API)와 주고받는 데이터 구조 정의
│   │   ├── mapper          # 서버 데이터 모델과 앱 내부 도메인 모델간의 변환
│   │   └── repository      # Repository 구현체
│   ├── detection           # AI 감지 파이프라인 (버스, 정류장, 좌석, OCR 로직)
│   ├── di                  # Hilt 의존성 주입 모듈 (NetworkModule, RepositoryModule)
│   ├── domain              # 도메인 계층 (Model, Repository Interface, UseCase)
│   ├── presentation        # UI 계층 (Jetpack Compose)
│   │   ├── components      # 공통 UI 컴포넌트
│   │   ├── navigation      # 앱 내 화면 이동 그래프
│   │   └── screens         # 기능별 화면 (Home, Recognition, Navigation 등)
│   │   │   ├── busrecognition      # 버스 인식 화면
│   │   │   ├── busstoparrival      # 정류장 도착 안내
│   │   │   ├── emptyseat           # 빈 좌석 찾기
│   │   │   └── walkingguide        # 도보 안내
│   ├── ui/theme            # Compose 테마 및 스타일
│   ├── util                # 유틸리티 (위치, 센서, TTS, 음성입력 헬퍼)
│   └── walknavi            # 도보 내비게이션 핵심 로직 (TMAP 연동)
└── res                     # 리소스 파일 (Drawables, Strings 등)
```
## 👥 팀원 소개 (Contributors)
* **이정우 (AI & Nav)**: YOLOv8n 모델 파인튜닝 및 TFLite 변환, 환승, 도보 내비게이션 구현
* **한영준 (PM & Frontend)**: 기획, UI/UX 설계, 안드로이드 앱 개발 (TalkBack 최적화)
* **이다니엘 (Embedded)**: 라즈베리 파이 기반 버스 단말기 구축 및 소켓 통신 구현

## 📜 라이선스 (License)
Distributed under the MIT License. See `LICENSE` for more information.
