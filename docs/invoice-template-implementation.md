# Invoice Template Implementation

## 1. Ringkasan Analisis Template

### Pilihan stack
Project existing sudah menggunakan Spring Boot + Thymeleaf + PostgreSQL + JPA/Hibernate, jadi implementasi invoice document dibuat langsung di stack ini agar:

- tidak membuat modul invoice terpisah dari billing yang sudah ada
- tetap kompatibel dengan `invoices`, `customers`, `customer_services`, dan `company_profiles`
- mudah dirender server-side sebagai HTML printable tanpa menambah runtime baru

### Bagian template invoice yang diidentifikasi

| Bagian | Elemen template | Status |
|---|---|---|
| Informasi perusahaan / brand | `.brand-name`, `.brand-tag`, logo/brand icon | Dinamis |
| Informasi invoice | `.title`, `.subtitle`, `Date`, `Due Date`, `Invoice No`, `Account No` | Dinamis |
| Informasi customer / penerima | blok `To` pada sidebar | Dinamis |
| Metode pembayaran | `.payment-row .payment-text` | Dinamis |
| Item invoice | `tbody > tr` | Dinamis loop |
| Subtotal / pajak / total | `.summary-left`, `.summary-right` | Dinamis |
| Footer / kontak / alamat | `.footer-left`, `.contact-item` | Dinamis |
| QR code / payment matrix | `.qr-wrap` | Dinamis |

### Ringkasan keputusan implementasi

- Template statis dari file referensi dipertahankan layout dan rasio komponennya di [`src/main/resources/templates/finance/invoice-document.html`](/c:/Projects/nmx/nmx/src/main/resources/templates/finance/invoice-document.html).
- Data billing existing tetap dipakai sebagai sumber utama.
- Field yang belum tersedia pada schema existing dibuat sebagai ekstensi invoice document:
  - `payment_methods`
  - `bank_accounts`
  - `company_settings`
  - `invoice_items`
  - `invoice_qr_codes`
  - kolom tambahan di `invoices`
- `company_profiles` diperlakukan sebagai tabel perusahaan aktif, sehingga tidak dibuat tabel `companies` baru agar tidak menduplikasi source of truth.
- `customers` sudah punya `full_name`, `phone`, `email`, `installation_address`, sehingga `customer_contacts` belum wajib untuk template ini.

## 2. Daftar Field Dinamis

### Daftar field yang dijadikan dinamis

| Field tampilan | Sumber utama | Status |
|---|---|---|
| Nama perusahaan | `company_profiles.name` | Existing |
| Tagline perusahaan | `company_profiles.tagline` | Existing |
| Logo perusahaan | `company_profiles.logo` | Existing |
| Alamat perusahaan | `company_profiles.address` | Existing |
| Telepon perusahaan | `company_profiles.phone` | Existing |
| Email perusahaan | `company_profiles.support_email` lalu `company_profiles.email` | Existing |
| Website perusahaan | `company_profiles.website` | Existing |
| Judul dokumen | `company_settings.default_invoice_title` | Perlu dibuat |
| Subtitle dokumen | `invoices.document_subtitle` lalu `company_settings.default_invoice_subtitle` | Perlu dibuat |
| Tanggal invoice | `invoices.issue_date` | Perlu dibuat |
| Jatuh tempo | `invoices.due_date` | Existing |
| Nomor invoice | `invoices.invoice_number` | Existing |
| Nomor rekening tampil | `bank_accounts.account_number` | Perlu dibuat |
| Nama metode pembayaran | `payment_methods.name` atau `invoices.payment_method` | Perlu dibuat / existing fallback |
| Nama pemilik rekening | `bank_accounts.account_name` | Perlu dibuat |
| Rekening terformat | `bank_accounts.bank_name` + `bank_accounts.account_number` | Perlu dibuat |
| Alamat/payment branch | `bank_accounts.branch_address` | Perlu dibuat |
| Label referensi pembayaran | `bank_accounts.payment_reference_label` | Perlu dibuat |
| Nomor referensi pembayaran | `invoices.reference_number` lalu `invoices.invoice_number` | Perlu dibuat |
| Nama customer | `customers.full_name` | Existing |
| Telepon customer | `customers.phone` | Existing |
| Email customer | `customers.email` | Existing |
| Alamat customer | `customers.installation_address` | Existing |
| Deskripsi item | `invoice_items.description` | Perlu dibuat |
| Rate item | `invoice_items.rate` | Perlu dibuat |
| Unit item | `invoice_items.unit_name` | Perlu dibuat |
| Quantity item | `invoice_items.quantity` | Perlu dibuat |
| Subtotal item | `invoice_items.subtotal` | Perlu dibuat |
| Subtotal invoice | `invoices.subtotal_amount` | Perlu dibuat |
| Tax rate | `invoices.tax_rate` lalu `company_settings.default_tax_rate` | Perlu dibuat |
| Tax amount | `invoices.tax_amount` | Perlu dibuat |
| Total akhir | `invoices.total_amount` | Existing |
| Footer note | `invoices.notes` lalu `company_settings.default_footer_note` | Existing / perlu dibuat |
| QR payload | `invoice_qr_codes.payload` lalu generated payload dari invoice | Perlu dibuat |
| QR image data URI | `invoice_qr_codes.image_data_uri` atau generator runtime | Perlu dibuat |

### Mapping sumber tabel/kolom

| Bagian template | Selector / lokasi elemen | Nama field tampilan | Sumber database | Fallback | Catatan |
|---|---|---|---|---|---|
| Brand | `.brand-name` | Nama perusahaan | `company_profiles.name` | `Data Belum di Set` | Primary company atau company dari ODP customer |
| Brand | `.brand-tag` | Tagline perusahaan | `company_profiles.tagline` | `Data Belum di Set` | Tetap tampil walau null |
| Brand | `.brand-icon img` | Logo perusahaan | `company_profiles.logo` | Inisial company | URL dibentuk oleh `CompanyProfile.getLogoUrl()` |
| Header | `.title` | Judul dokumen | `company_settings.default_invoice_title` | `INVOICE` | Default global company |
| Header | `.subtitle` | Subtitle dokumen | `invoices.document_subtitle` | `Document Payment Information` | Override per invoice |
| Sidebar | `Date` value | Tanggal invoice | `invoices.issue_date` | `Data Belum di Set` | Fallback ke `created_at`/`billing_month` |
| Sidebar | `Due Date` value | Jatuh tempo | `invoices.due_date` | `Data Belum di Set` | Existing |
| Sidebar | `To .name` | Nama customer | `customers.full_name` | `Data Belum di Set` | Existing |
| Sidebar | `To line 2` | Telepon customer | `customers.phone` | `Data Belum di Set` | Existing |
| Sidebar | `To line 3` | Email customer | `customers.email` | `Data Belum di Set` | Existing |
| Sidebar | `To line 4` | Alamat customer | `customers.installation_address` | `Data Belum di Set` | Existing |
| Meta box | `.meta-col:first-child .meta-value` | Account No | `bank_accounts.account_number` | `Data Belum di Set` | Rekening utama company |
| Meta box | `.meta-col:last-child .meta-small` | Invoice No | `invoices.invoice_number` | `Data Belum di Set` | Existing |
| Payment method | `.payment-text div:nth-child(1)` | Nama metode | `payment_methods.name` | `Data Belum di Set` | fallback string existing `invoices.payment_method` |
| Payment method | `.payment-text div:nth-child(2)` | Nama akun | `bank_accounts.account_name` | `Data Belum di Set` | Existing invoice string tidak cukup |
| Payment method | `.payment-text div:nth-child(3)` | Bank + no rekening | `bank_accounts.bank_name` + `bank_accounts.account_number` | `Data Belum di Set` | Perlu dibuat |
| Payment method | `.payment-text div:nth-child(4)` | Alamat / instruksi | `bank_accounts.branch_address` | `Data Belum di Set` | Perlu dibuat |
| Payment method | `.payment-text div:nth-child(5)` | Referensi bayar | `invoices.reference_number` | `Data Belum di Set` | Perlu dibuat |
| Item row | `tbody tr td:first-child` | Item description | `invoice_items.description` | `Data Belum di Set` | Jika kosong pakai placeholder row |
| Item row | `tbody tr td:nth-child(2)` | Rate | `invoice_items.rate` | `0` | format uang |
| Item row | `tbody tr td:nth-child(3)` | Unit | `invoice_items.unit_name` | `Data Belum di Set` | Perlu dibuat |
| Item row | `tbody tr td:nth-child(4)` | Subtotal | `invoice_items.subtotal` | `0` | format uang |
| Summary | `.summary-right div:nth-child(1)` | Subtotal | `invoices.subtotal_amount` | `0` | hitung dari item jika null |
| Summary | `.summary-right div:nth-child(2)` | Pajak | `invoices.tax_amount` | `0` | hitung dari tax rate jika null |
| Summary | `.summary-right div:nth-child(3)` | Total | `invoices.total_amount` | `0` | existing |
| Footer | `.footer-left` | Catatan footer | `invoices.notes` lalu `company_settings.default_footer_note` | `Data Belum di Set` | tetap aman walau kosong |
| Footer | kontak email | Email footer | `company_profiles.support_email` lalu `company_profiles.email` | `Data Belum di Set` | existing |
| Footer | kontak address | Alamat footer | `company_profiles.address` | `Data Belum di Set` | existing |
| QR | `.qr-wrap img` | QR / payment matrix image | `invoice_qr_codes.image_data_uri` | generated runtime | payload dari invoice/reference |

## 3. Skema Database Lengkap

### Tabel existing yang dipakai ulang

| Tabel | Fungsi |
|---|---|
| `company_profiles` | tabel perusahaan aktif, berperan sebagai `companies` |
| `customers` | identitas customer + primary contact |
| `customer_services` | konteks layanan customer |
| `invoices` | header invoice |
| `payments` | histori pembayaran |

### Tabel baru yang ditambahkan

#### `payment_methods`

| Kolom | Tipe | Null | Default |
|---|---|---|---|
| `id` | `BIGSERIAL` | No | auto |
| `company_profile_id` | `BIGINT` | Yes | `NULL` |
| `code` | `VARCHAR(50)` | Yes | none |
| `name` | `VARCHAR(100)` | No | `Data Belum di Set` |
| `channel_type` | `VARCHAR(50)` | Yes | `Data Belum di Set` |
| `instructions` | `TEXT` | Yes | `Data Belum di Set` |
| `notes` | `TEXT` | Yes | `Data Belum di Set` |
| `is_active` | `BOOLEAN` | No | `TRUE` |

#### `bank_accounts`

| Kolom | Tipe | Null | Default |
|---|---|---|---|
| `id` | `BIGSERIAL` | No | auto |
| `company_profile_id` | `BIGINT` | Yes | `NULL` |
| `payment_method_id` | `BIGINT` | Yes | `NULL` |
| `bank_name` | `VARCHAR(120)` | Yes | `Data Belum di Set` |
| `account_name` | `VARCHAR(150)` | Yes | `Data Belum di Set` |
| `account_number` | `VARCHAR(80)` | Yes | `Data Belum di Set` |
| `branch_address` | `TEXT` | Yes | `Data Belum di Set` |
| `swift_code` | `VARCHAR(50)` | Yes | `Data Belum di Set` |
| `payment_reference_label` | `VARCHAR(150)` | Yes | `Payment Reference` |
| `instructions` | `TEXT` | Yes | `Data Belum di Set` |
| `is_primary` | `BOOLEAN` | No | `FALSE` |
| `is_active` | `BOOLEAN` | No | `TRUE` |

#### `company_settings`

| Kolom | Tipe | Null | Default |
|---|---|---|---|
| `id` | `BIGSERIAL` | No | auto |
| `company_profile_id` | `BIGINT` | Yes | `NULL` |
| `default_currency_code` | `VARCHAR(10)` | Yes | `IDR` |
| `default_locale_code` | `VARCHAR(20)` | Yes | `id-ID` |
| `default_tax_rate` | `DECIMAL(5,2)` | Yes | `0` |
| `default_invoice_title` | `VARCHAR(100)` | Yes | `INVOICE` |
| `default_invoice_subtitle` | `VARCHAR(200)` | Yes | `Document Payment Information` |
| `default_footer_note` | `TEXT` | Yes | `Data Belum di Set` |
| `is_active` | `BOOLEAN` | No | `TRUE` |

#### `invoice_items`

| Kolom | Tipe | Null | Default |
|---|---|---|---|
| `id` | `BIGSERIAL` | No | auto |
| `invoice_id` | `BIGINT` | No | none |
| `description` | `VARCHAR(255)` | No | `Data Belum di Set` |
| `rate` | `DECIMAL(12,2)` | No | `0` |
| `quantity` | `DECIMAL(12,2)` | No | `1` |
| `unit_name` | `VARCHAR(80)` | No | `Data Belum di Set` |
| `subtotal` | `DECIMAL(12,2)` | No | `0` |
| `sort_order` | `INT` | Yes | `0` |
| `notes` | `TEXT` | Yes | `Data Belum di Set` |

#### `invoice_qr_codes`

| Kolom | Tipe | Null | Default |
|---|---|---|---|
| `id` | `BIGSERIAL` | No | auto |
| `invoice_id` | `BIGINT` | No | none |
| `code_format` | `VARCHAR(50)` | Yes | `DATA_MATRIX` |
| `payload` | `TEXT` | Yes | `NULL` |
| `image_data_uri` | `TEXT` | Yes | `NULL` |
| `notes` | `TEXT` | Yes | `Data Belum di Set` |
| `is_active` | `BOOLEAN` | No | `TRUE` |

### Kolom baru pada `invoices`

| Kolom | Tipe | Null | Default |
|---|---|---|---|
| `company_profile_id` | `BIGINT` | Yes | `NULL` |
| `payment_method_id` | `BIGINT` | Yes | `NULL` |
| `bank_account_id` | `BIGINT` | Yes | `NULL` |
| `issue_date` | `DATE` | Yes | populated by migration |
| `subtotal_amount` | `DECIMAL(12,2)` | Yes | `0` |
| `tax_rate` | `DECIMAL(5,2)` | Yes | `0` |
| `tax_amount` | `DECIMAL(12,2)` | Yes | `0` |
| `reference_number` | `VARCHAR(100)` | Yes | `NULL` |
| `currency_code` | `VARCHAR(10)` | Yes | `IDR` |
| `document_subtitle` | `VARCHAR(200)` | Yes | `Document Payment Information` |

### Relasi antar tabel

- `company_profiles (1) -> (n) payment_methods`
- `company_profiles (1) -> (n) bank_accounts`
- `company_profiles (1) -> (n) company_settings`
- `company_profiles (1) -> (n) invoices`
- `payment_methods (1) -> (n) bank_accounts`
- `payment_methods (1) -> (n) invoices`
- `bank_accounts (1) -> (n) invoices`
- `customers (1) -> (n) invoices`
- `invoices (1) -> (n) invoice_items`
- `invoices (1) -> (n) invoice_qr_codes`

## 4. SQL Migration / ORM Schema

### Lokasi implementasi

- ORM entity:
  - [`Invoice.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/model/Invoice.java)
  - [`PaymentMethod.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/model/PaymentMethod.java)
  - [`BankAccount.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/model/BankAccount.java)
  - [`CompanySetting.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/model/CompanySetting.java)
  - [`InvoiceItem.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/model/InvoiceItem.java)
  - [`InvoiceQrCode.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/model/InvoiceQrCode.java)
- Runtime migration:
  - [`InvoiceDocumentSchemaInitializer.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/config/InvoiceDocumentSchemaInitializer.java)
- Unified SQL baseline:
  - [`database/nmx_unified.sql`](/c:/Projects/nmx/nmx/database/nmx_unified.sql)

### Ringkasan migration SQL

```sql
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS issue_date DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tax_rate DECIMAL(5,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS currency_code VARCHAR(10) DEFAULT 'IDR';

CREATE TABLE IF NOT EXISTS payment_methods (...);
CREATE TABLE IF NOT EXISTS bank_accounts (...);
CREATE TABLE IF NOT EXISTS company_settings (...);
CREATE TABLE IF NOT EXISTS invoice_items (...);
CREATE TABLE IF NOT EXISTS invoice_qr_codes (...);
```

### Catatan schema

- Text display columns memakai default `Data Belum di Set` agar aman jika row dibuat dulu dan value diisi belakangan.
- Unique / identifier columns seperti `payment_methods.code` dan `invoices.invoice_number` tidak diberi default string umum agar tidak menimbulkan collision.

## 5. Query Pengambilan Data

### Query repository production

Repository utama untuk invoice document memakai entity graph:

- [`InvoiceRepository.findDocumentById(...)`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/repository/InvoiceRepository.java)

JPQL:

```java
@EntityGraph(attributePaths = {
    "customer",
    "customerService",
    "customerService.odp",
    "customerService.odp.companyProfile",
    "companyProfile",
    "paymentMethodEntity",
    "bankAccount",
    "bankAccount.paymentMethod"
})
@Query("SELECT i FROM Invoice i WHERE i.id = :id")
Optional<Invoice> findDocumentById(@Param("id") Long id);
```

### SQL join ekuivalen

```sql
SELECT i.*,
       c.full_name,
       c.phone,
       c.email,
       c.installation_address,
       cp.name AS company_name,
       cp.address AS company_address,
       cp.phone AS company_phone,
       cp.support_email,
       pm.name AS payment_method_name,
       ba.bank_name,
       ba.account_name,
       ba.account_number
FROM invoices i
LEFT JOIN customers c ON c.id = i.customer_id
LEFT JOIN company_profiles cp ON cp.id = COALESCE(i.company_profile_id, (
    SELECT o.company_profile_id
    FROM customer_services cs
    LEFT JOIN odps o ON o.id = cs.odp_id
    WHERE cs.id = i.customer_service_id
))
LEFT JOIN payment_methods pm ON pm.id = i.payment_method_id
LEFT JOIN bank_accounts ba ON ba.id = i.bank_account_id
WHERE i.id = :invoiceId;
```

## 6. Helper Fallback

### Lokasi helper

- [`InvoiceDocumentSupport.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/service/InvoiceDocumentSupport.java)

### Aturan yang diterapkan

- `safeValue(value)`:
  - jika `null`, blank, atau hanya spasi -> `Data Belum di Set`
- `safeNumber(value)`:
  - jika `null` -> `0`
- `safeDate(value, fallback)`:
  - jika tanggal utama `null`, ambil fallback
- `formatMoney(amount, currency, locale)`:
  - konsisten untuk tampilan uang
- `formatDate(date, locale)`:
  - konsisten untuk tampilan tanggal

### Fallback item invoice

Jika `invoice_items` kosong:

- generator akan mencoba membangun item dari kolom legacy:
  - `monthly_fee`
  - `installation_fee`
  - `other_charges`
- jika semuanya kosong / nol, tetap render 1 row placeholder:
  - `description = "Data Belum di Set"`
  - `rate = 0`
  - `unit = "Data Belum di Set"`
  - `subtotal = 0`

## 7. Template HTML Final Versi Dinamis

### Lokasi template final

- [`invoice-document.html`](/c:/Projects/nmx/nmx/src/main/resources/templates/finance/invoice-document.html)

### Refactor yang dilakukan

- semua hardcoded field pada template acuan diganti `th:text`, `th:src`, dan `th:each`
- loop item invoice sekarang memakai `document.items`
- blok QR sekarang mengambil `document.qrCodeDataUri`
- layout, grid, spacing, dan style dasar template referensi tetap dipertahankan
- tetap printable karena ini server-rendered HTML khusus dokumen

### Alasan memilih Thymeleaf

- project existing sudah memakai Thymeleaf
- invoice document perlu SSR yang stabil untuk print/export
- mapping data dari JPA ke template lebih langsung dan tidak butuh stack tambahan

## 8. Service / Controller Untuk Generate Invoice

### Service utama

- [`InvoiceDocumentService.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/service/InvoiceDocumentService.java)

Tugas service:

- ambil invoice lengkap
- resolve company aktif
- resolve company settings
- resolve payment method dan bank account
- resolve item invoice
- hitung subtotal, pajak, total
- format tanggal dan mata uang
- generate QR / payment matrix image data URI
- membangun `InvoiceDocumentView`

### Controller

- [`InvoiceDocumentController.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/controller/InvoiceDocumentController.java)

Endpoint:

- `GET /finance/invoice/{id}/document`
  - render HTML dokumen invoice
- `GET /api/invoices/{id}/document`
  - return JSON invoice document view

### Integrasi ke halaman invoice existing

- [`finance/invoice.html`](/c:/Projects/nmx/nmx/src/main/resources/templates/finance/invoice.html)

Ditambahkan quick action:

- `Dokumen Invoice`
- JS helper `openInvoiceDocument(invoiceId)`

## 9. Catatan Implementasi dan Asumsi

- Saya sengaja tidak membuat tabel `companies` baru karena `company_profiles` sudah menjadi source of truth company di project ini.
- Saya juga tidak membuat `customer_contacts` terpisah karena template invoice saat ini cukup memakai `customers.full_name`, `customers.phone`, `customers.email`, dan `customers.installation_address`.
- QR slot saat ini diisi oleh generator matrix offline di [`InvoiceMatrixCodeService.java`](/c:/Projects/nmx/nmx/src/main/java/com/netmaster/nmx/service/InvoiceMatrixCodeService.java), sehingga dokumen tetap mandiri tanpa layanan eksternal. Format default yang disimpan adalah `DATA_MATRIX`; ini sengaja dipilih agar tidak perlu menambah dependency baru di build.
- Semua render text tetap aman walau data kosong karena view-model dibangun lewat helper fallback.
- Untuk invoice lama yang belum punya `invoice_items`, dokumen tetap bisa dirender dari kolom legacy (`monthly_fee`, `installation_fee`, `other_charges`).
- Build sudah diverifikasi dengan:
  - `.\mvnw -q -DskipTests compile`
