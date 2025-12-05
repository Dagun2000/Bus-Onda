// deviceWS.js
import { WebSocketServer } from 'ws';

const { WebSocketServer } = require('ws');

export function attachDeviceWS(server, { onAdminBroadcast }) {
  // 서버에 붙이는 경로가 분리된 리버스프록시가 아니라면:

  const wss = new WebSocketServer({ server, path: '/device-ws' });
  // 포트 분리형이면:
  //const wss = new WebSocketServer({ port: process.env.DEVICE_WS_PORT || 8081 });

  const devices = new Map(); // id -> { ws, meta, lastSeen }

  function safeSend(ws, obj) {
    if (ws?.readyState === 1) ws.send(JSON.stringify(obj));
  }

  function broadcastAdminUpdate() {
    const list = [...devices.values()].map(d => ({
      ip: d.meta?.ip, id: d.meta?.id,
      busNumber: d.meta?.bus_number ?? null,
      vehicleNumber: d.meta?.vehicle_number ?? null,
      lastSeen: d.lastSeen
    }));
    onAdminBroadcast?.({
      type: 'connection_update', deviceType: 'bus', list
    });
  }

  wss.on('connection', (ws, req) => {
    let devId = null;

    ws.on('message', (buf) => {
      let msg; try { msg = JSON.parse(buf.toString()); } catch { return; }
      const now = Date.now();

      // 간단 검증
      if (!msg?.type || !msg?.device) return;

      if (msg.device?.id && !devId) {
        devId = msg.device.id;
        devices.set(devId, { ws, meta: { id: devId }, lastSeen: now });
      }

      // 메타 업데이트
      const d = devices.get(devId);
      if (d) {
        d.lastSeen = now;
        d.meta = { ...d.meta, ip: msg.device?.ip, bus_number: msg.payload?.bus_number ?? d.meta?.bus_number,
          vehicle_number: msg.payload?.vehicle_number ?? d.meta?.vehicle_number };
      }

      // ACK
      if (msg.msg_id) safeSend(ws, { type: 'ack', ack_id: msg.msg_id, ts: now });

      // 텔레메트리 과속 제어 (optional)
      if (msg.type === 'telemetry') {
        // 예: 500ms 이상 간격 권장, 너무 빠르면 slow_down
        // (필요시 주석 해제)
        // safeSend(ws, { type:'slow_down', msg_id:`sd-${now}`, ts:now, payload:{ interval_ms: 500 } });
      }

      // Admin으로 브로드캐스트(리스트/로그)
      if (['hello','telemetry','event'].includes(msg.type)) {
        broadcastAdminUpdate();
        onAdminBroadcast?.({ type:'log', line:`[${devId}] ${msg.type} ${msg.msg_id ?? ''}` });
      }

      // ping → 여기서는 서버가 보낼 것. 파이는 pong
    });

    ws.on('close', () => {
      if (devId) devices.delete(devId);
      broadcastAdminUpdate();
      onAdminBroadcast?.({ type:'log', line:`[${devId}] 연결 종료` });
    });
  });

  return {
    sendToDevice(devId, obj) {
      const d = devices.get(devId);
      if (!d) return false;
      safeSend(d.ws, obj);
      return true;
    },
    list() {
      return [...devices.values()].map(d => ({
        ip: d.meta?.ip, id: d.meta?.id, busNumber: d.meta?.bus_number ?? null,
        vehicleNumber: d.meta?.vehicle_number ?? null, lastSeen: d.lastSeen
      }));
    }
  };
}
module.exports = { attachDeviceWS };

