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
