# NMX VPS Deployment

Dokumen ini menyiapkan deployment ke VPS Ubuntu untuk aplikasi Spring Boot utama dan `whatsapp-gateway` sebagai service terpisah.

## Kebutuhan server

- Ubuntu 22.04/24.04 atau Debian modern
- Java 21
- PostgreSQL 14+
- Nginx
- `iputils-ping`
- Node.js 20+
- `npm`
- Chromium atau Google Chrome

Contoh instalasi paket dasar di Ubuntu:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless postgresql postgresql-client nginx iputils-ping nodejs npm chromium-browser
```

Jika paket `chromium-browser` tidak tersedia pada image Ubuntu Anda, pakai Google Chrome Stable lalu sesuaikan `WHATSAPP_CHROME_EXECUTABLE_PATH`.

## Arsitektur deploy

Production yang direkomendasikan:

- `nmx.service` menjalankan aplikasi Spring Boot pada `127.0.0.1:8080`
- `whatsapp-gateway.service` menjalankan gateway Node.js pada `127.0.0.1:3001`
- Nginx menjadi reverse proxy publik
- PostgreSQL berjalan lokal di VPS atau di server terpisah

Alasan pola ini direkomendasikan:

- restart gateway WhatsApp tidak ikut merestart aplikasi utama
- session WhatsApp lebih mudah dipersist di path Linux khusus
- dependency browser/Node tidak tercampur dengan lifecycle JVM
- production tidak bergantung pada auto-bootstrap `npm install` dari proses Java

## Build artifact

Build dari source:

```bash
./mvnw clean package -DskipTests
```

File hasil build:

```text
target/nmx-0.0.1-SNAPSHOT.jar
```

## Database

Buat database dan user:

```bash
sudo -u postgres psql
CREATE DATABASE nmx_db;
CREATE USER nmx_user WITH ENCRYPTED PASSWORD 'replace-with-strong-password';
GRANT ALL PRIVILEGES ON DATABASE nmx_db TO nmx_user;
\q
```

Import schema awal:

```bash
psql -U nmx_user -d nmx_db -f database/nmx_unified.sql
```

Catatan:

- File `database/nmx_unified.sql` adalah baseline schema awal.
- Aplikasi masih memiliki initializer ringan untuk penyesuaian schema tambahan saat startup.

## Struktur direktori server

```bash
sudo mkdir -p /opt/nmx/app
sudo mkdir -p /var/lib/nmx/uploads/company-logos
sudo mkdir -p /var/lib/nmx/whatsapp/sessions
sudo mkdir -p /var/lib/nmx/whatsapp/runtime
sudo mkdir -p /var/log/nmx
sudo mkdir -p /etc/nmx
sudo groupadd --system nmx || true
sudo useradd --system --home /opt/nmx --shell /usr/sbin/nologin --gid nmx nmx || true
sudo chown -R nmx:nmx /opt/nmx /var/lib/nmx /var/log/nmx
```

## Environment production aplikasi utama

Production menggunakan profile `prod` dari [application-prod.properties](/c:/Projects/nmx/nmx/src/main/resources/application-prod.properties).

Contoh `/etc/nmx/nmx.env`:

```env
SPRING_PROFILES_ACTIVE=prod
NMX_DB_URL=jdbc:postgresql://127.0.0.1:5432/nmx_db
NMX_DB_USERNAME=nmx_user
NMX_DB_PASSWORD=replace-with-strong-password
NMX_SEED_DEFAULT_USERS=false
NMX_COMPANY_LOGO_DIR=/var/lib/nmx/uploads/company-logos
NMX_LOG_FILE=/var/log/nmx/nmx.log
NMX_SESSION_COOKIE_SECURE=true
PORT=8080
NMX_WHATSAPP_GATEWAY_BASE_URL=http://127.0.0.1:3001
NMX_WHATSAPP_GATEWAY_TIMEOUT_MS=15000
NMX_WHATSAPP_GATEWAY_BOOTSTRAP_ENABLED=false
```

Catatan penting:

- `NMX_WHATSAPP_GATEWAY_BOOTSTRAP_ENABLED=false` direkomendasikan di VPS karena gateway dijalankan sebagai service terpisah.
- `NMX_SEED_DEFAULT_USERS=false` direkomendasikan di production.

## Environment production WhatsApp gateway

Contoh `/etc/nmx/whatsapp-gateway.env`:

```env
PORT=3001
WHATSAPP_SESSION_ID=default
WHATSAPP_SESSION_ROOT=/var/lib/nmx/whatsapp/sessions
WHATSAPP_STATE_FILE=/var/lib/nmx/whatsapp/runtime/whatsapp-state.json
WHATSAPP_CHROME_EXECUTABLE_PATH=/usr/bin/chromium-browser
WHATSAPP_WEB_CLIENT_TIMEOUT_MS=60000
```

Jika memakai Google Chrome, path biasanya bisa diubah menjadi:

```env
WHATSAPP_CHROME_EXECUTABLE_PATH=/usr/bin/google-chrome-stable
```

## File yang perlu disalin ke server

- JAR ke `/opt/nmx/app/nmx.jar`
- folder `whatsapp-gateway/` ke `/opt/nmx/app/whatsapp-gateway/`
- `deploy/nmx.env.example` ke `/etc/nmx/nmx.env`
- `deploy/whatsapp-gateway.env.example` ke `/etc/nmx/whatsapp-gateway.env`
- `deploy/nmx.service` ke `/etc/systemd/system/nmx.service`
- `deploy/whatsapp-gateway.service` ke `/etc/systemd/system/whatsapp-gateway.service`
- `deploy/nginx-nmx.conf` ke `/etc/nginx/sites-available/nmx.conf`

## Menjalankan dengan systemd

```bash
sudo systemctl daemon-reload
sudo systemctl enable whatsapp-gateway nmx
sudo systemctl start whatsapp-gateway
sudo systemctl start nmx
sudo systemctl status whatsapp-gateway
sudo systemctl status nmx
```

Lihat log:

```bash
sudo journalctl -u whatsapp-gateway -f
sudo journalctl -u nmx -f
tail -f /var/log/nmx/nmx.log
```

## Reverse proxy Nginx

Aktifkan site:

```bash
sudo ln -s /etc/nginx/sites-available/nmx.conf /etc/nginx/sites-enabled/nmx.conf
sudo nginx -t
sudo systemctl reload nginx
```

Jika memakai HTTPS, lanjutkan dengan Certbot atau reverse proxy yang Anda gunakan.

## Catatan kompatibilitas Linux

- Monitoring ping sudah mendukung Linux, tetapi paket `iputils-ping` wajib ada.
- Upload logo company sudah diarahkan ke path Linux yang bisa diatur lewat env.
- Session WhatsApp sebaiknya disimpan di `/var/lib/nmx/whatsapp/`.
- Jangan mengandalkan copy session Chromium dari Windows ke Linux. Praktiknya lebih aman login ulang dengan scan QR di VPS.

## Installer otomatis

Jika source project sudah ada di server, Anda bisa memakai helper berikut:

```bash
chmod +x deploy/install-nmx-service.sh
./deploy/install-nmx-service.sh
```

Script ini akan:

- build JAR production
- menyalin JAR ke `/opt/nmx/app/nmx.jar`
- menyalin source `whatsapp-gateway` tanpa `node_modules` dan runtime data lokal
- membuat `/etc/nmx/nmx.env` jika belum ada
- membuat `/etc/nmx/whatsapp-gateway.env` jika belum ada
- install dependency Node.js gateway di server
- enable dan restart `whatsapp-gateway` serta `nmx`

## Checklist setelah deploy

1. Pastikan `http://127.0.0.1:3001/health` merespons dari VPS.
2. Buka UI NMX dan cek status WhatsApp.
3. Scan QR ulang bila session baru belum authenticated.
4. Uji kirim pesan manual sebelum mengaktifkan reminder otomatis.
