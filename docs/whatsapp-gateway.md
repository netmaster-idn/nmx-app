# WhatsApp Gateway

Modul ini mengimplementasikan alur login WhatsApp berbasis `whatsapp-web.js` secara terpisah dari aplikasi Spring Boot utama.

## Struktur

- `whatsapp-gateway/src/whatsappService.js`: lifecycle client, QR, auth, reconnect, logout, reset session.
- `whatsapp-gateway/src/sessionStore.js`: state manager persisten dan metadata runtime.
- `whatsapp-gateway/src/server.js`: HTTP API multi-session untuk diakses aplikasi utama.
- `whatsapp-gateway/storage/sessions/session-<session-id>`: session persistent `LocalAuth` per tenant.
- `whatsapp-gateway/storage/runtime/whatsapp-state*.json`: status koneksi terakhir per session.

## Status yang dipakai

- `inactive`
- `initializing`
- `qr_required`
- `authenticated`
- `connected`
- `disconnected`
- `auth_failure`
- `error`

Metadata status yang dipersist:

- `lastConnectedAt`
- `lastDisconnectedAt`
- `lastError`
- `qrUpdatedAt`

## Menjalankan gateway di lokal

1. Masuk ke folder `whatsapp-gateway`
2. Copy `.env.example` menjadi `.env`
3. Install dependency: `npm install`
4. Jalankan service: `npm run start`

Default gateway aktif di `http://127.0.0.1:3001`.

## Integrasi dengan aplikasi utama

Spring Boot memanggil gateway lewat property berikut:

- `NMX_WHATSAPP_GATEWAY_BASE_URL`
- `NMX_WHATSAPP_GATEWAY_TIMEOUT_MS`

Untuk production Ubuntu, disarankan juga mengatur:

- `NMX_WHATSAPP_GATEWAY_BOOTSTRAP_ENABLED=false`

Dengan begitu aplikasi Java tidak mencoba menjalankan `npm install` atau `npm run start` sendiri saat startup.

Endpoint yang disediakan aplikasi utama:

- `GET /api/whatsapp/status`
- `POST /api/whatsapp/init`
- `GET /api/whatsapp/qr`
- `POST /api/whatsapp/qr/regenerate`
- `POST /api/whatsapp/logout`
- `POST /api/whatsapp/reset-session`

## Multi-session tenant

- Aplikasi utama sekarang mengirim header `X-Whatsapp-Session-Id` ke gateway.
- Nilai session id tenant dibentuk sebagai `tenant-<tenant-id>`.
- Setiap tenant punya QR login, status koneksi, folder session, dan file state sendiri.
- `logout` atau `reset-session` tenant A tidak akan memutus session tenant B.
- Jika request tidak membawa konteks tenant, gateway tetap fallback ke session `default` untuk kompatibilitas.

## Catatan deploy production

Pola deploy yang direkomendasikan di Ubuntu:

- jalankan gateway sebagai `systemd` service terpisah
- simpan session di path Linux seperti `/var/lib/nmx/whatsapp/sessions`
- simpan state file di `/var/lib/nmx/whatsapp/runtime/whatsapp-state.json`
- arahkan `WHATSAPP_CHROME_EXECUTABLE_PATH` ke Chromium atau Google Chrome di server

Contoh environment gateway production:

```env
PORT=3001
WHATSAPP_SESSION_ID=default
WHATSAPP_SESSION_ROOT=/var/lib/nmx/whatsapp/sessions
WHATSAPP_STATE_FILE=/var/lib/nmx/whatsapp/runtime/whatsapp-state.json
WHATSAPP_CHROME_EXECUTABLE_PATH=/usr/bin/chromium-browser
WHATSAPP_WEB_CLIENT_TIMEOUT_MS=60000
```

## Catatan migrasi Windows ke Linux

- Folder session Chromium dari Windows tidak boleh diasumsikan kompatibel di Linux.
- Saat pindah ke VPS Ubuntu, siapkan kemungkinan scan QR ulang.
- Setelah session Linux sudah stabil, QR biasanya tidak perlu discan ulang selama data session tetap tersimpan.
