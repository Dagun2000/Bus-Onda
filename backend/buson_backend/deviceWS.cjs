// deviceWS.cjs — attach by path (/device-ws)
const { WebSocketServer } = require('ws');

function attachDeviceWS(server, { onAdminBroadcast } = {}) {
  const wss = new WebSocketServer({ server, path: '/device-ws' });
  const devices = new Map();

  function safeSend(ws, obj) {
    if (ws?.readyState === 1) ws.send(JSON.stringify(obj));
  }

  // deviceType별 리스트 생성
  function list(deviceType = null) {
    return [...devices.values()]
        .filter(d => !deviceType || d.meta?.device_type === deviceType)
        .map(d => ({
          ip: d.meta?.ip,
          id: d.meta?.id,
          busNumber: d.meta?.bus_number ?? null,
          vehicleNumber: d.meta?.vehicle_number ?? null,
          stopId: d.meta?.stop_id ?? null,
          lastSeen: d.lastSeen
        }));
  }

  // type별로 나눠서 Admin으로 전송
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
    const ip = (req.socket && (req.socket.remoteAddress || '')).replace('::ffff:', '');
    let devId = null;

    ws.on('message', (buf) => {
      let msg;
      try {
        msg = JSON.parse(buf.toString());
      } catch {
        return;
      }

      const now = Date.now();
      if (!msg?.type || !msg?.device) return;

      if (msg.device?.id && !devId) {
        devId = msg.device.id;
        devices.set(devId, { ws, meta: { id: devId }, lastSeen: now });
      }

      const d = devices.get(devId);
      if (d) {
        d.lastSeen = now;
        d.meta = {
          ...d.meta,
          ip: msg.device?.ip || ip,

          // ✅ 여기 추가 — device_type을 저장
          device_type: msg.device?.device_type ?? d.meta?.device_type,

          // 기존 필드 유지
          bus_number: msg.payload?.bus_number ?? d.meta?.bus_number,
          vehicle_number: msg.payload?.vehicle_number ?? d.meta?.vehicle_number,
          stop_id: msg.payload?.stop_id ?? d.meta?.stop_id
        };
      }

      // 500ms 미만 반복 패킷 rate-limit (옵션)
      if (d) {
        if (d.lastSent && now - d.lastSent < 400) return; // 너무 빠른 전송 무시
        d.lastSent = now;
      }

      // ACK 응답
      if (msg.msg_id) safeSend(ws, { type: 'ack', ack_id: msg.msg_id, ts: now });

      // 관리자에게 상태 갱신
      if (['hello', 'telemetry', 'event'].includes(msg.type)) {
        broadcastAdminUpdate();
        onAdminBroadcast?.({
          type: 'log',
          line: `[${devId}] ${msg.type} (${msg.device?.device_type ?? '?'}) ${msg.msg_id ?? ''}`
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

      // 명령 형태 보정
      const payload = {
        type: "command",
        cmd: obj.cmd ?? obj.command ?? null,
        data: obj.data ?? {},
      };
      console.log("[WS → Device]", devId, JSON.stringify(payload));

      safeSend(d.ws, payload);
      return true;
    }

  };
}

module.exports = { attachDeviceWS };
