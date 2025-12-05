// server.cjs
const express = require('express');
const http = require('http');
const path = require('path');
const cors = require('cors');
const { WebSocketServer } = require('ws');
require('dotenv').config();

const app = express();
const PORT = process.env.HTTP_PORT || 3000;
//const adminWSS = new WebSocketServer({ server, path: '/admin-ws' });

app.use(cors());
app.use(express.json());

// Admin 정적
const __root = __dirname;
const adminDir = path.join(__root, 'admin');
app.use('/admin', express.static(adminDir));
app.get('/', (_req, res) => res.redirect('/admin'));

// 디버그: HTTP로 /device-ws 접근하면 426
app.get('/device-ws', (_req, res) => res.status(426).send('Use WebSocket here'));
app.get('/admin-ws', (_req, res) => res.status(426).send('Use WebSocket here'));

const server = http.createServer(app);

// ---------- Admin 클라이언트 브로드캐스트 ----------
const adminClients = new Set();
function adminBroadcast(obj) {
  const data = JSON.stringify(obj);
  for (const ws of adminClients) if (ws.readyState === 1) ws.send(data);
}

// ---------- WS 서버(noServer) ----------
const adminWSS  = new WebSocketServer({ noServer: true });
const mobileWSS = new WebSocketServer({ noServer: true });
const deviceWSS = new WebSocketServer({ noServer: true });
const stopWSS   = new WebSocketServer({ noServer: true });
adminWSS.on('connection', (ws) => {
  console.log('UPGRADE path = /admin-ws'); // ✅ 관리자 WS 업그레이드 확인
  adminClients.add(ws);
  ws.on('close', () => adminClients.delete(ws));
});



// ---------- 단말 WS 공통 처리 ----------
function setupWS(wss, deviceType) {
  const clients = new Map(); // id -> { ws, meta, lastSeen }

  function broadcast() {
    adminBroadcast({
      type: 'connection_update',
      deviceType,
      list: [...clients.values()].map(d => ({
        ip: d.meta?.ip,
        id: d.meta?.id,
        busNumber: d.meta?.bus_number ?? null,
        vehicleNumber: d.meta?.vehicle_number ?? null,
        stopId: d.meta?.stop_id ?? null,
        lastSeen: d.lastSeen,
      })),
    });
  }

  wss.on('connection', (ws, req) => {
    const ip = (req.socket?.remoteAddress || '').replace('::ffff:', '');
    let devId = null;

    ws.on('message', (buf) => {
      let msg; try { msg = JSON.parse(buf.toString()); } catch { return; }
      const now = Date.now();
      if (!msg?.device?.id) return;
      devId ||= msg.device.id;

      clients.set(devId, {
        ws,
        meta: { ...msg.device, ip },
        lastSeen: now,
      });

      ws.send(JSON.stringify({ type: 'ack', ack_id: msg.msg_id ?? null, ts: now }));
      if (['hello', 'telemetry', 'event'].includes(msg.type)) broadcast();
    });

    ws.on('close', () => {
      if (devId) clients.delete(devId);
      broadcast();
    });
  });

  return clients;
}

// 각 WS별로 적용
const mobileClients = setupWS(mobileWSS, 'phone');
const deviceClients = setupWS(deviceWSS, 'bus');
const stopClients   = setupWS(stopWSS, 'stop');



// 수동 업그레이드 라우팅
server.on('upgrade', (req, socket, head) => {
  console.log('UPGRADE path =', req.url);

  if (req.method !== 'GET' ||
      req.headers.upgrade?.toLowerCase() !== 'websocket' ||
      !req.headers['sec-websocket-key']) {
    socket.write('HTTP/1.1 400 Bad Request\r\n\r\nBad Request');
    socket.destroy();
    return;
  }

  // path별 라우팅
  const routeMap = {
    '/admin-ws':  adminWSS,
    '/mobile-ws': mobileWSS,
    '/device-ws': deviceWSS,
    '/busstop-ws': stopWSS,
  };

  const target = routeMap[req.url];
  if (target) {
    target.handleUpgrade(req, socket, head, (ws) => {
      target.emit('connection', ws, req);
    });
  } else {
    socket.write('HTTP/1.1 404 Not Found\r\n\r\nNot Found');
    socket.destroy();
  }
});


// ---------- REST ----------


function getClientMap(type) {
  if (type === 'phone') return mobileClients;
  if (type === 'bus') return deviceClients;
  if (type === 'stop') return stopClients;
  return null;
}

app.get('/api/connections', (req, res) => {
  const map = getClientMap(req.query.type);
  if (!map) return res.json([]);
  res.json([...map.values()].map(d => ({
    ip: d.meta?.ip,
    id: d.meta?.id,
    busNumber: d.meta?.bus_number ?? null,
    vehicleNumber: d.meta?.vehicle_number ?? null,
    stopId: d.meta?.stop_id ?? null,
    lastSeen: d.lastSeen,
  })));
});


// app.get('/api/connections', (_req, res) => {
//   const list = [...devices.values()].map(d => ({
//     ip: d.meta?.ip || null,
//     id: d.meta?.id || null,
//     busNumber: d.meta?.bus_number ?? null,
//     vehicleNumber: d.meta?.vehicle_number ?? null,
//     stopId: d.meta?.stop_id ?? null,
//     lastSeen: d.lastSeen || null,
//   }));
//   res.json(list);
// });
app.post('/api/command', (req, res) => {
  const { targetId, command } = req.body || {};
  const d = devices.get(targetId);
  if (!d || d.ws.readyState !== 1) return res.json({ success: false });
  d.ws.send(JSON.stringify({
    type: 'info', msg_id: `cmd-${Date.now()}`, ts: Date.now(),
    payload: { command, server_time: Date.now() }
  }));
  res.json({ success: true });
});
app.post('/api/run-code', (req, res) => {
  const { targetId, language, code } = req.body || {};
  const d = devices.get(targetId);
  if (!d || d.ws.readyState !== 1) return res.json({ success: false });
  d.ws.send(JSON.stringify({
    type: 'config', msg_id: `run-${Date.now()}`, ts: Date.now(),
    payload: { run: { language, code } }
  }));
  res.json({ success: true });
});
app.get('/api/logs', (req, res) => res.json([]));

server.listen(PORT, () => {
  console.log(`HTTP server on http://localhost:${PORT}`);
  console.log(`Admin WS   → ws://localhost:${PORT}/admin-ws`);
  console.log(`Mobile WS  → ws://localhost:${PORT}/mobile-ws`);
  console.log(`Device WS  → ws://localhost:${PORT}/device-ws`);
  console.log(`Bus Stop WS    → ws://localhost:${PORT}/busstop-ws`);
});
