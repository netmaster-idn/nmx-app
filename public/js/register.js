const registerForm = document.getElementById("registerForm");
const submitButton = document.getElementById("submitButton");
const submitLabel = document.getElementById("submitLabel");
const formAlert = document.getElementById("formAlert");
const togglePassword = document.getElementById("togglePassword");
const passwordInput = document.getElementById("password");
const confirmPasswordInput = document.getElementById("confirmPassword");
const confirmPasswordStatus = document.getElementById("confirmPasswordStatus");
const passwordRuleLength = document.getElementById("passwordRuleLength");
const passwordRuleUppercase = document.getElementById("passwordRuleUppercase");
const passwordRuleSpecial = document.getElementById("passwordRuleSpecial");

function resolveBackendBaseUrl() {
  const { protocol, hostname, port, origin } = window.location;
  if (port === "3000") {
    return `${protocol}//${hostname}:8080`;
  }
  return origin;
}

const backendBaseUrl = resolveBackendBaseUrl();

const fieldNames = [
  "companyName",
  "email",
  "phone",
  "picName",
  "username",
  "password",
  "confirmPassword"
];

function clearErrors() {
  fieldNames.forEach((field) => {
    const errorNode = document.querySelector(`[data-error-for="${field}"]`);
    if (errorNode) {
      errorNode.textContent = "";
    }
  });
}

function showErrors(errors = {}) {
  clearErrors();
  Object.entries(errors).forEach(([field, message]) => {
    const errorNode = document.querySelector(`[data-error-for="${field}"]`);
    if (errorNode) {
      errorNode.textContent = message;
    }
  });
}

function showAlert(type, message) {
  formAlert.className = "register-alert " + (type === "success" ? "success" : "error");
  formAlert.textContent = message;
}

function hideAlert() {
  formAlert.classList.add("hidden");
}

function hasMinimumLength(password) {
  return String(password || "").length >= 8;
}

function hasUppercase(password) {
  return /[A-Z]/.test(String(password || ""));
}

function hasSpecialCharacter(password) {
  return /[^A-Za-z0-9]/.test(String(password || ""));
}

function setPasswordRuleState(node, isValid) {
  if (!node) {
    return;
  }
  node.className = isValid ? "password-rule is-valid" : "password-rule";

  const icon = node.querySelector(".password-rule-icon");
  if (icon) {
    icon.textContent = isValid ? "*" : ".";
  }
}

function updatePasswordGuide(password) {
  setPasswordRuleState(passwordRuleLength, hasMinimumLength(password));
  setPasswordRuleState(passwordRuleUppercase, hasUppercase(password));
  setPasswordRuleState(passwordRuleSpecial, hasSpecialCharacter(password));
}

function updateConfirmPasswordStatus() {
  if (!confirmPasswordStatus || !confirmPasswordInput) {
    return;
  }

  const password = passwordInput ? passwordInput.value || "" : "";
  const confirmPassword = confirmPasswordInput.value || "";

  if (!confirmPassword) {
    confirmPasswordStatus.classList.add("hidden");
    confirmPasswordStatus.textContent = "incorrect password";
    return;
  }

  if (password === confirmPassword) {
    confirmPasswordStatus.className = "confirm-password-status is-valid";
    confirmPasswordStatus.textContent = "password match";
    return;
  }

  confirmPasswordStatus.className = "confirm-password-status";
  confirmPasswordStatus.textContent = "incorrect password";
}

function validateForm(values) {
  const errors = {};
  const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  const phonePattern = /^\+?\d{9,15}$/;
  const usernamePattern = /^(?=\S+$)[a-zA-Z0-9._-]{4,}$/;

  if ((values.companyName || "").trim().length < 3) {
    errors.companyName = "Nama perusahaan minimal 3 karakter.";
  }

  if (!emailPattern.test((values.email || "").trim().toLowerCase())) {
    errors.email = "Masukkan email yang valid.";
  }

  const normalizedPhone = (values.phone || "").trim().replace(/[^\d+]/g, "");
  if (!phonePattern.test(normalizedPhone)) {
    errors.phone = "Masukkan nomor HP yang valid.";
  }

  if ((values.picName || "").trim().length < 3) {
    errors.picName = "Nama PIC minimal 3 karakter.";
  }

  if (!usernamePattern.test((values.username || "").trim())) {
    errors.username = "Username minimal 4 karakter dan tidak boleh mengandung spasi.";
  }

  if ((values.password || "").length < 8) {
    errors.password = "Password minimal 8 karakter.";
  } else if (!hasUppercase(values.password || "")) {
    errors.password = "Password harus memiliki minimal 1 huruf besar.";
  } else if (!hasSpecialCharacter(values.password || "")) {
    errors.password = "Password harus memiliki minimal 1 karakter spesial.";
  }

  if ((values.confirmPassword || "").length < 8) {
    errors.confirmPassword = "Repeat password minimal 8 karakter.";
  } else if ((values.password || "") !== (values.confirmPassword || "")) {
    errors.confirmPassword = "Password dan repeat password tidak sama.";
  }

  return errors;
}

function setLoadingState(isLoading) {
  submitButton.disabled = isLoading;
  submitLabel.textContent = isLoading ? "Memproses Registrasi..." : "Daftarkan Perusahaan";
}

togglePassword.addEventListener("click", () => {
  const isHidden = passwordInput.type === "password";
  passwordInput.type = isHidden ? "text" : "password";
  if (confirmPasswordInput) {
    confirmPasswordInput.type = isHidden ? "text" : "password";
  }
  togglePassword.textContent = isHidden ? "Sembunyikan Password" : "Lihat Password";
});

if (passwordInput) {
  passwordInput.addEventListener("input", () => {
    updatePasswordGuide(passwordInput.value || "");
    updateConfirmPasswordStatus();
  });
}

if (confirmPasswordInput) {
  confirmPasswordInput.addEventListener("input", updateConfirmPasswordStatus);
}

updatePasswordGuide(passwordInput ? passwordInput.value || "" : "");
updateConfirmPasswordStatus();

registerForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  hideAlert();

  const formData = new FormData(registerForm);
  const values = Object.fromEntries(formData.entries());
  const errors = validateForm(values);

  if (Object.keys(errors).length > 0) {
    showErrors(errors);
    showAlert("error", "Periksa kembali data registrasi Anda.");
    return;
  }

  clearErrors();
  setLoadingState(true);

  try {
    const payload = {
      companyName: (values.companyName || "").trim(),
      email: (values.email || "").trim().toLowerCase(),
      phone: (values.phone || "").trim(),
      ownerName: (values.picName || "").trim(),
      ownerEmail: (values.email || "").trim().toLowerCase(),
      ownerUsername: (values.username || "").trim(),
      password: values.password || "",
      confirmPassword: values.confirmPassword || "",
      requestedPlanCode: "TRIAL"
    };

    const response = await fetch(`${backendBaseUrl}/register-tenant`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload)
    });

    const result = await response.json();

    if (!response.ok) {
      showErrors(result.errors || {});
      showAlert("error", result.message || "Registrasi gagal diproses.");
      return;
    }

    registerForm.reset();
    updatePasswordGuide("");
    updateConfirmPasswordStatus();
    showAlert("success", result.message || "Registrasi perusahaan berhasil dikirim dan sedang menunggu approval superadmin.");
    setTimeout(() => {
      window.location.href = `${backendBaseUrl}/login`;
    }, 1500);
  } catch (error) {
    showAlert("error", "Registrasi gagal diproses. Silakan coba beberapa saat lagi.");
  } finally {
    setLoadingState(false);
  }
});
