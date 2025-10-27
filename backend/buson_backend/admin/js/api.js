//admin/js/api.js
// API 래퍼 (백엔드 REST와 연동)
// 백엔드에서 다음 엔드포인트를 제공한다고 가정함:
//
// GET  /api/connections?type=phone|bus|stop
//  -> [{ ip, id, lastSeen, busNumber|null, vehicleNumber|null, stopId|null }]
//
// POST /api/command { targetType, targetId, command }
//  -> { success: true }
//
// POST /api/run-code { targetType, targetId, language, code }
//  -> { success: true, output?: "..." }
//
// GET  /api/logs?limit=200
//  -> ["log line 1", "log line 2", ...]
//
// (서버 IP는 동일 오리진 가정. 필요 시 BASE_URL 교체)

const BASE_URL = '';

export async function fetchConnections(type) {
  const res = await fetch(`${BASE_URL}/api/connections?type=${encodeURIComponent(type)}`);
  if (!res.ok) throw new Error('연결 목록을 불러오지 못했습니다');
  return res.json();
}

export async function sendCommand(payload) {
  const res = await fetch(`${BASE_URL}/api/command`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error('명령 전송 실패');
  return res.json();
}

export async function runCode(payload) {
  const res = await fetch(`${BASE_URL}/api/run-code`, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(payload)
  });
  if (!res.ok) throw new Error('코드 실행 실패');
  return res.json();
}

export async function fetchLogs(limit = 200) {
  const res = await fetch(`${BASE_URL}/api/logs?limit=${limit}`);
  if (!res.ok) throw new Error('로그를 불러오지 못했습니다');
  return res.json();
}
