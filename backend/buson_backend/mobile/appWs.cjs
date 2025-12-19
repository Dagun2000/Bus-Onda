// appWs.cjs

// const { WebSocketServer } = require('ws'); // 이제는 안 써도 됨(남겨놔도 상관 없음)
const { rideRequests } = require('../models/rideRequests.cjs');
const { haversine, calcDistanceAndETA } = require('../utils/geo.cjs');
const { devicesTelemetry, activeBuses } = require('./appApi.cjs');

// deviceId -> { ws, deviceId, lastPing }
const appClients = new Map();

/**
 * server.cjs 에서 만든 mobileWSS 에 붙이는 함수
 *   const { attachAppWS } = require('./mobile/appWs.cjs');
 *   attachAppWS(mobileWSS);
 */
function attachAppWS(wss /*, deps */) {
    wss.on('connection', (ws, req) => {
        let deviceId = null;

        console.log(`[AppWS] 연결됨: ${req.socket.remoteAddress}`);

        ws.on('message', (buf) => {
            let msg;
            try {
                msg = JSON.parse(buf.toString());
            } catch {
                console.log('[AppWS] invalid JSON:', buf.toString());
                return;
            }

            // 1) deviceId 자동 추출 (auth 유무 상관 없이 최대한 맞춰줌)
            if (!deviceId) {
                if (msg.deviceId) deviceId = msg.deviceId;
                else if (msg.device?.id) deviceId = msg.device.id;
            }

            // 옛 스펙의 {type:'auth', deviceId} 도 같이 지원
            if (msg.type === 'auth' && msg.deviceId) {
                deviceId = msg.deviceId;
            }

            if (deviceId) {
                const entry = appClients.get(deviceId);
                if (!entry || entry.ws !== ws) {
                    appClients.set(deviceId, { ws, deviceId, lastPing: Date.now() });
                    console.log(`[AppWS] deviceId=${deviceId} 등록됨`);
                } else {
                    entry.lastPing = Date.now();
                }
            }

            // 2) ping/pong
            if (msg.type === 'pong' && deviceId) {
                const entry = appClients.get(deviceId);
                if (entry) entry.lastPing = Date.now();
                return;
            }

            // 3) 기타 ack
            if (msg.type === 'ack' && msg.eventId && deviceId) {
                console.log(`[AppWS] ACK from ${deviceId}: ${msg.eventId}`);
                return;
            }
        });

        ws.on('close', () => {
            if (deviceId) appClients.delete(deviceId);
            console.log(`[AppWS] 연결 종료: ${deviceId}`);
        });
    });

    // --- 거리/근접/도착 업데이트 루프 (2초 간격) ---
    setInterval(() => {
        for (const [deviceId, client] of appClients.entries()) {
            const { ws } = client;
            if (ws.readyState !== 1) continue;

            // 현재 이 deviceId 가 가진 승차 요청 1건 검색
            const req = [...rideRequests.values()].find((r) => r.deviceId === deviceId);
            if (!req) continue;

            // 앱이 보낸 최신 위치 우선 사용
            const latestUser = devicesTelemetry.get(deviceId);
            const userPos = latestUser
                ? { lat: latestUser.lat, lon: latestUser.lon }
                : req.userLocation;
            if (!userPos?.lat || !userPos?.lon) continue;

            // 해당 노선/버스의 가장 가까운 차량 찾기
            let nearest = null;
            let minDist = Infinity;
            for (const bus of activeBuses.values()) {
                const sameLine =
                    (req.lineNo && bus.busNumber === req.lineNo) ||
                    (req.busNumber && bus.busNumber === req.busNumber);

                if (sameLine && bus.lat && bus.lon) {
                    const dist = haversine(bus.lat, bus.lon, userPos.lat, userPos.lon);
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = bus;
                    }
                }
            }

            if (!nearest || !isFinite(minDist)) continue;

            const { distance_m, eta_sec } = calcDistanceAndETA(nearest, userPos);

            // 1) 거리 업데이트
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

            // 2) 근접 알림 (< 50m)
            if (distance_m < 50 && !req.flags?.nearSent) {
                console.log(`[AppWS] bus_nearby → ${deviceId}, dist=${distance_m}`);
                ws.send(JSON.stringify({
                    type: "bus_nearby",
                    distance_m: distance_m
                }));
                req.flags = { ...(req.flags || {}), nearSent: true };
            }

            // 3) 도착 알림 (< 10m)
            if (distance_m < 10 && !req.flags?.arrivedSent) {
                console.log(`[AppWS] bus_arrived → ${deviceId}, dist=${distance_m}`);
                ws.send(JSON.stringify({
                    type: "bus_arrived",
                    distance_m: distance_m
                }));
                req.flags = { ...(req.flags || {}), arrivedSent: true };
            }


            // 움직임 기반 request_confirmed 같은 고급 플래그는 그대로 유지
            const prev = req.flags?.prevUserPos;
            if (prev?.lat && prev?.lon) {
                const moved = haversine(prev.lat, prev.lon, userPos.lat, userPos.lon);
                req.flags.totalMoved = (req.flags.totalMoved || 0) + moved;

                if (distance_m <= 50 && !req.flags?.confirmed && req.flags.totalMoved >= 20) {
                    ws.send(JSON.stringify({
                        type: 'request_confirmed',
                        requestId: req.requestId,
                        by: 'movement_within_50m_20m',
                        ts: new Date().toISOString(),
                    }));
                    req.flags.confirmed = true;
                }
            }

            req.flags = { ...(req.flags || {}), prevUserPos: userPos };
        }
    }, 2000);
}

function pushToApp(deviceId, payload) {
    const entry = appClients.get(deviceId);
    if (entry && entry.ws.readyState === 1) {
        entry.ws.send(JSON.stringify(payload));
        return true;
    }
    return false;
}
// --- WebSocket keepalive (ping) 추가 ---
setInterval(() => {
    for (const [deviceId, client] of appClients.entries()) {
        if (client.ws.readyState === 1) {
            try {
                client.ws.ping();
            } catch (err) {
                console.log(`[AppWS] ping error for ${deviceId}:`, err);
            }
        }
    }
}, 15000);



module.exports = { attachAppWS, appClients, pushToApp };
