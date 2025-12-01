/**
 * appApi.cjs BusOn 모바일 앱용 REST API
 * ----------------------------------------------
 * Base URL: /api
 */

const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { haversine } = require('../utils/geo.cjs');
const { pushToDevice } = require('../deviceBridge.cjs');
const { pushToApp } = require('../mobile/appWs.cjs');

//const { wrap } = require('./utils/wrap.js'); // 공통 응답 헬퍼 (있으면)

const router = express.Router();
// appApi.cjs 상단 부분

// 추가 (rideRequests 모듈)
const {
    createRideRequest,
    updateStatus,
    getRequest,
    listRequests,
    deleteRequest,
    rideRequests,
} = require('../models/rideRequests.cjs');


// ======================
// 데이터 저장소 (임시 in-memory)
// ======================
//const rideRequests = new Map();     // requestId → { deviceId, lineNo, direction, userLocation, status }
const devicesTelemetry = new Map(); // deviceId → { lat, lon, ts }
const activeBuses = new Map();      // busId → { lat, lon, busNumber, vehicleNumber, ws }

// ======================
// 공통 유틸
// ======================
function nowISO() {
    return new Date().toISOString();
}

function wrap(success, data, error) {
    return {
        success,
        data,
        error,
        serverTime: nowISO(),
    };
}

// ======================
//  디바이스 등록 (앱 로그인)
// POST /api/v1/auth/device
// ======================

const fs = require('fs');
const path = require('path');
const dataDir = path.join(__dirname, '../data');
const deviceFile = path.join(dataDir, 'devices.json');

// 로그인 데이터 로드/저장 헬퍼
function loadDevices() {
    try {
        if (!fs.existsSync(deviceFile)) return {};
        return JSON.parse(fs.readFileSync(deviceFile, 'utf8'));
    } catch {
        return {};
    }
}
function saveDevices(devices) {
    if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });
    fs.writeFileSync(deviceFile, JSON.stringify(devices, null, 2));
}

router.post('/auth/device', (req, res) => {
    const { deviceId, appVersion, model, platform } = req.body || {};
    if (!deviceId)
        return res.status(400).json({
            success: false,
            error: { code: 'VALIDATION_ERROR', message: 'deviceId is required' },
            serverTime: new Date().toISOString(),
        });

    const devices = loadDevices();

    // 이미 등록된 기기면 기존 토큰 재사용
    let token = devices[deviceId]?.token;
    if (!token) {
        token = 'demo-' + uuidv4();
        devices[deviceId] = {
            token,
            appVersion,
            model,
            platform,
            registeredAt: new Date().toISOString(),
        };
        saveDevices(devices);
    }

    console.log(`[AUTH] Device registered: ${deviceId} (${model || '?'}, ${platform || '?'})`);

    res.json({
        success: true,
        data: {
            token,
            expiresInSec: 60 * 60 * 24 * 30, // 30일
        },
        serverTime: new Date().toISOString(),
    });
});


// ======================
//  버스/정류장 리스트 조회
//  GET /api/v1/list
// ======================
router.get('/list', (req, res) => {
    try {
        // 이미 setupWS로 만든 client 맵 가져오기
        const { deviceClients, stopClients } = require('../server.cjs');

        const buses = [...deviceClients.values()].map(d => ({
            id: d.meta?.id ?? null,
            busNumber: d.meta?.bus_number ?? null,
            vehicleNumber: d.meta?.vehicle_number ?? null,
            ip: d.meta?.ip ?? null,
            lastSeen: d.lastSeen ? new Date(d.lastSeen).toISOString() : null,
        }));

        const stops = [...stopClients.values()].map(d => ({
            id: d.meta?.id ?? null,
            stopId: d.meta?.stop_id ?? null,
            ip: d.meta?.ip ?? null,
            lastSeen: d.lastSeen ? new Date(d.lastSeen).toISOString() : null,
        }));

        res.json({
            success: true,
            data: { buses, stops },
            serverTime: new Date().toISOString(),
        });
    } catch (err) {
        console.error('[API /list]', err);
        res.status(500).json({
            success: false,
            error: {
                code: 'SERVER_ERROR',
                message: err.message,
            },
            serverTime: new Date().toISOString(),
        });
    }
});

// ======================
//  위치 전송
// POST /api/v1/telemetry
// ======================
router.post('/telemetry', (req, res) => {
    const { deviceId, lineNo, direction, position } = req.body || {};
    if (!deviceId || !position?.lat || !position?.lon)
        return res.status(400).json(wrap(false, null, { code: 'VALIDATION_ERROR', message: 'deviceId and position required' }));

    devicesTelemetry.set(deviceId, {
        ...position,
        ts: nowISO(),
        lineNo,
        direction,
    });

    console.log(`[TELEMETRY] ${deviceId} → (${position.lat}, ${position.lon})`);
    res.json(wrap(true, { accepted: true }));
});


// ======================
// 호출 취소
// POST /api/v1/ride/cancel
// ======================
router.post('/ride/cancel', (req, res) => {
    const { deviceId, requestId, reason } = req.body || {};
    const info = rideRequests.get(requestId);
    if (!info)
        return res.status(404).json(wrap(false, null, { code: 'NOT_FOUND', message: 'Request not found' }));

    info.status = 'CANCELLED';
    res.json(wrap(true, { status: 'CANCELLED' }));

    // 버스 알림
    for (const bus of activeBuses.values()) {
        if (bus.busNumber === info.lineNo && bus.ws?.readyState === 1) {
            bus.ws.send(
                JSON.stringify({
                    type: 'command',
                    cmd: 'cancel_request',
                    payload: { requestId, reason: reason || 'user_cancel' },
                })
            );
        }
    }

    console.log(`[RIDE] ${requestId} cancelled`);
});

// ======================
//  하차 요청
// POST /api/v1/ride/alight-request
// ======================
router.post('/ride/alight-request', (req, res) => {
    const { requestId, targetStopId } = req.body || {};
    const info = updateStatus(requestId, 'ALIGHT_PENDING');
    if (!info)
        return res.status(404).json(wrap(false, null, { code: 'NOT_FOUND', message: 'Request not found' }));

    res.json(wrap(true, { status: info.status }));

    for (const bus of activeBuses.values()) {
        if (bus.busNumber === info.busNumber && bus.ws?.readyState === 1) {
            bus.ws.send(
                JSON.stringify({
                    type: 'command',
                    cmd: 'drop_request',
                    payload: { requestId, targetStopId },
                })
            );
        }
    }
    console.log(`[RIDE] ${requestId} → 하차 요청`);
});


// ======================
// 하차/승차 완료 이벤트
// POST /api/v1/ride/event
// ======================
router.post('/ride/event', (req, res) => {
    const { requestId, type } = req.body || {};
    const statusMap = {
        ALIGHT_COMPLETE: 'COMPLETED',
        BOARD_COMPLETE: 'ACCEPTED',
        NO_SHOW_REPORT: 'NO_SHOW',
    };
    const newStatus = statusMap[type];
    if (!newStatus)
        return res.status(400).json(wrap(false, null, { code: 'VALIDATION_ERROR', message: 'Invalid type' }));

    const info = updateStatus(requestId, newStatus);
    if (!info)
        return res.status(404).json(wrap(false, null, { code: 'NOT_FOUND', message: 'Request not found' }));

    console.log(`[RIDE] ${requestId} event: ${type}`);
    res.json(wrap(true, { ack: true }));
});


// ======================
// 상태 조회
// GET /api/v1/ride/status?requestId=...
// ======================
router.get('/ride/status', (req, res) => {
    const { requestId } = req.query;
    const info = getRequest(requestId);
    if (!info)
        return res.status(404).json(wrap(false, null, { code: 'NOT_FOUND', message: 'Request not found' }));

    // 근처 버스 계산
    let nearest = null;
    let minDist = Infinity;
    for (const bus of activeBuses.values()) {
        if (bus.busNumber === info.busNumber && bus.lat && info.userLocation) {
            const dist = haversine(bus.lat, bus.lon, info.userLocation.lat, info.userLocation.lon);
            if (dist < minDist) {
                minDist = dist;
                nearest = bus;
            }
        }
    }

    res.json(
        wrap(true, {
            requestId,
            status: info.status,
            busInfo: nearest
                ? { busNumber: nearest.busNumber, vehicleNumber: nearest.vehicleNumber || null }
                : null,
            distance_m: isFinite(minDist) ? Math.round(minDist) : null,
            eta_sec: isFinite(minDist) ? Math.round(minDist / 5) : null, // 5m/s 속도 가정
        })
    );
});

// ------------------------------
// 승차 요청 (ride_request)
// ------------------------------
router.post('/ride/request', (req, res) => {
    const { deviceId, lineNo, direction, userLocation } = req.body || {};
    if (!deviceId || !lineNo)
        return res.status(400).json(wrap(false, null, { code: 'VALIDATION_ERROR', message: 'deviceId and lineNo required' }));

    const request = createRideRequest({ deviceId, busNumber: lineNo, direction, userLocation });

    // 10초 자동승인 타이머
    request.autoApproveTimer = setTimeout(() => {
        const r = rideRequests.get(request.requestId);
        if (!r || r.status !== 'PENDING') return;
        r.status = 'ACCEPTED_AUTO';
        r.flags = { ...(r.flags||{}), autoAccepted: true };
        // 앱에 자동 승인 통지
        pushToApp(r.deviceId, {
            type: 'ride_accepted',
            requestId: r.requestId,
            auto: true,
            ts: Date.now(),
            message: '버스 무응답으로 자동 승인되었습니다.',
        });
        // TODO: 단말 강제 승인 프로토콜 도입 시 아래 전송
        // pushToDevice(lineNo, { type:'command', cmd:'force_accept', msg_id:r.requestId, ts:Date.now() });
    }, 10_000);

    const payload = {
        type: 'command',
        cmd: 'ride_request',
        msg_id: request.requestId,
        ts: Date.now(),
        payload: { deviceId, lineNo, direction, userLocation },
    };
    const ok = pushToDevice(lineNo, payload);

    if (!ok) return res.json(wrap(false, null, { code: 'NOT_FOUND', message: `Bus ${lineNo} not connected` }));

    console.log(`[RIDE] ${request.requestId} → push to bus ${lineNo}`);
    res.json(wrap(true, { requestId: request.requestId, status: 'PENDING' }));
});


// ------------------------------
// 하차 요청 (drop_request)
// ------------------------------
router.post('/ride/alight-request', (req, res) => {
    const { busNumber } = req.body || {};
    if (!busNumber)
        return res.status(400).json(wrap(false, null, { code: 'VALIDATION_ERROR', message: 'busNumber is required' }));

    const payload = {
        type: 'command',
        cmd: 'drop_request',
        msg_id: uuidv4(),
        ts: Date.now(),
    };

    const ok = pushToDevice(busNumber, payload);
    if (!ok)
        return res.json(wrap(false, null, { code: 'NOT_FOUND', message: `Bus ${busNumber} not connected` }));

    res.json(wrap(true, { status: 'ALIGHT_PENDING' }));
});

// ------------------------------
// 요청 리셋 (reset)
// ------------------------------
router.post('/ride/reset', (req, res) => {
    const { busNumber } = req.body || {};
    if (!busNumber)
        return res.status(400).json(wrap(false, null, { code: 'VALIDATION_ERROR', message: 'busNumber is required' }));

    const payload = {
        type: 'command',
        cmd: 'reset',
        msg_id: uuidv4(),
        ts: Date.now(),
    };

    const ok = pushToDevice(busNumber, payload);
    if (!ok)
        return res.json(wrap(false, null, { code: 'NOT_FOUND', message: `Bus ${busNumber} not connected` }));

    res.json(wrap(true, { status: 'RESET_SENT' }));
});

// ------------------------------
// 요청 없음으로 상태 초기화 (cancel_request)
// ------------------------------
router.post('/ride/none', (req, res) => {
    const { busNumber } = req.body || {};
    if (!busNumber)
        return res.status(400).json(wrap(false, null, { code: 'VALIDATION_ERROR', message: 'busNumber is required' }));

    const payload = {
        type: 'command',
        cmd: 'cancel_request',
        msg_id: uuidv4(),
        ts: Date.now(),
    };

    const ok = pushToDevice(busNumber, payload);
    if (!ok)
        return res.json(wrap(false, null, { code: 'NOT_FOUND', message: `Bus ${busNumber} not connected` }));

    res.json(wrap(true, { status: 'IDLE' }));
});
// ======================
// 핑 (헬스체크)
// GET /api/v1/ping
// ======================
router.get('/ping', (_req, res) => {
    res.json(wrap(true, { uptimeSec: process.uptime(), version: '1.0.0' }));
});
// ======================
// 요청 목록 전체 조회 (디버그용)
// GET /api/v1/ride/list
// ======================
router.get('/ride/list', (_req, res) => {
    res.json(wrap(true, { list: listRequests() }));
});



// 플래그 초기화용
// POST /api/v1/mobile/reset-flags
router.post('/mobile/reset-flags', (req, res) => {
    const { deviceId } = req.body || {};
    if (!deviceId)
        return res.status(400).json(wrap(false, null, { code: 'VALIDATION_ERROR', message: 'deviceId required' }));

    const ok = pushToApp(deviceId, {
        type: 'command',
        cmd: 'reset_flags',
        payload: { flags: { ride: 0, alight: 0, near: 0 } },
        ts: Date.now(),
    });

    if (!ok)
        return res.json(wrap(false, null, { code: 'NOT_FOUND', message: `App ${deviceId} not connected` }));

    res.json(wrap(true, { success: true }));
});




// ======================
// export
// ======================
module.exports = {
    router,
    rideRequests,
    devicesTelemetry,
    activeBuses,
};
