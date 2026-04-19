function sanitizeText(value) {
  return String(value || "").trim().replace(/\s+/g, " ");
}

function sanitizeUsername(value) {
  return String(value || "").trim().toLowerCase();
}

function sanitizeEmail(value) {
  return String(value || "").trim().toLowerCase();
}

function sanitizePhone(value) {
  return String(value || "").trim().replace(/[^\d+]/g, "");
}

function validateRegistrationPayload(payload) {
  const data = {
    companyName: sanitizeText(payload.companyName),
    email: sanitizeEmail(payload.email),
    phone: sanitizePhone(payload.phone),
    picName: sanitizeText(payload.picName),
    username: sanitizeUsername(payload.username),
    password: String(payload.password || "")
  };

  const errors = {};
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const phonePattern = /^\+?\d{9,15}$/;
  const usernamePattern = /^(?=\S+$)[a-zA-Z0-9._-]{4,}$/;

  if (data.companyName.length < 3) {
    errors.companyName = "Nama perusahaan minimal 3 karakter.";
  }

  if (!emailPattern.test(data.email)) {
    errors.email = "Masukkan email yang valid.";
  }

  if (!phonePattern.test(data.phone)) {
    errors.phone = "Masukkan nomor HP yang valid.";
  }

  if (data.picName.length < 3) {
    errors.picName = "Nama PIC minimal 3 karakter.";
  }

  if (!usernamePattern.test(data.username)) {
    errors.username = "Username minimal 4 karakter, tanpa spasi, dan hanya boleh huruf, angka, titik, strip, atau underscore.";
  }

  if (data.password.length < 8) {
    errors.password = "Password minimal 8 karakter.";
  }

  return {
    data,
    errors,
    isValid: Object.keys(errors).length === 0
  };
}

module.exports = {
  validateRegistrationPayload
};
