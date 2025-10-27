// deviceWS.cjs — attach by path (/device-ws)
const { WebSocketServer } = require('ws');
const { haversine } = require('./utils/geo.js'); // 거리 계산 유틸 추가
const { updateStatus } = require('./rideRequests.js'); // 선택: 요청 상태 동기화용

function attachDeviceWS(server, { onAdminBroadcast } = {}) {
  const wss = new WebSocketServer({ server, path: '/device-ws' });
  const devices = new Map(); // id -> { ws, meta, lastSeen }

  function safeSend(ws, obj) {
    if (ws?.readyState === 1) ws.send(JSON.stringify(obj));
  }

  // 타입별 목록 생성
  function list(deviceType = null) {
    return [...devices.values()]
      .filter(d => !deviceType || d.meta?.device_type === deviceType)
      .map(d => ({
        ip: d.meta?.ip,
        id: d.meta?.id,
        busNumber: d.meta?.bus_number ?? null,
        vehicleNumber: d.meta?.vehicle_number ?? null,
        stopId: d.meta?.stop_id ?? null,
        lat: d.meta?.lat ?? null,
        lon: d.meta?.lon ?? null,
        lastSeen: d.lastSeen
      }));
  }

  function broadcastAdminUpdate() {
    const types = [
      { code: 1, label: 'phone' },
      { code: 2, label: 'bus' },
      { code: 3, label: 'stop' }
    ];
    for (const t of types) {
      const lst = list(t.code);
      onAdminBroadcast?.({ type: 'connection_update', deviceType: t.label, list: lst });
    }
  }

  wss.on('connection', (ws, req) => {
    const ip = (req.socket?.remoteAddress || '').replace('::ffff:', '');
    let devId = null;

    ws.on('message', (buf) => {
      let msg;
      try { msg = JSON.parse(buf.toString()); } catch { return; }

      const now = Date.now();
      if (!msg?.type || !msg?.device) return;

      // 식별자 초기화
      if (msg.device?.id && !devId) {
        devId = msg.device.id;
        devices.set(devId, { ws, meta: { id: devId }, lastSeen: now });
      }

      const d = devices.get(devId);
      if (!d) return;

      // 메타데이터 갱신
      d.lastSeen = now;
      d.meta = {
        ...d.meta,
        ip,
        device_type: msg.device?.device_type ?? d.meta?.device_type,
        bus_number: msg.payload?.bus_number ?? d.meta?.bus_number,
        vehicle_number: msg.payload?.vehicle_number ?? d.meta?.vehicle_number,
        stop_id: msg.payload?.stop_id ?? d.meta?.stop_id,
      };

      // telemetry일 경우 위치 저장
      if (msg.type === 'telemetry' && msg.payload?.gps) {
        d.meta.lat = msg.payload.gps.lat ?? d.meta.lat;
        d.meta.lon = msg.payload.gps.lon ?? d.meta.lon;
      }

      // 거리 계산 (옵션: 근처 요청 탐색)
      if (msg.type === 'telemetry' && d.meta.lat && d.meta.lon) {
        const { listRequests } = require('./rideRequests.js');
        for (const req of listRequests()) {
          if (req.busNumber === d.meta.bus_number && req.userLocation) {
            const dist = haversine(
              d.meta.lat, d.meta.lon,
              req.userLocation.lat, req.userLocation.lon
            );
            onAdminBroadcast?.({
              type: 'distance_update',
              requestId: req.requestId,
              distance_m: Math.round(dist),
              eta_sec: Math.round(dist / 5),
              bus: { busNumber: d.meta.bus_number },
              ts: new Date().toISOString()
            });
          }
        }
      }

      // ACK
      if (msg.msg_id) safeSend(ws, { type: 'ack', ack_id: msg.msg_id, ts: now });

      // 상태 갱신
      if (['hello', 'telemetry', 'event'].includes(msg.type)) {
        broadcastAdminUpdate();
        onAdminBroadcast?.({
          type: 'log',
          line: `[${devId}] ${msg.type} (${msg.device?.device_type ?? '?'})`
        });
      }
    });

    ws.on('close', () => {
      if (devId) devices.delete(devId);
      broadcastAdminUpdate();
      onAdminBroadcast?.({ type: 'log', line: `[${devId}] 연결 종료` });
    });
  });

  return {
    sendToDevice(devId, obj) {
      const d = devices.get(devId);
      if (!d) return false;
      const payload = {
        type: 'command',
        cmd: obj.cmd ?? obj.command ?? null,
        data: obj.data ?? {},
      };
      console.log('[WS → Device]', devId, JSON.stringify(payload));
      safeSend(d.ws, payload);
      return true;
    },
    list
  };
}

module.exports = { attachDeviceWS };
