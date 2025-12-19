import serial
from datetime import datetime, timezone, timedelta

def read_gps():
    try:
        ser = serial.Serial("/dev/serial0", baudrate=9600, timeout=1)
    except Exception as e:
        print("GPS 포트 열기 실패:", e)
        return ("NO_MODULE", None)

    line = ser.readline().decode("ascii", errors="ignore").strip()
    if not line:
        return ("NO_FIX", None)

    if line.startswith("$GPGGA"):``
        parts = line.split(",")
        if len(parts) < 15:
            return ("NO_FIX", None)
        fix_status = parts[6]
        if fix_status == "0":
            return ("NO_FIX", None)

        # 위도/경도 변환 (ddmm.mmmm to dd.ddddd)
        def convert(coord, direction):
            if coord == "" or direction == "":
                return None
            degrees = float(coord[:2])
            minutes = float(coord[2:])
            result = degrees + (minutes / 60)
            if direction in ["S", "W"]:
                result = -result
            return round(result, 4)

        lat = convert(parts[2], parts[3])
        lon = convert(parts[4], parts[5])
        utc_time = parts[1]
        # UTC to KST 변환
        if utc_time:
            try:
                hh = int(utc_time[0:2])
                mm = int(utc_time[2:4])
                ss = int(utc_time[4:6])
                utc_dt = datetime(2025, 1, 1, hh, mm, ss, tzinfo=timezone.utc)
                kst_dt = utc_dt.astimezone(timezone(timedelta(hours=9)))
                time_str = kst_dt.strftime("%Y-%m-%d %H:%M:%S")
            except:
                time_str = "시간 오류"
        else:
            time_str = "시간 없음"

        return ("FIX", {"lat": lat, "lon": lon, "time": time_str})

    elif line.startswith("$GPRMC"):
        parts = line.split(",")
        if len(parts) < 12:
            return ("NO_FIX", None)
        status = parts[2]
        if status != "A":
            return ("NO_FIX", None)

        lat = parts[3]
        lat_dir = parts[4]
        lon = parts[5]
        lon_dir = parts[6]
        speed = parts[7]

        def convert(coord, direction):
            if coord == "" or direction == "":
                return None
            degrees = float(coord[:2])
            minutes = float(coord[2:])
            result = degrees + (minutes / 60)
            if direction in ["S", "W"]:
                result = -result
            return round(result, 4)

        lat = convert(lat, lat_dir)
        lon = convert(lon, lon_dir)
        speed_knots = float(speed) if speed else 0
        speed_kmh = round(speed_knots * 1.852, 2)

        utc_time = parts[1]
        if utc_time:
            try:
                hh = int(utc_time[0:2])
                mm = int(utc_time[2:4])
                ss = int(utc_time[4:6])
                utc_dt = datetime(2025, 1, 1, hh, mm, ss, tzinfo=timezone.utc)
                kst_dt = utc_dt.astimezone(timezone(timedelta(hours=9)))
                time_str = kst_dt.strftime("%Y-%m-%d %H:%M:%S")
            except:
                time_str = "시간 오류"
        else:
            time_str = "시간 없음"

        return ("FIX", {"lat": lat, "lon": lon, "speed": speed_kmh, "time": time_str})

    else:
        return ("NO_FIX", None)
