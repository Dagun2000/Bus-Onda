// admin/js/app.js  (ES Module)
import { fetchConnections, sendCommand, runCode, fetchLogs } from './api.js';

const wsStatus = document.getElementById('wsStatus');
const tblPhones = document.getElementById('tblPhones')?.querySelector('tbody');
const tblBuses  = document.getElementById('tblBuses')?.querySelector('tbody');
const tblStops  = document.getElementById('tblStops')?.querySelector('tbody');
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

function renderPhones(data = []) {
  if (!tblPhones) return;
  tblPhones.innerHTML = '';

  data.forEach(d => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${fmt(d.ip)}</td>
      <td>${fmt(d.id)}</td>
      <td>${fmt(d.lat)}</td>
      <td>${fmt(d.lon)}</td>
      <td>${d.requestBus || '-'}</td>
      <td>${d.boardCount ?? 0}</td>
      <td>${d.alightCount ?? 0}</td>
      <td>${d.cancelCount ?? 0}</td>
      <td>${fmtTime(d.lastSeen)}</td>
    `;
    rowClickable(tr, 'phone', d.id);
    tblPhones.appendChild(tr);
  });
}




function renderBuses(data=[]) {
  if (!tblBuses) return;
  tblBuses.innerHTML = '';
  data.forEach(d => {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${fmt(d.ip)}</td><td>${fmt(d.busNumber)}</td><td>${fmt(d.vehicleNumber)}</td><td>${fmt(d.id)}</td><td>${fmtTime(d.lastSeen)}</td>`;
    rowClickable(tr, 'bus', d.id);
    tblBuses.appendChild(tr);
  });
}

function renderStops(data=[]) {
  if (!tblStops) return;
  tblStops.innerHTML = '';
  data.forEach(d => {
    const tr = document.createElement('tr');
    tr.innerHTML = `<td>${fmt(d.ip)}</td><td>${fmt(d.stopId)}</td><td>${fmt(d.id)}</td><td>${fmtTime(d.lastSeen)}</td>`;
    rowClickable(tr, 'stop', d.id);
    tblStops.appendChild(tr);
  });
}

function addLog(line, level='') {
  if (!logArea) return;
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

  document.getElementById('btnLogRefresh')?.addEventListener('click', async () => {
    try {
      const lines = await fetchLogs(200);
      if (logArea) {
        logArea.innerHTML = '';
        lines.reverse().forEach(l => addLog(l));
      }
    } catch (e) {
      addLog(`로그 갱신 실패: ${e.message}`, 'err');
    }
  });

  document.getElementById('btnLogClear')?.addEventListener('click', () => {
    if (logArea) logArea.innerHTML = '';
  });

  document.querySelectorAll('.btn-command').forEach(btn => {
    btn.addEventListener('click', () => {
      const type = btn.dataset.target;
      const id = selected[type];
      if (!id) return addLog(`먼저 ${type} 목록에서 단말을 클릭해 선택하세요`, 'warn');
      const input = document.querySelector('#formCommand [name="targetId"]');
      const select = document.querySelector('#formCommand [name="targetType"]');
      if (input && select) { select.value = type; input.value = id; }
    });
  });

  document.querySelectorAll('.btn-code').forEach(btn => {
    btn.addEventListener('click', () => {
      const type = btn.dataset.target;
      const id = selected[type];
      if (!id) return addLog(`먼저 ${type} 목록에서 단말을 클릭해 선택하세요`, 'warn');
      const input = document.querySelector('#formCode [name="targetId"]');
      const select = document.querySelector('#formCode [name="targetType"]');
      if (input && select) { select.value = type; input.value = id; }
    });
  });

  // 휴대단말 빠른 제어
  document.querySelectorAll('.quick-actions.phone .quick-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const cmd = btn.dataset.command;
      const id = selected.phone;
      if (!id) return addLog('먼저 휴대단말 목록에서 단말을 클릭하세요', 'warn');

      const payload = { targetType: 'phone', targetId: id, command: cmd };

      try {
        const r = await sendCommand(payload);
        addLog(`휴대단말 빠른 제어 → ${id}: ${cmd}`, r.success ? 'ok' : 'warn');
      } catch (err) {
        addLog(`빠른 제어 실패 (${cmd}): ${err.message}`, 'err');
      }
    });
  });

  document.getElementById('formCommand')?.addEventListener('submit', async (e) => {
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

  document.getElementById('formCode')?.addEventListener('submit', async (e) => {
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

  //버스 단말 빠른 제어 버튼
  document.querySelectorAll('.quick-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const cmd = btn.dataset.command;
      const id = selected.bus;
      if (!id) return addLog('먼저 버스 단말 목록에서 단말을 클릭하세요', 'warn');

      const payload = { targetType: 'bus', targetId: id, command: cmd };
      try {
        const r = await sendCommand(payload);
        addLog(`명령 전송 → [bus] ${id}: ${cmd}`, r.success ? 'ok' : 'warn');
      } catch (err) {
        addLog(`명령 전송 실패 (${cmd}): ${err.message}`, 'err');
      }
    });
  });


}

function connectWS() {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
  const wsURL = `${proto}//${location.host}/admin-ws`; // ✅ 서버와 동일 경로
  const ws = new WebSocket(wsURL);

  ws.addEventListener('open', () => {
    if (wsStatus) {
      wsStatus.textContent = 'WS: 연결됨';
      wsStatus.style.color = '#28c76f';
    }
    addLog('WebSocket 연결됨', 'ok');
  });
  ws.addEventListener('close', () => {
    if (wsStatus) {
      wsStatus.textContent = 'WS: 연결 끊김 (재시도 중)';
      wsStatus.style.color = '#ff9f43';
    }
    addLog('WebSocket 연결 끊김', 'warn');
    setTimeout(connectWS, 1500);
  });
  ws.addEventListener('message', (ev) => {
    try {
      const msg = JSON.parse(ev.data);
      if (msg.type === 'connection_update') {
        if (msg.deviceType === 'phone') renderPhones(msg.list);
        else if (msg.deviceType === 'bus') renderBuses(msg.list);
        else if (msg.deviceType === 'stop') renderStops(msg.list);
        addLog(`실시간 갱신: ${msg.deviceType} ${msg.list?.length ?? 0}건`, 'ok');
      }
      if (msg.type === 'bus_nearby') {
        addLog(`[APP] 근접 알림: ${msg.distance_m}m`);
        return;
      }

      if (msg.type === 'bus_arrived') {
        addLog(`[APP] 도착 알림: ${msg.distance_m}m`);
        return;
      }

      else if (msg.type === 'log') {
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
  // 폴백 주기 갱신(WS 미수신 대비)
  setInterval(() => { refresh('phone'); }, 15000);
  setInterval(() => { refresh('bus'); }, 16000);
  setInterval(() => { refresh('stop'); }, 17000);
  document.querySelectorAll('.quick-actions .quick-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const cmd = btn.dataset.command;
      const id = selected.phone;
      if (!id) return addLog('먼저 휴대단말 목록에서 단말을 클릭하세요', 'warn');

      const payload = { targetType: 'phone', targetId: id, command: cmd };

      try {
        const r = await sendCommand(payload);
        addLog(`휴대단말 빠른 제어 → ${id}: ${cmd}`, r.success ? 'ok' : 'warn');
      } catch (err) {
        addLog(`빠른 제어 실패 (${cmd}): ${err.message}`, 'err');
      }
    });
  });

})();
