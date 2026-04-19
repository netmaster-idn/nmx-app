const bcrypt = require("bcrypt");

const pool = require("../db/pool");
const { validateRegistrationPayload } = require("../utils/registerValidator");

async function registerCompany(req, res, next) {
  const { data, errors, isValid } = validateRegistrationPayload(req.body || {});

  if (!isValid) {
    return res.status(422).json({
      success: false,
      message: "Periksa kembali data registrasi Anda.",
      errors
    });
  }

  let connection;

  try {
    connection = await pool.getConnection();
    await connection.beginTransaction();

    const [existingCompany] = await connection.execute(
      "SELECT id FROM companies WHERE email = ? LIMIT 1",
      [data.email]
    );

    if (existingCompany.length > 0) {
      await connection.rollback();
      return res.status(409).json({
        success: false,
        message: "Email sudah digunakan.",
        errors: {
          email: "Email sudah terdaftar."
        }
      });
    }

    const [existingUser] = await connection.execute(
      "SELECT id FROM users WHERE username = ? LIMIT 1",
      [data.username]
    );

    if (existingUser.length > 0) {
      await connection.rollback();
      return res.status(409).json({
        success: false,
        message: "Username sudah digunakan.",
        errors: {
          username: "Username sudah dipakai."
        }
      });
    }

    const [companyResult] = await connection.execute(
      `INSERT INTO companies (company_name, email, phone, pic_name, status)
       VALUES (?, ?, ?, ?, 'trial')`,
      [data.companyName, data.email, data.phone, data.picName]
    );

    const companyId = companyResult.insertId;
    const passwordHash = await bcrypt.hash(
      data.password,
      Number(process.env.BCRYPT_ROUNDS || 10)
    );

    await connection.execute(
      `INSERT INTO users (company_id, name, username, password_hash, role, is_active)
       VALUES (?, ?, ?, ?, 'owner', 1)`,
      [companyId, data.picName, data.username, passwordHash]
    );

    await connection.commit();

    return res.status(201).json({
      success: true,
      message: "Registrasi berhasil. Silakan login."
    });
  } catch (error) {
    if (connection) {
      await connection.rollback();
    }

    error.statusCode = 500;
    error.publicMessage = "Registrasi gagal diproses. Silakan coba beberapa saat lagi.";
    return next(error);
  } finally {
    if (connection) {
      connection.release();
    }
  }
}

module.exports = {
  registerCompany
};
