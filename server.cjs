// server.cjs — All-in-one (noServer 라우팅)
const express = require('express');
const http = require('http');
const path = require('path');
const cors = require('cors');
const { WebSocketServer } = require('ws');
require('dotenv').config();
const { attachDeviceWS } = require('./deviceWS.cjs');
const { rideRequests } = require('./models/rideRequests.cjs');
// const { pushToApp } = require('./mobile/appWs.cjs');
// const { pushToApp } = require('../mobile/appWs.cjs');
const { activeBuses } = require('./mobile/appApi.cjs');

const app = express();
const PORT = process.env.HTTP_PORT || 3000;
//const adminWSS = new WebSocketServer({ server, path: '/admin-ws' });
app.use(cors());
app.use(express.json());

const { router: appApi } = require('./mobile/appApi.cjs');
app.use('/api/v1', appApi);


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


adminWSS.on('connection', (ws, req) => {
  try {
    console.log(`[ADMIN-WS] Connected: ${req.socket.remoteAddress}`);
    ws.send(JSON.stringify({ type: 'welcome', msg: 'admin connected' }));

    // 서버 → 클라이언트 주기적 ping
    const pingTimer = setInterval(() => {
      if (ws.readyState === 1) {
        ws.send(JSON.stringify({ type: 'ping', ts: Date.now() }));
      }
    }, 15000);

    // 클라이언트 → 서버 pong 응답 or 기타 메시지
    ws.on('message', (data) => {
      let msg;
      try { msg = JSON.parse(data); } catch { return; }

      if (msg.type === 'pong') {
        // pong 수신 로그
        console.log(`[ADMIN-WS] pong from admin`);
        return;
      }

      console.log('[ADMIN-WS message]', msg);
    });

    ws.on('close', () => {
      clearInterval(pingTimer);
      console.log('[ADMIN-WS] Closed');
    });
  } catch (err) {
    console.error('[ADMIN-WS ERROR]', err);
  }
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

      if (deviceType === 'bus') console.log('[DEBUG] from bus:', msg);

      if (!msg?.device?.id) return;
      devId ||= msg.device.id;

      // 상태 갱신
      clients.set(devId, {
        ws,
        meta: {
          ...msg.device,
          ip,
          ...(deviceType === 'bus' ? {
            bus_number: msg.payload?.bus_number ?? null,
            vehicle_number: msg.payload?.vehicle_number ?? null,
            stop_id: msg.payload?.stop_id ?? null,
          } : {}),
        },
        lastSeen: now,
      });

      // ack 먼저
      if (msg.msg_id) {
        ws.send(JSON.stringify({ type: 'ack', ack_id: msg.msg_id, ts: now }));
      }
      
      if (deviceType === 'bus') {
        const p = msg.payload?.position || msg.position;
        if (p?.lat && p?.lon) {
          const meta = clients.get(devId)?.meta || {};
          activeBuses.set(devId, {
            lat: p.lat,
            lon: p.lon,
            busNumber: meta.bus_number || msg.payload?.bus_number || null,
            vehicleNumber: meta.vehicle_number || msg.payload?.vehicle_number || null,
            ws,
          });
        }
      }

      // server.cjs - setupWS 내부 ride_response 분기 안
      if (deviceType === 'bus' && msg.type === 'ride_response') {
        const { requestId, decision } = msg.payload || {};
        const r = require('./models/rideRequests.cjs').rideRequests.get(requestId);
        if (r) {
          // 자동승인 타이머 정리
          if (r.autoApproveTimer) { clearTimeout(r.autoApproveTimer); r.autoApproveTimer = null; }

          r.status = (decision === 'accepted') ? 'ACCEPTED' : 'REJECTED';
          require('./mobile/appWs.cjs').pushToApp(r.deviceId, {
            type: (decision === 'accepted') ? 'ride_accepted' : 'ride_rejected',
            requestId,
            auto: false,
            ts: new Date().toISOString(),
            message: (decision === 'accepted') ? '버스가 승차 요청을 승인했습니다.' : '버스가 승차 요청을 거절했습니다.',
          });
        }
        adminBroadcast({ type: 'ride_response', payload: { requestId, decision, deviceId: devId }});
        return;
      }


      // ③ 공통 브로드캐스트
      if (['hello', 'telemetry', 'event'].includes(msg.type)) {
        broadcast();
      }
    });

    ws.on('close', () => {
      if (devId) clients.delete(devId);
      broadcast();
    });
  });


  return clients;
}

// 각 WS별로 적용
// const mobileClients = setupWS(mobileWSS, 'phone'); //그냥 디버그용 구버전
const deviceClients = setupWS(deviceWSS, 'bus');
const stopClients   = setupWS(stopWSS, 'stop');

const { attachAppWS } = require('./mobile/appWs.cjs');
attachAppWS(mobileWSS, { deviceClients, stopClients });

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



app.post('/api/command', (req, res) => {
  const { targetType, targetId, command } = req.body || {};
  const map = getClientMap(targetType);
  if (!map) return res.json({ success: false, reason: 'Invalid type' });

  const d = map.get(targetId);
  if (!d || d.ws.readyState !== 1)
    return res.json({ success: false, reason: 'Not connected' });

  d.ws.send(JSON.stringify({
    type: 'command',
    cmd: command,
    ts: Date.now(),
    payload: { server_time: Date.now() }
  }));
  res.json({ success: true });
});

app.post('/api/run-code', (req, res) => {
  const { targetId, language, code } = req.body || {};
  const d = deviceClients.get(targetId);
  if (!d || d.ws.readyState !== 1) return res.json({ success: false });
  d.ws.send(JSON.stringify({
    type: 'config', msg_id: `run-${Date.now()}`, ts: Date.now(),
    payload: { run: { language, code } }
  }));
  res.json({ success: true });
});

app.get('/api/logs', (req, res) => res.json([]));


// // ----------------------
// // /device-ws (버스 단말기 채널)
// // ----------------------
// const { sendToDevice, list: listDevices } = attachDeviceWS(server, {
//   onAdminBroadcast: (msg) => {
//     // 관리자 콘솔에 상태 갱신
//     if (msg.type === 'connection_update')
//       adminBroadcast(msg);

//     // 단말기에서 ride_response 수신 처리
//     if (msg.type === 'ride_response') {
//       const { requestId, decision } = msg.payload || {};
//       const req = rideRequests.get(requestId);
//       if (!req) return;

//       if (decision === 'accepted') {
//         req.status = 'ACCEPTED';
//         pushToApp(req.deviceId, {
//           type: 'ride_accepted',
//           requestId,
//           message: '버스가 승차 요청을 승인했습니다.',
//           ts: new Date().toISOString()
//         });
//       } else if (decision === 'rejected') {
//         req.status = 'REJECTED';
//         pushToApp(req.deviceId, {
//           type: 'ride_rejected',
//           requestId,
//           message: '버스가 승차 요청을 거절했습니다.',
//           ts: new Date().toISOString()
//         });
//       }
//     }
//   }
// });

// --------------------------------------------
//  /mobile-ws : BusOn 모바일 앱 실시간 채널
// --------------------------------------------
const mobileClients = new Map();          // clientId -> { ws, token, lastSeen }
const mobileSubscriptions = new Map();    // clientId -> Set(busNumbers)

// 모바일에 메시지 안전 전송
function pushToMobile(clientId, payload) {
  const entry = mobileClients.get(clientId);
  if (entry && entry.ws.readyState === 1) {
    entry.ws.send(JSON.stringify(payload));
  }
}

// 특정 버스 구독 중인 모든 모바일에 브로드캐스트
function broadcastToBusSubscribers(busNumber, payload) {
  for (const [cid, subs] of mobileSubscriptions.entries()) {
    if (subs.has(busNumber)) pushToMobile(cid, payload);
  }
}

//  연결 핸들러
mobileWSS.on('connection', (ws, req) => {
  const clientId = `m-${Date.now()}-${Math.floor(Math.random() * 9999)}`;
  mobileClients.set(clientId, { ws, token: null, lastSeen: Date.now() });
  mobileSubscriptions.set(clientId, new Set());
  const fs = require('fs');
  const devicesFile = path.join(__dirname, 'mobile', 'data', 'devices.json');
  function loadDevices() {
    try {
      return JSON.parse(fs.readFileSync(devicesFile, 'utf8'));
    } catch { return {}; }
  }
  console.log(`[MOBILE] connected ${clientId}`);


  ws.on('message', (buf) => {
    let msg;
    try { msg = JSON.parse(buf.toString()); } catch { return; }
    const now = Date.now();

    // 1 인증
    if (msg.type === 'auth') {
      const devices = loadDevices();
      const valid = Object.values(devices).some(d => d.token === msg.token);

      if (!valid) {
        ws.send(JSON.stringify({ type: 'auth_ack', ok: false, reason: 'invalid_token' }));
        ws.close();
        console.log(`[MOBILE] auth failed`);
        return;
      }

      mobileClients.get(clientId).token = msg.token;
      ws.send(JSON.stringify({ type: 'auth_ack', ok: true, ts: now }));
      console.log(`[MOBILE] ${clientId} authenticated`);
      return;
    }

    // 2 구독 요청
    if (msg.type === 'subscribe' && msg.busNumber) {
      const subs = mobileSubscriptions.get(clientId);
      subs.add(msg.busNumber);
      ws.send(JSON.stringify({ type: 'subscribed', busNumber: msg.busNumber, ts: now }));
      console.log(`[MOBILE] ${clientId} subscribed bus ${msg.busNumber}`);
      return;
    }

    // 3 구독 해제
    if (msg.type === 'unsubscribe' && msg.busNumber) {
      const subs = mobileSubscriptions.get(clientId);
      subs.delete(msg.busNumber);
      ws.send(JSON.stringify({ type: 'unsubscribed', busNumber: msg.busNumber, ts: now }));
      return;
    }

    // 4 ping/pong 처리
    if (msg.type === 'pong') {
      mobileClients.get(clientId).lastSeen = now;
      return;
    }
  });

  ws.on('close', () => {
    mobileClients.delete(clientId);
    mobileSubscriptions.delete(clientId);
    console.log(`[MOBILE] disconnected ${clientId}`);
  });
});


server.listen(PORT, () => {
  console.log(`HTTP server on http://localhost:${PORT}`);
  console.log(`Admin WS   → ws://localhost:${PORT}/admin-ws`);
  console.log(`Mobile WS  → ws://localhost:${PORT}/mobile-ws`);
  console.log(`Device WS  → ws://localhost:${PORT}/device-ws`);
  console.log(`Bus Stop WS    → ws://localhost:${PORT}/busstop-ws`);
});
