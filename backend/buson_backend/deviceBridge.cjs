const activeSockets = new Map();

function pushToDevice(busNumber, payload) {
    const ws = activeSockets.get(busNumber);
    if (!ws || ws.readyState !== 1) return false;

    ws.send(JSON.stringify(payload));
    return true;
}

module.exports = { pushToDevice, activeSockets };
