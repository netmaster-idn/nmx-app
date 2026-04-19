# NMX Startup Guide

Panduan ini memungkinkan Anda menjalankan aplikasi tanpa perlu mengetik manual `spring-boot:run`.

## Windows

Jalankan aplikasi sekali klik atau dari terminal:

```powershell
.\scripts\start-nmx.bat -Build
```

Untuk menjalankan di background:

```powershell
.\scripts\start-nmx.bat -Build -Background
```

Untuk membuat auto start saat login Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-windows-startup.ps1 -Build
```

Catatan:
- Script akan build JAR bila belum ada atau jika Anda memberi `-Build`.
- Log background disimpan di folder `logs\`.

## Linux / Ubuntu

Jalankan manual tanpa Maven dev mode:

```bash
chmod +x scripts/start-nmx.sh
./scripts/start-nmx.sh --build
```

Jalankan di background:

```bash
./scripts/start-nmx.sh --build --daemon
```

Log background disimpan di `logs/`.

## Ubuntu VPS dengan systemd

Instal service agar aplikasi otomatis hidup saat boot:

```bash
chmod +x deploy/install-nmx-service.sh
./deploy/install-nmx-service.sh
```

Setelah itu kelola service dengan:

```bash
sudo systemctl status nmx
sudo systemctl restart nmx
sudo journalctl -u nmx -f
```

Catatan:
- Service menggunakan JAR tetap di `/opt/nmx/app/nmx.jar`.
- File environment ada di `/etc/nmx/nmx.env`.
