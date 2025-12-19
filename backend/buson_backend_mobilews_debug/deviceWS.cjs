// deviceWS.cjs — attach by path (/device-ws)
const { WebSocketServer } = require('ws');

function attachDeviceWS(server, { onAdminBroadcast } = {}) {
  const wss = new WebSocketServer({ server, path: '/device-ws' });
  const devices = new Map();

  function safeSend(ws, obj) { if (ws?.readyState === 1) ws.send(JSON.stringify(obj)); }
  function list() {
    return [...devices.values()].map(d => ({
      ip: d.meta?.ip, id: d.meta?.id,
      busNumber: d.meta?.bus_number ?? null,
      vehicleNumber: d.meta?.vehicle_number ?? null,
      lastSeen: d.lastSeen
    }));
  }
  function broadcastAdminUpdate() {
    onAdminBroadcast?.({ type:'connection_update', deviceType:'bus', list: list() });
  }

  wss.on('connection', (ws, req) => {
    const ip = (req.socket && (req.socket.remoteAddress || '')).replace('::ffff:', '');
    let devId = null;

    ws.on('message', (buf) => {
      let msg; try { msg = JSON.parse(buf.toString()); } catch { return; }
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
          bus_number: msg.payload?.bus_number ?? d.meta?.bus_number,
          vehicle_number: msg.payload?.vehicle_number ?? d.meta?.vehicle_number
        };
      }

      if (msg.msg_id) safeSend(ws, { type:'ack', ack_id: msg.msg_id, ts: now });
      



      if (['hello','telemetry','event'].includes(msg.type)) {
        broadcastAdminUpdate();
        onAdminBroadcast?.({ type:'log', line:`[${devId}] ${msg.type} ${msg.msg_id ?? ''}` });
      }
    });

      if (msg.type === 'ride_response') {
      // 단말기에서 승인/거절 신호 보냄
      onAdminBroadcast?.({
        type: 'ride_response',
        payload: msg.payload,
      });

      // ack 보내주기
      if (msg.msg_id) safeSend(ws, { type: 'ack', ack_id: msg.msg_id, ts: now });
      return;
    }

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
    list
  };
}




module.exports = { attachDeviceWS };
