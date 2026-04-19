require("dotenv").config();

const path = require("path");
const express = require("express");
const helmet = require("helmet");

const registerRoutes = require("./routes/registerRoutes");

const app = express();
const PORT = Number(process.env.PORT || 3000);
const publicDir = path.join(__dirname, "..", "public");

app.disable("x-powered-by");

app.use(
  helmet({
    contentSecurityPolicy: false,
    crossOriginEmbedderPolicy: false
  })
);

app.use(express.json({ limit: "1mb" }));
app.use(express.urlencoded({ extended: false, limit: "1mb" }));
app.use(express.static(publicDir));

app.get("/", (req, res) => {
  res.redirect("/register.html");
});

app.get("/health", (req, res) => {
  res.json({
    success: true,
    message: "Registration service is healthy"
  });
});

app.use("/api/register", registerRoutes);

app.use("/api", (req, res) => {
  res.status(404).json({
    success: false,
    message: "Endpoint tidak ditemukan."
  });
});

app.use((err, req, res, next) => {
  console.error("[registration-service]", err);

  if (res.headersSent) {
    return next(err);
  }

  res.status(err.statusCode || 500).json({
    success: false,
    message: err.publicMessage || "Terjadi kesalahan pada server. Silakan coba lagi."
  });
});

app.listen(PORT, () => {
  console.log(`Registration service running at http://localhost:${PORT}`);
});
