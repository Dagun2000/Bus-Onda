// server.js - All-in-one (Admin UI + REST API + WS[admin/device])
const express = require('express');
const http = require('http');
const path = require('path');
const cors = require('cors');
const { WebSocketServer } = require('ws');
require('dotenv').config();

const app = express();
const PORT = process.env.HTTP_PORT || 3000;

app.use(cors());
app.use(express.json());

// ---------- Admin 정적 페이지 서빙 ----------
const __root = __dirname; // CommonJS라 사용 가능
const adminDir = path.join(__root, 'admin');

// /admin 하위 정적 파일 제공
app.use('/admin', express.static(adminDir));

// 루트 접근 시 /admin으로 안내
app.get('/', (_req, res) => res.redirect('/admin'));

// ---------- 메모리 상태 저장소 ----------
const logs = [];
function log(line) {
  const msg = `${new Date().toISOString()} ${line}`;
  logs.push(msg);
  if (logs.length > 2000) logs.shift();
  // 관리자 클라이언트에 브로드캐스트
  adminBroadcast({ type: 'log', line: msg });
}

// 연결된 디바이스 (라즈베리파이/ESP…)
const devices = new Map(); // id -> { ws, meta: {id, ip, bus_number, vehicle_number}, lastSeen }

// ---------- HTTP API (Admin UI에서 사용) ----------
app.get('/api/connections', (req, res) => {
  // type=phone|bus|stop → 현재 템플릿은 모두 devices로 통합해서 반환
  const type = req.query.type; // 지금은 미사용. 필요 시 필터링 로직 추가
  const list = [...devices.values()].map(d => ({
    ip: d.meta?.ip || null,
    id: d.meta?.id || null,
    busNumber: d.meta?.bus_number ?? null,
    vehicleNumber: d.meta?.vehicle_number ?? null,
    stopId: d.meta?.stop_id ?? null,
    lastSeen: d.lastSeen || null,
  }));
  return res.json(list);
});

app.post('/api/command', (req, res) => {
  const { targetType, targetId, command } = req.body || {};
  const ok = sendToDevice(targetId, {
    type: 'info',
    msg_id: `cmd-${Date.now()}`,
    ts: Date.now(),
    payload: { command, server_time: Date.now() }
  });
  if (ok) log(`[ADMIN] command → ${targetId}: ${command}`);
  return res.json({ success: !!ok });
});

app.post('/api/run-code', (req, res) => {
  const { targetType, targetId, language, code } = req.body || {};
  const ok = sendToDevice(targetId, {
    type: 'config',
    msg_id: `run-${Date.now()}`,
    ts: Date.now(),
    payload: { run: { language, code } }
  });
  if (ok) log(`[ADMIN] run-code → ${targetId} (${language})`);
  return res.json({ success: !!ok });
});

app.get('/api/logs', (req, res) => {
  const limit = Math.max(1, Math.min(2000, parseInt(req.query.limit || '200', 10)));
  return res.json(logs.slice(-limit));
});

// ---------- HTTP 서버 + WebSocket 서버 ----------
const server = http.createServer(app);

// 관리자 대시보드용 WS (브라우저에서 접속)
// 클라이언트 JS에서 ws://<host>/admin-ws 로 연결하세요.
const adminWSS = new WebSocketServer({ server, path: '/admin-ws' });
const adminClients = new Set();

adminWSS.on('connection', (ws) => {
  adminClients.add(ws);
  log('[ADMIN WS] connected');
  ws.on('close', () => {
    adminClients.delete(ws);
    log('[ADMIN WS] disconnected');
  });
});

function adminBroadcast(obj) {
  const data = JSON.stringify(obj);
  for (const ws of adminClients) {
    if (ws.readyState === 1) ws.send(data);
  }
}

// 디바이스(라즈베리파이/ESP 등)용 WS
// 파이 클라이언트를 ws://<host>/device-ws 로 연결
const deviceWSS = new WebSocketServer({ server, path: '/device-ws' });

deviceWSS.on('connection', (ws, req) => {
  // 접속 시점의 IP 추정(프록시없다고 가정)
  const ip = (req.socket && (req.socket.remoteAddress || '')).replace('::ffff:', '');
  let devId = null;

  ws.on('message', (buf) => {
    let msg; try { msg = JSON.parse(buf.toString()); } catch { return; }
    const now = Date.now();
    if (!msg?.type || !msg?.device) return;

    // 최초 아이디 고정
    if (!devId && msg.device?.id) devId = msg.device.id;

    // 등록/갱신
    const prev = devices.get(devId) || {};
    const meta = {
      ...(prev.meta || {}),
      id: msg.device?.id || prev.meta?.id || devId,
      ip: msg.device?.ip || ip || prev.meta?.ip || null,
      bus_number: msg.payload?.bus_number ?? prev.meta?.bus_number ?? null,
      vehicle_number: msg.payload?.vehicle_number ?? prev.meta?.vehicle_number ?? null,
      stop_id: msg.payload?.stop_id ?? prev.meta?.stop_id ?? null,
    };
    devices.set(devId, { ws, meta, lastSeen: now });

    // ACK
    if (msg.msg_id && ws.readyState === 1) {
      ws.send(JSON.stringify({ type: 'ack', ack_id: msg.msg_id, ts: now }));
    }

    // 관리 페이지 실시간 갱신
    if (['hello','telemetry','event'].includes(msg.type)) {
      adminBroadcast({
        type: 'connection_update',
        deviceType: 'bus', // 필요 시 phone/stop 구분 로직 추가
        list: [...devices.values()].map(d => ({
          ip: d.meta?.ip || null,
          id: d.meta?.id || null,
          busNumber: d.meta?.bus_number ?? null,
          vehicleNumber: d.meta?.vehicle_number ?? null,
          lastSeen: d.lastSeen || null
        }))
      });
      log(`[DEV ${devId}] ${msg.type} ${msg.msg_id || ''}`);
    }
  });

  ws.on('close', () => {
    if (devId) {
      devices.delete(devId);
      adminBroadcast({
        type: 'connection_update',
        deviceType: 'bus',
        list: [...devices.values()].map(d => ({
          ip: d.meta?.ip || null,
          id: d.meta?.id || null,
          busNumber: d.meta?.bus_number ?? null,
          vehicleNumber: d.meta?.vehicle_number ?? null,
          lastSeen: d.lastSeen || null
        }))
      });
      log(`[DEV ${devId}] disconnected`);
    }
  });
});

function sendToDevice(devId, obj) {
  const d = devices.get(devId);
  if (!d || !d.ws || d.ws.readyState !== 1) return false;
  d.ws.send(JSON.stringify(obj));
  return true;
}

server.listen(PORT, () => {
  console.log(`🌐 HTTP server on http://localhost:${PORT}`);
  console.log(`📦 Admin UI      → http://localhost:${PORT}/admin`);
  console.log(`🔌 Admin WS      → ws://localhost:${PORT}/admin-ws`);
  console.log(`🔌 Device WS     → ws://localhost:${PORT}/device-ws`);
});
