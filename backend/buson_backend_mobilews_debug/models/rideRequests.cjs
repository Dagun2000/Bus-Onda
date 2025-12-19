// models/rideRequests.cjs

const rideRequests = new Map();

function createRideRequest({ deviceId, busNumber, direction, userLocation }) {
  const requestId = `R-${Date.now()}-${Math.floor(Math.random() * 9999)}`;
  const req = {
    requestId,
    deviceId,
    busNumber,
    direction,
    userLocation,
    status: 'PENDING',
    createdAt: new Date().toISOString(),
  };
  rideRequests.set(requestId, req);
  return req;
}

function updateStatus(requestId, status) {
  const r = rideRequests.get(requestId);
  if (!r) return null;
  r.status = status;
  return r;
}

function getRequest(requestId) {
  return rideRequests.get(requestId);
}

function listRequests() {
  return [...rideRequests.values()];
}

module.exports = { rideRequests, createRideRequest, updateStatus, getRequest, listRequests };
