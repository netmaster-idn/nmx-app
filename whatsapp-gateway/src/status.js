const STATUS = Object.freeze({
  INACTIVE: "inactive",
  INITIALIZING: "initializing",
  QR_REQUIRED: "qr_required",
  AUTHENTICATED: "authenticated",
  CONNECTED: "connected",
  DISCONNECTED: "disconnected",
  AUTH_FAILURE: "auth_failure",
  ERROR: "error"
});

const LABELS = Object.freeze({
  [STATUS.INACTIVE]: "Nonaktif",
  [STATUS.INITIALIZING]: "Connecting",
  [STATUS.QR_REQUIRED]: "QR Required",
  [STATUS.AUTHENTICATED]: "Authenticated",
  [STATUS.CONNECTED]: "Connected",
  [STATUS.DISCONNECTED]: "Disconnected",
  [STATUS.AUTH_FAILURE]: "Auth Failure",
  [STATUS.ERROR]: "Error"
});

function labelForStatus(status) {
  return LABELS[status] || LABELS[STATUS.ERROR];
}

module.exports = {
  STATUS,
  labelForStatus
};
