const express = require("express");

const { registerCompany } = require("../controllers/registerController");

const router = express.Router();

router.post("/", registerCompany);

module.exports = router;
