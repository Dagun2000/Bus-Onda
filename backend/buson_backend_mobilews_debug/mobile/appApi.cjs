/**
 * appApi.cjs BusOn 모바일 앱용 REST API
 * ----------------------------------------------
 * Base URL: /api/v1 (server.cjs에서 app.use('/api/v1', router) 로 마운트)
 */



const express = require('express');
const { v4: uuidv4 } = require('uuid');
const { haversine } = require('../utils/geo.cjs');
const { pushToDevice } = require('../deviceBridge.cjs');

const router = express.Router();

// 승차 요청 모델
const {
  createRideRequest,
  updateStatus,
  getRequest,
  listRequests,
  deleteRequest,
  rideRequests,
} = require('../models/rideRequests.cjs');

// ======================
// In-memory 저장소
// ======================
const devicesTelemetry = new Map(); // deviceId → { lat, lon, ts, plateNumber, lineName, stopNo, direction }
const activeBuses = new Map();      // busId   → { lat, lon, busNumber, vehicleNumber, ws }



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
//  버스/정류장 리스트 조회
//  GET /api/v1/list
// ======================
router.get('/list', (req, res) => {
  try {
    // server.cjs 에서 setupWS 로 만든 client 맵 사용
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

    res.json(wrap(true, { buses, stops }));
  } catch (err) {
    console.error('[API /list]', err);
    res.status(500).json(
      wrap(false, null, {
        code: 'SERVER_ERROR',
        message: err.message,
      })
    );
  }
});

// ======================
// 0. 실시간 위치 전송 (Telemetry)
// POST /api/v1/telemetry
// ======================
//
// 앱이 1초 주기로 보내는 위치/상황 정보
// Body:
// {
//   "deviceId": "android-001",
//   "plateNumber": "서울70가1234",
//   "lineName": "동작 01",
//   "stopNo": "115000123",
//   "direction": "up",
//   "position": { "lat": 37.5665, "lon": 126.9780 }
// }
//
router.post('/telemetry', (req, res) => {
  const {
    deviceId,
    plateNumber,
    lineName,
    stopNo,
    direction,
    position
  } = req.body || {};

  if (!deviceId || !position?.lat || !position?.lon) {
    return res.status(400).json({
      success: false,
      error: {
        code: "VALIDATION_ERROR",
        message: "deviceId and position(lat, lon) required"
      },
      serverTime: new Date().toISOString()
    });
  }

  // 위치 저장 (distance_update용)
  devicesTelemetry.set(deviceId, {
    lat: position.lat,
    lon: position.lon
  });

  //  여기 추가: 휴대단말 목록도 갱신 (관리자용)
  try {
    const { phoneClients } = require('../server.cjs');
    const prev = phoneClients.get(deviceId) || {};
    phoneClients.set(deviceId, {
      ...prev,
      id: deviceId,
      ip: prev.ip || req.ip,
      lat: position.lat,
      lon: position.lon,
      requestBus: prev.requestBus || null,
      boardCount: prev.boardCount || 0,
      alightCount: prev.alightCount || 0,
      cancelCount: prev.cancelCount || 0,
      lastSeen: Date.now(),
    });
  } catch (e) {
    console.error('[telemetry] phoneClients update error:', e);
  }

  res.json({
    success: true,
    data: {
      accepted: true,
      send: true
    },
    serverTime: new Date().toISOString()
  });
});



// ======================
// 1. 승차 요청 (Ride Request)
// POST /api/v1/ride/request
// ======================
//

router.post('/ride/request', (req, res) => {
  const {
    deviceId,
    lineNo,        // 구버전 호환용
    lineName,      // 새 명세
    plateNumber,   // 선택값
    stopNo,
    stopName,
    direction,
    userLocation,
  } = req.body || {};

  // lineNo 또는 lineName 둘 중 하나만 와도 되게
  const effectiveLine = lineNo || lineName;

  if (!deviceId || !effectiveLine || !stopNo ||
      !userLocation?.lat || !userLocation?.lon) {
    return res.status(400).json(wrap(false, null, {
      code: 'VALIDATION_ERROR',
      message: 'deviceId, lineName(or lineNo), stopNo, userLocation(lat, lon) required',
    }));
  }


  
const safeStopName = stopName || stopNo;

const request = createRideRequest({
  requestId: `req-${Date.now()}`,
  deviceId,
  lineNo: effectiveLine,
  busNumber: effectiveLine,
  plateNumber: plateNumber ?? null,
  stopNo,
  stopName,
  direction,
  userLocation,
});





    rideRequests.set(request.requestId, request);

  // 여기서부터: 찾는 버스가 없으면, 개발 단말기인 라즈베리파이(1)로 ride_request 전송
  try {
    const { deviceClients } = require('../server.cjs');
    const { haversine } = require('../utils/geo.cjs');

    let targetDevId = null;
    let targetWs = null;
    let minDist = Infinity;

    // 1) 같은 노선/차량번호인 버스 먼저 찾기
    for (const [devId, d] of deviceClients.entries()) {
      const meta = d.meta || {};
      const busNo   = meta.bus_number;
      const vehNo   = meta.vehicle_number;

      const sameLine =
        (busNo && busNo === effectiveLine) ||
        (plateNumber && vehNo && vehNo === plateNumber);

      if (!sameLine) continue;

      // 위치가 있으면 가장 가까운 버스 선택
      if (meta.lat != null && meta.lon != null &&
          userLocation?.lat && userLocation?.lon) {
        const dist = haversine(meta.lat, meta.lon, userLocation.lat, userLocation.lon);
        if (dist < minDist) {
          minDist = dist;
          targetDevId = devId;
          targetWs = d.ws;
        }
      } else if (!targetDevId) {
        // 위치 없으면 일단 첫 번째 한 대라도 선택
        targetDevId = devId;
        targetWs = d.ws;
      }
    }

    // 2) 버스를 못 찾으면 라즈베리파이(디바이스 ID = '1')로 fallback
    if (!targetDevId) {
      targetDevId = '1';
      const d = deviceClients.get(targetDevId);
      if (d) targetWs = d.ws;
    }

    if (targetWs && targetWs.readyState === 1) {
      targetWs.send(JSON.stringify({
        type: 'ride_request',
        msg_id: request.requestId,
        payload: {
          requestId: request.requestId,
          fromDeviceId: deviceId,           // 이 요청을 보낸 휴대폰
          lineName: effectiveLine,
          plateNumber: plateNumber ?? null,
          stopNo,
          stopName,                 // 지금은 stopNo가 곧 정류장 이름/표시라고 보고 같이 보냄
          direction,
          userLocation,
        },
      }));
      console.log('[ride/request] sent to device', targetDevId);
    } else {
      console.log('[ride/request] no bus or raspi(1) connected, stored only', request.requestId);
    }
  } catch (e) {
    console.error('[ride/request] send to bus/raspi error:', e);
  }

  // ---- 클라이언트(휴대폰)에는 기존대로 응답 ----
  return res.json({
    success: true,
    data: {
      requestId: request.requestId,
      status: request.status,
    },
    serverTime: new Date().toISOString(),
  });
});



// ======================
// 2. 승차 요청 취소
// POST /api/v1/ride/cancel
// ======================
router.post('/ride/cancel', (req, res) => {
  const { deviceId, requestId } = req.body || {};
  const info = rideRequests.get(requestId);

  if (!info) {
    return res
      .status(404)
      .json(wrap(false, null, { code: 'NOT_FOUND', message: 'Request not found' }));
  }

  info.status = 'CANCELLED';
  info.cancelledBy = deviceId || null;

  // 앱에는 즉시 OK 반환
  res.json(wrap(true, { status: 'CANCELLED' }));

  // -------------------------------
  // 1) 버스 단말(또는 라즈베리파이)로 취소 통보
  //    - assignedDevId 우선
  //    - 없으면 lineNo/busNumber로 deviceClients 매칭
  //    - 그래도 없으면 raspi '1'
  // -------------------------------
  try {
    const { deviceClients } = require('../server.cjs');

    const targets = new Set();

    // (A) 가장 확실: ride/request 때 기록해둔 타겟 단말
    if (info.assignedDevId) targets.add(String(info.assignedDevId));

    // (B) 보조: 노선 매칭으로도 한 번 더 잡기
    for (const [devId, d] of deviceClients.entries()) {
      const meta = d.meta || {};
      const sameLine =
        (info.lineNo && meta.bus_number === info.lineNo) ||
        (info.busNumber && meta.bus_number === info.busNumber);

      if (sameLine) targets.add(String(devId));
    }

    // (C) 그래도 없으면 fallback raspi
    if (targets.size === 0) targets.add('1');

    // 전송
    for (const devId of targets) {
      const d = deviceClients.get(devId);
      if (d?.ws?.readyState === 1) {
        d.ws.send(JSON.stringify({
          type: 'command',
          cmd: 'cancel_request',
          ts: Date.now(),
          payload: { requestId, reason: 'user_cancel' },
        }));
        console.log(`[RIDE] cancel_request sent to device=${devId} requestId=${requestId}`);
      }
    }
  } catch (e) {
    console.error('[ride/cancel] send cancel_request to device error:', e);
  }

  // -------------------------------
  // 2) (기존 유지) activeBuses에도 취소 통보
  //    - activeBuses가 실제로 최신 ws를 보장하지 않으면
  //      위 deviceClients만으로도 충분함(원하면 삭제 가능)
  // -------------------------------
  try {
    for (const bus of activeBuses.values()) {
      const sameLine =
        (info.lineNo && bus.busNumber === info.lineNo) ||
        (info.busNumber && bus.busNumber === info.busNumber);

      if (sameLine && bus.ws?.readyState === 1) {
        bus.ws.send(JSON.stringify({
          type: 'command',
          cmd: 'cancel_request',
          ts: Date.now(),
          payload: { requestId, reason: 'user_cancel' },
        }));
      }
    }
  } catch (e) {
    console.error('[ride/cancel] activeBuses cancel broadcast error:', e);
  }

  console.log(`[RIDE] ${requestId} cancelled by ${deviceId || 'unknown-device'}`);

  // -------------------------------
  // 3) 휴대단말 취소 카운트 갱신
  // -------------------------------
  try {
    const { phoneClients } = require('../server.cjs');
    const phone = phoneClients.get(deviceId);
    if (phone) {
      phone.cancelCount = (phone.cancelCount || 0) + 1;
      phone.lastSeen = Date.now();
    }
  } catch (e) {
    console.error('[ride/cancel] phoneClients update error:', e);
  }
});



// ======================
// 3. 하차 요청 - 항상 OK + 오류여도 카운트 증가
// ======================
router.post('/ride/alight', (req, res) => {
  const {
    deviceId,
    plateNumber,
    lineName,
    stopNo,
    position
  } = req.body || {};

  // 기본 검증 — 실제 서비스라면 이마저도 OK로 바꾸고 싶으면 말해줘.
  if (!deviceId || !lineName || !stopNo ||
      !position?.lat || !position?.lon) {

    // 그래도 카운트는 증가시킴
    try {
      const { phoneClients } = require('../server.cjs');
      const phone = phoneClients.get(deviceId);
      if (phone) {
        phone.alightCount = (phone.alightCount || 0) + 1;
        phone.lastSeen = Date.now();
      }
    } catch (e) {
      console.error('[alight] count update error:', e);
    }

    // 앱에게는 실패로 주면 앱이 오류 창을 띄우니 false를 주지 않아야 함.
    return res.json({
      success: true,
      data: {
        accepted: true,
        send: true,
        status: "ALIGHT_PENDING",
      },
      serverTime: new Date().toISOString()
    });
  }

  // -------------------------------
  // 정상 요청이든 오류든 카운트 증가
  // -------------------------------
  try {
    const { phoneClients } = require('../server.cjs');
    const phone = phoneClients.get(deviceId);
    if (phone) {
      phone.alightCount = (phone.alightCount || 0) + 1;
      phone.lastSeen = Date.now();
      phone.requestBus = lineName;
    }
  } catch (e) {
    console.error('[alight] phoneClients update error:', e);
  }

  // -------------------------------
  // 버스 또는 라즈베리파이에 하차 요청 전송
  // -------------------------------
  try {
    const { deviceClients } = require('../server.cjs');

    let targetDevId = null;
    let targetWs = null;

    // 1) lineName과 일치하는 버스 찾기
    for (const [devId, d] of deviceClients.entries()) {
      const meta = d.meta || {};
      if (meta.bus_number && meta.bus_number === lineName) {
        targetDevId = devId;
        targetWs = d.ws;
        break;
      }
    }

    // 2) 버스가 없으면 라즈베리파이 ID = '1'
    if (!targetDevId) {
      const raspi = deviceClients.get('1');
      if (raspi) {
        targetDevId = '1';
        targetWs = raspi.ws;
      }
    }

    // 3) 전송 (없으면 로그만 남기고 무시)
    if (targetWs && targetWs.readyState === 1) {
      targetWs.send(JSON.stringify({
        type: 'alight_request',
        msg_id: uuidv4(),
        ts: Date.now(),
        payload: {
          deviceId,
          lineName,
          plateNumber: plateNumber ?? null,
          stopNo,              // 정류장 번호/이름
          stopName: stopNo,    // 동일하게 전달 (앱에서는 문자열을 정류장이름으로 보고 있음)
          position
        }
      }));
      console.log(`[ALIGHT] Sent alight_request to '${targetDevId}'`);
    } else {
      console.log(`[ALIGHT] No bus or raspi connected for line '${lineName}'`);
    }

  } catch (e) {
    console.error('[ALIGHT] internal send error:', e);
  }

  // -------------------------------
  // 앱에게는 항상 OK
  // -------------------------------
  return res.json({
    success: true,
    data: {
      accepted: true,
      send: true,
      status: "ALIGHT_PENDING"
    },
    serverTime: new Date().toISOString()
  });
});


// ======================
// (기존 고급 기능들: ride/event, ride/status 등은 일단 그대로 두거나,
// 나중에 완전히 안 쓸 거면 이 파일에서 제거해도 무방)
// ======================

// 하차/승차 완료 이벤트 (기존 로직 유지, 필요시 삭제 가능)
router.post('/ride/event', (req, res) => {
  const { requestId, type } = req.body || {};
  const statusMap = {
    ALIGHT_COMPLETE: 'COMPLETED',
    BOARD_COMPLETE: 'ACCEPTED',
    NO_SHOW_REPORT: 'NO_SHOW',
  };
  const newStatus = statusMap[type];
  if (!newStatus) {
    return res
      .status(400)
      .json(wrap(false, null, { code: 'VALIDATION_ERROR', message: 'Invalid type' }));
  }

  const info = updateStatus(requestId, newStatus);
  if (!info) {
    return res
      .status(404)
      .json(wrap(false, null, { code: 'NOT_FOUND', message: 'Request not found' }));
  }

  console.log(`[RIDE] ${requestId} event: ${type}`);
  res.json(wrap(true, { ack: true }));
});

// 상태 조회 (기존 유지 / 필요없으면 제거 가능)
router.get('/ride/status', (req, res) => {
  const { requestId } = req.query;
  const info = getRequest(requestId);
  if (!info) {
    return res
      .status(404)
      .json(wrap(false, null, { code: 'NOT_FOUND', message: 'Request not found' }));
  }

  let nearest = null;
  let minDist = Infinity;
  for (const bus of activeBuses.values()) {
    const sameLine =
      (info.lineNo && bus.busNumber === info.lineNo) ||
      (info.busNumber && bus.busNumber === info.busNumber);

    if (sameLine && bus.lat && info.userLocation) {
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
      eta_sec: isFinite(minDist) ? Math.round(minDist / 5) : null, // 5m/s 가정
    })
  );
});

// 핑 (헬스체크) — GET /api/v1/ping
router.get('/ping', (_req, res) => {
  res.json(wrap(true, { uptimeSec: process.uptime(), version: '1.0.0' }));
});

// 요청 목록 전체 조회 (디버그용)
router.get('/ride/list', (_req, res) => {
  res.json(wrap(true, { list: listRequests() }));
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
