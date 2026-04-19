const fs = require("fs");
const path = require("path");
const { STATUS, labelForStatus } = require("./status");

class SessionStore {
  constructor(options) {
    this.sessionId = options.sessionId || "default";
    this.stateFile = path.resolve(options.stateFile);
    this.sessionRoot = path.resolve(options.sessionRoot);
    this.sessionPath = path.join(this.sessionRoot, "session-" + this.sessionId);
    this.state = this.load();
  }

  ensureDirectories() {
    fs.mkdirSync(path.dirname(this.stateFile), { recursive: true });
    fs.mkdirSync(this.sessionRoot, { recursive: true });
  }

  defaultState() {
    return {
      status: STATUS.INACTIVE,
      label: labelForStatus(STATUS.INACTIVE),
      isReady: false,
      hasQr: false,
      qrCode: null,
      sessionId: this.sessionId,
      lastConnectedAt: null,
      lastDisconnectedAt: null,
      lastError: null,
      qrUpdatedAt: null
    };
  }

  load() {
    this.ensureDirectories();
    if (!fs.existsSync(this.stateFile)) {
      const fallback = this.defaultState();
      this.save(fallback);
      return fallback;
    }

    try {
      const parsed = JSON.parse(fs.readFileSync(this.stateFile, "utf8"));
      return Object.assign(this.defaultState(), parsed || {});
    } catch (error) {
      const fallback = this.defaultState();
      fallback.status = STATUS.ERROR;
      fallback.label = labelForStatus(STATUS.ERROR);
      fallback.lastError = "State file tidak dapat dibaca: " + error.message;
      this.save(fallback);
      return fallback;
    }
  }

  save(nextState) {
    this.state = Object.assign(this.defaultState(), nextState || {});
    fs.writeFileSync(this.stateFile, JSON.stringify(this.state, null, 2), "utf8");
    return this.state;
  }

  update(patch) {
    const nextState = Object.assign({}, this.state, patch || {});
    nextState.label = labelForStatus(nextState.status);
    return this.save(nextState);
  }

  getState() {
    return Object.assign({}, this.state);
  }

  clearPersistentSession() {
    fs.rmSync(this.sessionPath, { recursive: true, force: true });
  }
}

module.exports = {
  SessionStore
};

