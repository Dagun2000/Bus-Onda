/**
 * utils/geo.js
 * ----------------------------------------------
 * 지구상 두 좌표(lat/lon) 간의 거리(m 단위)를 계산하는 유틸.
 * - 좌표계: WGS84
 * - 반환 단위: meters (m)
 */

function toRad(deg) {
  return (deg * Math.PI) / 180;
}

/**
 * Haversine 거리 계산
 * @param {number} lat1 위도1
 * @param {number} lon1 경도1
 * @param {number} lat2 위도2
 * @param {number} lon2 경도2
 * @returns {number} 거리 (미터)
 */
function haversine(lat1, lon1, lat2, lon2) {
  const R = 6371e3; // 지구 반지름 (미터)
  const φ1 = toRad(lat1);
  const φ2 = toRad(lat2);
  const Δφ = toRad(lat2 - lat1);
  const Δλ = toRad(lon2 - lon1);

  const a =
    Math.sin(Δφ / 2) ** 2 +
    Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

  return R * c;
}

/**
 * 거리 + ETA 계산 헬퍼
 * (ETA는 기본 속도 5m/s 기준으로 추정)
 */
function calcDistanceAndETA(bus, user) {
  if (!bus?.lat || !user?.lat) return { distance_m: null, eta_sec: null };

  const dist = haversine(bus.lat, bus.lon, user.lat, user.lon);
  const eta = dist / 5; // 5 m/s ≈ 18km/h
  return {
    distance_m: Math.round(dist),
    eta_sec: Math.round(eta),
  };
}

module.exports = { haversine, calcDistanceAndETA };
