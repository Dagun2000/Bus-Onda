/**
 * appWs.cjs — BusOn 모바일 앱용 WebSocket 서버 (/app-ws)
 * -------------------------------------------------------
 * 역할:
 *  - 앱에서 실시간 거리/상태 업데이트 수신
 *  - 서버에서 푸시: distance_update, ride_rejected, no_show_warning 등
 */

const { WebSocketServer } = require('ws');
//const { rideRequests, devicesTelemetry, activeBuses } = require('./appApi.cjs');
//const { haversine } = require('./utils/geo.js');
const { rideRequests } = require('../models/rideRequests.cjs');
//const { activeBuses } = require('./appApi.cjs');
const { haversine, calcDistanceAndETA } = require('../utils/geo.cjs');
const { devicesTelemetry, activeBuses } = require('./appApi.cjs');

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
            if (!req) continue;

            // 앱 최신 위치 우선 사용
            const latestUser = devicesTelemetry.get(deviceId);
            const userPos = latestUser
                ? { lat: latestUser.lat, lon: latestUser.lon }
                : req.userLocation;
            if (!userPos?.lat || !userPos?.lon) continue;

            // 해당 노선의 가장 가까운 버스 찾기
            let nearest = null;
            let minDist = Infinity;
            for (const bus of activeBuses.values()) {
                if (bus.busNumber === req.lineNo && bus.lat && bus.lon) {
                    const dist = haversine(bus.lat, bus.lon, userPos.lat, userPos.lon);
                    if (dist < minDist) {
                        minDist = dist;
                        nearest = bus;
                    }
                }
            }

            // 거리 계산 및 앱 알림
            if (nearest && isFinite(minDist)) {
                const { distance_m, eta_sec } = calcDistanceAndETA(nearest, userPos);
                ws.send(JSON.stringify({
                    type: 'distance_update',
                    requestId: req.requestId,
                    distance_m, eta_sec,
                    bus: {
                        busNumber: nearest.busNumber,
                        vehicleNumber: nearest.vehicleNumber || null,
                    },
                    ts: new Date().toISOString(),
                }));

                // 근접/도착 알림
                if (distance_m < 50 && !req.flags?.nearSent) {
                    ws.send(JSON.stringify({
                        type: 'bus_nearby',
                        message: '버스가 50m 이내 접근 중입니다.',
                        distance_m,
                        ts: new Date().toISOString(),
                    }));
                    req.flags = { ...(req.flags || {}), nearSent: true };
                }

                if (distance_m < 10 && !req.flags?.arrivedSent) {
                    ws.send(JSON.stringify({
                        type: 'bus_arrived',
                        message: '버스가 정류장에 도착했습니다.',
                        distance_m,
                        ts: new Date().toISOString(),
                    }));
                    req.flags = { ...(req.flags || {}), arrivedSent: true };
                }

                // ===== 확인 / 자동승인-노쇼 플래그 로직 =====
                const prev = req.flags?.prevUserPos;
                if (prev?.lat && prev?.lon) {
                    const moved = haversine(prev.lat, prev.lon, userPos.lat, userPos.lon);
                    req.flags.totalMoved = (req.flags.totalMoved || 0) + moved;

                    // [확인] 50m 이내 + 누적 20m 이동
                    if (distance_m <= 50 && !req.flags?.confirmed && req.flags.totalMoved >= 20) {
                        ws.send(JSON.stringify({
                            type: 'request_confirmed',
                            requestId: req.requestId,
                            by: 'movement_within_50m_20m',
                            ts: new Date().toISOString(),
                        }));
                        req.flags.confirmed = true;
                    }

                    // [자동승인 → 노쇼] 자동승인 상태 + 100m 이내 + 누적 20m 이동
                    if (req.status === 'ACCEPTED_AUTO' && !req.flags?.noShowFlag &&
                        distance_m <= 100 && req.flags.totalMoved >= 20) {
                        ws.send(JSON.stringify({
                            type: 'no_show_flag',
                            requestId: req.requestId,
                            by: 'auto_accept_within_100m_20m',
                            ts: new Date().toISOString(),
                        }));
                        req.flags.noShowFlag = true;
                        // TODO: 여기서 요청 종료 + 새 버스 매칭 로직은 추후 구현
                    }
                }

                // 현재 위치 저장
                req.flags = { ...(req.flags || {}), prevUserPos: userPos };
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


function resetMobileFlags(deviceId) {
    pushToApp(deviceId, {
        type: 'command',
        cmd: 'reset_flags',
        payload: { flags: { ride: 0, alight: 0, near: 0 } },
        ts: Date.now(),
    });
    console.log(`[APP] reset_flags → ${deviceId}`);
}



module.exports = { attachAppWS, appClients, pushToApp };
