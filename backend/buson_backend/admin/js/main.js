import { fetchConnections, sendCommand, runCode, fetchLogs } from './api.js';

const wsStatus = document.getElementById('wsStatus');
const tblPhones = document.getElementById('tblPhones').querySelector('tbody');
const tblBuses  = document.getElementById('tblBuses').querySelector('tbody');
const tblStops  = document.getElementById('tblStops').querySelector('tbody');
const logArea   = document.getElementById('logArea');

const selected = { phone: null, bus: null, stop: null };

function fmt(v) { return v ?? '<span class="badge null">NULL</span>'; }
function fmtTime(ts) {
  if (!ts) return '<span class="badge null">NULL</span>';
  try { return new Date(ts).toLocaleString(); } catch { return ts; }
}
function rowClickable(tr, type, id) {
  tr.style.cursor = 'pointer';
  tr.addEventListener('click', () => {
    selected[type] = id;
    addLog(`선택됨 → [${type}] ${id}`, 'ok');
  });
}

function renderPhones(data=[]) {
  tblPhones.innerHTML = '';
  data.forEach(d => {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${fmt(d.ip)}</td><td>${fmt(d.id)}</td><td>${fmtTime(d.lastSeen)}</td>`;
    rowClickable(tr, 'phone', d.id);
    tblPhones.appendChild(tr);
  });
}

function renderBuses(data=[]) {
  tblBuses.innerHTML = '';
  data.forEach(d => {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${fmt(d.ip)}</td><td>${fmt(d.busNumber)}</td><td>${fmt(d.vehicleNumber)}</td><td>${fmt(d.id)}</td><td>${fmtTime(d.lastSeen)}</td>`;
    rowClickable(tr, 'bus', d.id);
    tblBuses.appendChild(tr);
  });
}

function renderStops(data=[]) {
  tblStops.innerHTML = '';
  data.forEach(d => {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${fmt(d.ip)}</td><td>${fmt(d.stopId)}</td><td>${fmt(d.id)}</td><td>${fmtTime(d.lastSeen)}</td>`;
    rowClickable(tr, 'stop', d.id);
    tblStops.appendChild(tr);
  });
}

function addLog(line, level='') {
  const cls = level ? ` ${level}` : '';
  logArea.insertAdjacentHTML('afterbegin', `<div class="${cls}">${new Date().toLocaleTimeString()} · ${line}</div>`);
}

async function refresh(type) {
  try {
    if (type === 'phone') {
      const data = await fetchConnections('phone'); renderPhones(data);
      addLog(`휴대단말 ${data.length}건 갱신`, 'ok');
    } else if (type === 'bus') {
      const data = await fetchConnections('bus'); renderBuses(data);
      addLog(`버스 단말 ${data.length}건 갱신`, 'ok');
    } else if (type === 'stop') {
      const data = await fetchConnections('stop'); renderStops(data);
      addLog(`정류장 단말 ${data.length}건 갱신`, 'ok');
    }
  } catch (e) {
    addLog(`갱신 실패: ${e.message}`, 'err');
  }
}

function hookButtons() {
  document.querySelectorAll('button[data-refresh]').forEach(btn => {
    btn.addEventListener('click', () => refresh(btn.dataset.refresh));
  });

  document.getElementById('btnLogRefresh').addEventListener('click', async () => {
    try {
      const lines = await fetchLogs(200);
      logArea.innerHTML = '';
      lines.reverse().forEach(l => addLog(l));
    } catch (e) {
      addLog(`로그 갱신 실패: ${e.message}`, 'err');
    }
  });

  document.getElementById('btnLogClear').addEventListener('click', () => {
    logArea.innerHTML = '';
  });

  // 선택된 테이블 행의 ID를 양식에 넣어주기 위한 헬퍼
  document.querySelectorAll('.btn-command').forEach(btn => {
    btn.addEventListener('click', () => {
      const type = btn.dataset.target;
      const id = selected[type];
      if (!id) return addLog(`먼저 ${type} 목록에서 단말을 클릭해 선택하세요`, 'warn');
      const input = document.querySelector('#formCommand [name="targetId"]');
      const select = document.querySelector('#formCommand [name="targetType"]');
      select.value = type; input.value = id;
    });
  });
  document.querySelectorAll('.btn-code').forEach(btn => {
    btn.addEventListener('click', () => {
      const type = btn.dataset.target;
      const id = selected[type];
      if (!id) return addLog(`먼저 ${type} 목록에서 단말을 클릭해 선택하세요`, 'warn');
      const input = document.querySelector('#formCode [name="targetId"]');
      const select = document.querySelector('#formCode [name="targetType"]');
      select.value = type; input.value = id;
    });
  });

  // 명령 전송
  document.getElementById('formCommand').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const payload = {
      targetType: fd.get('targetType'),
      targetId:   fd.get('targetId'),
      command:    fd.get('command')
    };
    if (!payload.targetId || !payload.command) return addLog('대상/명령을 입력하세요', 'warn');
    try {
      const r = await sendCommand(payload);
      addLog(`명령 전송 완료 → ${payload.targetType}/${payload.targetId}: ${payload.command}`, r.success?'ok':'warn');
    } catch (err) {
      addLog(`명령 전송 실패: ${err.message}`, 'err');
    }
  });

  // 코드 실행
  document.getElementById('formCode').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    const payload = {
      targetType: fd.get('targetType'),
      targetId:   fd.get('targetId'),
      language:   fd.get('language'),
      code:       fd.get('code')
    };
    if (!payload.targetId || !payload.code) return addLog('대상/코드를 입력하세요', 'warn');
    try {
      const r = await runCode(payload);
      addLog(`코드 실행 요청 → ${payload.targetType}/${payload.targetId} (${payload.language})`, r.success?'ok':'warn');
      if (r.output) addLog(`출력:\n${r.output}`);
    } catch (err) {
      addLog(`코드 실행 실패: ${err.message}`, 'err');
    }
  });
}

function connectWS() {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  //const wsURL = `${proto}//${location.host}/admin`;
  const wsURL = `${proto}//${location.host}/admin-ws`;
  const ws = new WebSocket(wsURL);

  ws.addEventListener('open', () => {
    wsStatus.textContent = 'WS: 연결됨';
    wsStatus.style.color = '#28c76f';
    addLog('WebSocket 연결됨', 'ok');
  });
  ws.addEventListener('close', () => {
    wsStatus.textContent = 'WS: 연결 끊김 (재시도 중)';
    wsStatus.style.color = '#ff9f43';
    addLog('WebSocket 연결 끊김', 'warn');
    setTimeout(connectWS, 1500);
  });
  ws.addEventListener('message', (ev) => {
    try {
      const msg = JSON.parse(ev.data);
      // 서버에서 보내줄 표준 이벤트 예시:
      // {type:"connection_update", deviceType:"bus|phone|stop", list:[...]}
      // {type:"log", line:"..."}
      if (msg.type === 'connection_update') {
        if (msg.deviceType === 'phone') renderPhones(msg.list);
        else if (msg.deviceType === 'bus') renderBuses(msg.list);
        else if (msg.deviceType === 'stop') renderStops(msg.list);
        addLog(`실시간 갱신: ${msg.deviceType} ${msg.list?.length ?? 0}건`, 'ok');
      } else if (msg.type === 'log') {
        addLog(msg.line);
      }
    } catch {
      addLog(ev.data);
    }
  });
}

(async function init() {
  hookButtons();
  connectWS();
  // 초기 로드
  await Promise.all([refresh('phone'), refresh('bus'), refresh('stop')]).catch(()=>{});
  // 주기적 폴백 갱신 (WS가 안 올 때 대비)
  setInterval(() => { refresh('phone'); }, 15000);
  setInterval(() => { refresh('bus'); }, 16000);
  setInterval(() => { refresh('stop'); }, 17000);
})();
