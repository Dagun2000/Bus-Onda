function sendToRaspi(deviceClients, payload) {
  const raspi = deviceClients.get("1"); // ID '1'번 Raspberry Pi
  if (!raspi || raspi.ws.readyState !== 1) {
    console.log("[Raspi] Not connected");
    return false;
  }

  raspi.ws.send(JSON.stringify(payload));
  console.log("[Raspi] Sent:", JSON.stringify(payload));
  return true;
}

module.exports = { sendToRaspi };