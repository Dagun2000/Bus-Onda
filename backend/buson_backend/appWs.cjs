/**
 * appWs.cjs — BusOn 모바일 앱용 WebSocket 서버 (/app-ws)
 * -------------------------------------------------------
 * 역할:
 *  - 앱에서 실시간 거리/상태 업데이트 수신
 *  - 서버에서 푸시: distance_update, ride_rejected, no_show_warning 등
 */

const { WebSocketServer } = require('ws');
const { rideRequests, devicesTelemetry, activeBuses } = require('./appApi.cjs');
const { haversine } = require('./utils/geo.js');
const { haversine, calcDistanceAndETA } = require('./utils/geo.js');

// 연결된 앱 클라이언트 저장
// token -> { ws, deviceId, lastPing }
const appClients = new Map();

/**
 * 앱용 WebSocket 서버 생성기
 */
function attachAppWS(server) {
  const wss = new WebSocketServer({ noServer: true });

  wss.on('connection', (ws, req) => {
    let authed = false;
    let token = null;
    let deviceId = null;

    console.log(`[AppWS] 연결됨: ${req.socket.remoteAddress}`);

    // --- 메시지 수신 ---
    ws.on('message', (buf) => {
      let msg;
      try { msg = JSON.parse(buf.toString()); } catch { return; }

      //인증 메시지
      if (msg.type === 'auth' && msg.token) {
        token = msg.token;
        deviceId = msg.deviceId || token.replace(/^demo-/, '');
        authed = true;

        appClients.set(token, { ws, deviceId, lastPing: Date.now() });
        console.log(`[AppWS] 인증 성공: ${deviceId}`);
        ws.send(JSON.stringify({ type: 'server_info', message: 'Authorized', ts: new Date().toISOString() }));
        return;
      }

      if (!authed) {
        ws.send(JSON.stringify({ type: 'error', message: 'unauthorized' }));
        return;
      }

      //  ping/pong 응답
      if (msg.type === 'pong') {
        const c = appClients.get(token);
        if (c) c.lastPing = Date.now();
      }

      //  기타 ack 메시지
      if (msg.type === 'ack' && msg.eventId) {
        console.log(`[AppWS] ACK from ${deviceId}: ${msg.eventId}`);
      }
    });

    // --- 연결 종료 ---
    ws.on('close', () => {
      if (token) appClients.delete(token);
      console.log(`[AppWS] 연결 종료: ${deviceId}`);
    });
  });

  // --- 거리 업데이트 루프 (2초 간격) ---
  setInterval(() => {
    for (const [token, client] of appClients.entries()) {
      const { ws, deviceId } = client;
      if (ws.readyState !== 1) continue;

      // 현재 요청 찾기
      const req = [...rideRequests.values()].find(r => r.deviceId === deviceId);
      if (!req || !req.userLocation) continue;

      // 해당 노선의 버스 중 가장 가까운 거 찾기
      let nearest = null;
      let minDist = Infinity;
      for (const bus of activeBuses.values()) {
        if (bus.busNumber === req.lineNo && bus.lat && bus.lon) {
          const dist = haversine(bus.lat, bus.lon, req.userLocation.lat, req.userLocation.lon);
          if (dist < minDist) {
            minDist = dist;
            nearest = bus;
          }
        }
      }
        //거리 계산
        if (nearest && isFinite(minDist)) {
        const { distance_m, eta_sec } = calcDistanceAndETA(nearest, req.userLocation);
        ws.send(JSON.stringify({
            type: 'distance_update',
            requestId: req.requestId,
            distance_m,
            eta_sec,
            bus: {
            busNumber: nearest.busNumber,
            vehicleNumber: nearest.vehicleNumber || null,
            },
            ts: new Date().toISOString(),
        }));
        }

    }
  }, 2000);

  // --- 노쇼 감시 (3분동안 승차 이벤트 없을 시) ---
  setInterval(() => {
    const now = Date.now();
    for (const req of rideRequests.values()) {
      if (req.status === 'PENDING' && req.createdAt && now - new Date(req.createdAt).getTime() > 180000) {
        // 대상 앱 찾기
        const target = [...appClients.values()].find(c => c.deviceId === req.deviceId);
        if (target?.ws?.readyState === 1) {
          target.ws.send(JSON.stringify({
            type: 'no_show_warning',
            requestId: req.requestId,
            message: '요청 후 3분 동안 승차 확인 없음',
            ts: new Date().toISOString(),
          }));
          console.log(`[NoShow] ${req.deviceId} (${req.lineNo})`);
        }
      }
    }
  }, 60000);

  return wss;
}

/**
 * 서버에서 앱으로 직접 푸시할 때 사용
 */
function pushToApp(deviceId, payload) {
  for (const { ws, deviceId: d } of appClients.values()) {
    if (d === deviceId && ws.readyState === 1) {
      ws.send(JSON.stringify(payload));
      return true;
    }
  }
  return false;
}

module.exports = { attachAppWS, appClients, pushToApp };
