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
const phoneClients = new Map();

module.exports.phoneClients = phoneClients;

const { pushToApp } = require('./mobile/appWs.cjs');

const app = express();
const PORT = process.env.HTTP_PORT || 3000;
//const adminWSS = new WebSocketServer({ server, path: '/admin-ws' });
app.use(cors());
app.use(express.json());

const { router: appApi } = require('./mobile/appApi.cjs');
app.use('/api/v1', appApi);
const { appClients } = require('./mobile/appWs.cjs');


// Admin 정적
const __root = __dirname;
const adminDir = path.join(__root, 'admin');
app.use('/admin', express.static(adminDir));

app.use((req, res, next) => {
  console.log(`---- [REQ] ${req.method} ${req.url}`);
  console.log(req.body);
  next();
});
// ========== GLOBAL REQUEST LOGGER ==========
app.use((req, res, next) => {

  //  특정 URL은 로그 제외
  // if (req.url.startsWith('/api/connections')) {
  //   return next();
  // }

  console.log("\n==============================");
  console.log(`HTTP ${req.method} ${req.url}`);
  console.log("Headers:", req.headers);
  console.log("Body:", req.body);

  const oldJson = res.json;
  res.json = function (data) {
    console.log("Response:", data);
    return oldJson.call(this, data);
  };

  next();
});



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
      let msg;
      try {
        msg = JSON.parse(buf.toString());
      } catch {
        return;
      }
      const now = Date.now();

      //  구형 deviceWS와 동일하게: type + device만 있으면 처리
      if (!msg?.type || !msg?.device) return;

      // 최초 한 번 id 세팅 + 등록
      if (msg.device?.id && !devId) {
        devId = msg.device.id;
        clients.set(devId, { ws, meta: { id: devId }, lastSeen: now });
      }

      const d = clients.get(devId);
      if (!d) return;

      //  메타 업데이트 (구형 로직 반영)
      d.lastSeen = now;
      d.meta = {
        ...d.meta,
        ip: msg.device?.ip || ip,
        bus_number: msg.payload?.bus_number ?? d.meta.bus_number,
        vehicle_number: msg.payload?.vehicle_number ?? d.meta.vehicle_number,
        stop_id: msg.payload?.stop_id ?? d.meta.stop_id,
      };

      // (옵션) telemetry일 때 GPS도 저장
      if (msg.type === 'telemetry' && msg.payload?.gps) {
        d.meta.lat = msg.payload.gps.lat ?? d.meta.lat;
        d.meta.lon = msg.payload.gps.lon ?? d.meta.lon;
      }

      //  ACK 응답
      if (msg.msg_id) {
        ws.send(JSON.stringify({ type: 'ack', ack_id: msg.msg_id, ts: now }));
      }

      //  관리자 브로드캐스트
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
attachAppWS(mobileWSS, { phoneClients });

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


// function getClientMap(type) {
//   if (type === 'phone') return phoneClients; 
//   if (type === 'bus')   return deviceClients;
//   if (type === 'stop')  return stopClients;
//   return null;
// }

function getClientMap(type) {
  switch (type) {
    case 'bus':
      return deviceClients;
    case 'stop':
      return stopClients;
    case 'phone':
      return phoneClients;   // 여기서 phone은 phoneClients를 쓰도록
    default:
      return null;
  }
}


app.get('/api/connections', (req, res) => {
  const type = req.query.type;
  const map = getClientMap(type);
  if (!map) return res.json([]);

  if (type === 'phone') {
    // phoneClients: { id, ip, ws?, lat, lon, requestBus, boardCount, ... }
    return res.json([...map.values()].map(p => ({
      ip:          p.ip ?? null,
      id:          p.id ?? null,
      lat:         p.lat ?? null,
      lon:         p.lon ?? null,
      requestBus:  p.requestBus ?? '-',
      boardCount:  p.boardCount ?? 0,
      alightCount: p.alightCount ?? 0,
      cancelCount: p.cancelCount ?? 0,
      lastSeen:    p.lastSeen ?? null,
    })));
  }

  // bus / stop 은 setupWS에서 넣어준 meta 사용
  return res.json([...map.values()].map(d => ({
    ip:            d.meta?.ip,
    id:            d.meta?.id,
    busNumber:     d.meta?.bus_number ?? null,
    vehicleNumber: d.meta?.vehicle_number ?? null,
    stopId:        d.meta?.stop_id ?? null,
    lastSeen:      d.lastSeen,
  })));
});


app.post('/api/force/bus_nearby', (req, res) => {
  const { deviceId, distance } = req.body;

  const ok = pushToApp(deviceId, {
    type: "bus_nearby",
    distance_m: distance ?? 12
  });

  res.json({ success: ok });
});

app.post('/api/force/bus_arrived', (req, res) => {
  const { deviceId, distance } = req.body;

  const ok = pushToApp(deviceId, {
    type: "bus_arrived",
    distance_m: distance ?? 5
  });

  return res.json({ success: ok });
});


// ping (GET 방식 기존)
app.get('/api/v1/ping', (req, res) => {
  res.json({
    success: true,
    data: { uptimeSec: process.uptime(), version: "1.0.0" },
    serverTime: new Date().toISOString()
  });
});

// ping (POST 방식 추가)
app.post('/api/v1/ping', (req, res) => {
  res.json({
    success: true,
    data: { uptimeSec: process.uptime(), version: "1.0.0" },
    serverTime: new Date().toISOString()
  });
});

app.post('/api/command', (req, res) => {
  const { targetType, targetId, command } = req.body || {};
  console.log('[/api/command] targetType =', targetType, 'targetId =', targetId);

  console.log('[DEBUG] phoneClients keys =', Array.from(phoneClients.keys()));
  console.log('[DEBUG] appClients   keys =', Array.from(appClients.keys()));

  if (targetType === 'phone') {
    // 근접/도착 알림 두 개는 특수 처리
    if (command === 'bus_nearby' || command === 'bus_arrived') {
      const ok = pushToApp(targetId, {
        type: command,          // "bus_nearby" 또는 "bus_arrived"
        distance_m: 5           // 필요하면 UI에서 조정 가능
      });
      return res.json({ success: ok });
    }

    // 그 외 command는 아직 미지원
    return res.json({ success: false, reason: 'Unsupported command for phone' });
  }

  // ---- 2) phone 이 아닌 경우는 기존 로직 (버스/정류장 단말) ----
  let ws = null;

  const map = getClientMap(targetType);
  console.log('[DEBUG] using map =', targetType, 'size =', map ? map.size : null);
  if (!map) return res.json({ success: false, reason: 'Invalid type' });

  const d = map.get(targetId);
  if (!d) {
    return res.json({ success: false, reason: 'Not connected (no such client)' });
  }
  ws = d.ws;

  if (!ws || ws.readyState !== 1) {
    return res.json({ success: false, reason: 'Not connected (ws closed)' });
  }

  ws.send(JSON.stringify({
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

// // --------------------------------------------
// //  /mobile-ws : BusOn 모바일 앱 실시간 채널
// // --------------------------------------------
// const mobileClients = new Map();          // clientId -> { ws, token, lastSeen }
// const mobileSubscriptions = new Map();    // clientId -> Set(busNumbers)

// // 모바일에 메시지 안전 전송
// function pushToMobile(clientId, payload) {
//   const entry = mobileClients.get(clientId);
//   if (entry && entry.ws.readyState === 1) {
//     entry.ws.send(JSON.stringify(payload));
//   }
// }

// // 특정 버스 구독 중인 모든 모바일에 브로드캐스트
// function broadcastToBusSubscribers(busNumber, payload) {
//   for (const [cid, subs] of mobileSubscriptions.entries()) {
//     if (subs.has(busNumber)) pushToMobile(cid, payload);
//   }
// }


// //  연결 핸들러
// mobileWSS.on('connection', (ws, req) => {
//   const ip = req.socket.remoteAddress;
//   const clientId = `m-${Date.now()}-${Math.floor(Math.random() * 9999)}`;
//   let deviceId = null;

//   mobileClients.set(clientId, { ws, deviceId: null, lastSeen: Date.now() });
//   mobileSubscriptions.set(clientId, new Set());

//   console.log(`[MOBILE] connected ${clientId} from ${ip}`);

//   ws.on('message', (buf) => {
//     let msg;
//     try { msg = JSON.parse(buf.toString()); } catch { return; }
//     const now = Date.now();

//     // ==========================
//     // 1) 인증(auth)
//     // ==========================
//     if (msg.type === 'auth') {
//       if (!msg.deviceId) {
//         ws.send(JSON.stringify({ type: 'auth_ack', ok: false, reason: 'deviceId required' }));
//         console.log(`[MOBILE] auth failed (no deviceId) clientId=${clientId}`);
//         return;
//       }

//       deviceId = msg.deviceId;

//       // mobileClients 갱신
//       const entry = mobileClients.get(clientId);
//       if (entry) {
//         entry.deviceId = deviceId;
//         entry.lastSeen = now;
//       }

//       // ===== 휴대단말 목록 저장 =====
//       phoneClients.set(deviceId, {
//         id: deviceId,
//         ip,
//         ws,
//         lastSeen: now,
//         lat: null,
//         lon: null,
//         requestBus: null,
//         boardCount: 0,
//         alightCount: 0,
//         cancelCount: 0
//       });

//       ws.send(JSON.stringify({ type: 'auth_ack', ok: true, deviceId, ts: now }));
//       console.log(`[MOBILE] ${clientId} authenticated as ${deviceId}`);
//       return;
//     }

//     // ==========================
//     // 2) telemetry 갱신
//     // {type:'telemetry', lat: xxx, lon: yyy }
//     // ==========================
//     if (msg.type === 'telemetry') {
//       if (!deviceId) return; // 인증 안됨
//       const p = phoneClients.get(deviceId);
//       if (p) {
//         p.lat = msg.lat ?? p.lat;
//         p.lon = msg.lon ?? p.lon;
//         p.lastSeen = now;
//       }
//       return;
//     }

//     // ==========================
//     // 3) subscribe
//     // ==========================
//     if (msg.type === 'subscribe' && msg.busNumber) {
//       const subs = mobileSubscriptions.get(clientId);
//       subs.add(msg.busNumber);
//       ws.send(JSON.stringify({ type: 'subscribed', busNumber: msg.busNumber, ts: now }));
//       console.log(`[MOBILE] ${clientId} subscribed bus ${msg.busNumber}`);
//       return;
//     }

//     // ==========================
//     // 4) unsubscribe
//     // ==========================
//     if (msg.type === 'unsubscribe' && msg.busNumber) {
//       const subs = mobileSubscriptions.get(clientId);
//       subs.delete(msg.busNumber);
//       ws.send(JSON.stringify({ type: 'unsubscribed', busNumber: msg.busNumber, ts: now }));
//       return;
//     }

//     // ==========================
//     // 5) pong
//     // ==========================
//     if (msg.type === 'pong') {
//       const entry = mobileClients.get(clientId);
//       if (entry) entry.lastSeen = now;

//       // phoneClients도 갱신
//       if (deviceId) {
//         const p = phoneClients.get(deviceId);
//         if (p) p.lastSeen = now;
//       }
//       return;
//     }
//   });

//   ws.on('close', () => {
//     console.log(`[MOBILE] disconnected ${clientId}`);
//     mobileClients.delete(clientId);
//     mobileSubscriptions.delete(clientId);

//     if (deviceId && phoneClients.has(deviceId)) {
//       phoneClients.delete(deviceId);
//     }
//   });
// });

module.exports = {
  deviceClients,
  stopClients,
  phoneClients,
  activeBuses,
};


setInterval(() => {
  console.log("=== AppClients keys:", Array.from(require('./mobile/appWs.cjs').appClients.keys()));
}, 3000);

setTimeout(() => {
  console.log("Sending test msg...");
  pushToApp("android-844dbf09", {
    type: "bus_nearby",
    distance_m: 8,
    message: "테스트",
    ts: new Date().toISOString()
  });
}, 5000);


server.listen(PORT, () => {
  console.log(`HTTP server on http://localhost:${PORT}`);
  console.log(`Admin WS   → ws://localhost:${PORT}/admin-ws`);
  console.log(`Mobile WS  → ws://localhost:${PORT}/mobile-ws`);
  console.log(`Device WS  → ws://localhost:${PORT}/device-ws`);
  console.log(`Bus Stop WS    → ws://localhost:${PORT}/busstop-ws`);
});
