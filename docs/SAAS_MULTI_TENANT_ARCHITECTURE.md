# SaaS Multi-Tenant Architecture

## Assumptions

- Stack tetap Spring Boot 3 + Spring Security + Spring Data JPA + PostgreSQL.
- Modul existing di `com.netmaster.nmx.model` diperlakukan sebagai tenant domain.
- Control plane SaaS baru disimpan di master database melalui package `com.netmaster.nmx.master.*`.
- Migrasi modul operasional existing ke tenant DB dilakukan bertahap, bukan big bang rewrite.
- Endpoint baru SaaS memakai JSON API dan session-based middleware agar coexist dengan login web existing.

## Architecture Overview

- `master database`
  - menyimpan superadmin, registrasi tenant, directory tenant, metadata koneksi tenant, subscription, audit, approval history.
- `tenant database`
  - 1 tenant = 1 database PostgreSQL terpisah.
  - semua tabel operasional ISP hidup di sini.
- `tenant resolver`
  - resolve tenant dari `X-Tenant-Slug`, parameter `tenantSlug`, session, atau subdomain.
- `tenant routing datasource`
  - `TenantRoutingDataSource` + `TenantContextHolder` menentukan koneksi database tenant per request.
- `master control plane`
  - superadmin hanya query data tenant via master DB.
  - kalau perlu akses data operasional, superadmin harus pilih tenant dulu lalu masuk support context.

## Core Flow

### Registration Flow

1. ISP submit `POST /register-tenant`.
2. data masuk ke `isp_registrations` dengan status `PENDING`.
3. event dicatat ke `approval_history` dan `audit_logs`.
4. superadmin melihat daftar pending di `/superadmin/tenants/pending`.

### Approval Flow

1. superadmin login ke master DB.
2. superadmin approve `POST /superadmin/tenants/{id}/approve`.
3. sistem membuat record `tenants`.
4. sistem membuat database tenant baru.
5. sistem menjalankan baseline schema tenant.
6. sistem seed role + tenant admin default.
7. metadata koneksi disimpan di `tenant_databases`.
8. audit log approval dicatat.

### Tenant Login Flow

1. tenant kirim `POST /tenant/login` dengan `tenantSlug`, `username`, `password`.
2. system resolve tenant dari master DB.
3. tenant wajib `ACTIVE`.
4. `TenantConnectionManager` switch context ke tenant DB.
5. user divalidasi di tabel `users` tenant DB.
6. session tenant dibuat.

## Database Design

### Master Database Tables

- `superadmins`
- `isp_registrations`
- `tenants`
- `tenant_databases`
- `subscription_plans`
- `tenant_users_index`
- `audit_logs`
- `approval_history`

### Tenant Database Tables

- `users`
- `roles`
- `user_roles`
- `customers`
- `internet_packages`
- `invoices`
- `payments`
- `network_devices`
- `tickets`
- `projects`
- `monitoring_logs`
- `settings`

## Key Components

- `MasterPersistenceConfig`
  - JPA + transaction manager untuk master DB.
- `TenantPersistenceConfig`
  - routing datasource untuk tenant DB.
- `TenantConnectionManager`
  - register datasource tenant dan execute code dalam tenant context.
- `TenantProvisioningService`
  - create DB, migrate, seed, create tenant admin.
- `TenantApprovalService`
  - approve/reject tenant registration.
- `SuperAdminTenantAccessService`
  - summary tenant, CRUD master tenant directory, support context.
- `TenantContextMiddleware`
  - resolve tenant sebelum controller tenant/superadmin summary dijalankan.
- `SuperAdminAuthMiddleware`
  - jaga route `/superadmin/**`.
- `TenantAuthMiddleware`
  - jaga route `/tenant/**`.
- `EnsureTenantIsActive`
  - blok tenant non-active.
- `LoginRateLimitFilter`
  - rate limit register/login endpoint publik.

## Folder Structure

```text
src/main/java/com/netmaster/nmx/
  config/
    MasterPersistenceConfig.java
    TenantPersistenceConfig.java
    TenantContextHolder.java
    TenantRoutingDataSource.java
    MasterDataInitializer.java
  controller/
    TenantRegistrationController.java
    SuperAdminAuthController.java
    SuperAdminTenantController.java
    TenantAuthController.java
    TenantDashboardController.java
  dto/
    superadmin/*
    tenant/*
  master/
    model/*
    repository/*
  security/
    SessionAttributeKeys.java
    TenantResolver.java
    TenantContextMiddleware.java
    SuperAdminAuthMiddleware.java
    TenantAuthMiddleware.java
    EnsureTenantIsActive.java
    RolePermissionMiddleware.java
    LoginRateLimitFilter.java
  service/
    TenantRegistrationService.java
    TenantApprovalService.java
    TenantProvisioningService.java
    TenantConnectionManager.java
    TenantAuthenticationService.java
    MasterSuperAdminAuthService.java
    SuperAdminTenantAccessService.java
    MasterAuditLogService.java
src/main/resources/sql/saas/
  master-schema.sql
  tenant-baseline.sql
  tenant-seed.sql
```

## Routes

- `POST /register-tenant`
- `POST /superadmin/login`
- `GET /superadmin/tenants`
- `GET /superadmin/tenants/pending`
- `POST /superadmin/tenants/{id}/approve`
- `POST /superadmin/tenants/{id}/reject`
- `GET /superadmin/tenants/{id}`
- `PUT /superadmin/tenants/{id}`
- `DELETE /superadmin/tenants/{id}`
- `POST /tenant/login`
- `GET /tenant/dashboard`
- `POST /superadmin/tenants/{id}/impersonate`
- `GET /superadmin/tenants/{id}/summary`

## Pseudocode

### Approval Tenant

```text
load registration by id
assert registration.status == PENDING
load requested subscription plan
create tenant record with ACTIVE status
update registration.reviewed_by / reviewed_at / status
create database nmx_tenant_<slug>
save tenant_databases metadata
run tenant schema script
seed tenant roles/settings
create tenant admin user
insert tenant_users_index
write approval_history
write audit_logs
```

### Switch Tenant Connection

```text
resolve tenant from session/header/subdomain
find connection metadata in master DB
if datasource not cached:
  build datasource from tenant metadata
  register datasource in TenantRoutingDataSource
set TenantContextHolder = tenant-<id>
execute repository/service call
clear TenantContextHolder in finally block
```

### Audit Log

```text
MasterAuditLogService.record(
  actorType = SUPERADMIN,
  actorId = 1,
  action = TENANT_APPROVED,
  tenantId = 22,
  targetType = TENANT,
  targetId = "22",
  metadata = {"plan":"TRIAL","tenantSlug":"isp-a"},
  requestIp = "10.10.10.5"
)
```

## Security Checklist

- master DB dan tenant DB dipisah.
- semua akses tenant melalui `TenantContextHolder`.
- login/register endpoint pakai rate limit.
- password tenant dan superadmin tetap bcrypt.
- kredensial koneksi tenant disimpan terenkripsi via `EncryptedStringConverter`.
- approval/reject/impersonation dicatat ke `audit_logs`.
- support context default read-only secara session flag.
- tenant bisa di-suspend tanpa hapus database.
- soft delete tenant tersedia via `@SQLDelete`.

## Testing Checklist

- tenant A tidak bisa resolve ke tenant B saat `tenantSlug` berbeda.
- tenant `PENDING` gagal login.
- tenant `ACTIVE` bisa login.
- approve tenant membuat record `tenants` dan `tenant_databases`.
- approve tenant menjalankan tenant baseline schema.
- superadmin bisa melihat daftar tenant pending.
- superadmin hanya bisa baca summary tenant setelah memilih tenant.
- impersonation superadmin tercatat ke audit log.
- reject tenant menyimpan alasan penolakan.
- delete tenant hanya soft delete di master DB.

## Implementation Roadmap

1. Refactor auth dan tambahkan middleware SaaS baru.
2. Siapkan master database schema.
3. Tambahkan provisioning service tenant DB.
4. Aktifkan approval flow superadmin.
5. Pindahkan login tenant ke dynamic datasource.
6. Bangun panel superadmin dashboard.
7. Aktifkan audit log semua aksi lintas tenant.
8. Migrasikan service existing satu per satu ke tenant-only assumptions.
9. Tambahkan observability, backup per tenant, dan deployment hardening.

## Production Notes

- jalankan `master-schema.sql` dulu pada master DB.
- pastikan credential `NMX_MASTER_DB_*` dan `NMX_TENANT_DB_*` dipisah.
- idealnya user DB tenant berbeda per tenant, bukan shared postgres admin.
- gunakan connection pool limit per tenant bila tenant sudah banyak.
- backup dilakukan per database tenant dan backup master DB terpisah.
- pasang reverse proxy agar subdomain dapat dipetakan ke tenant slug.
- tambahkan secret manager untuk password DB tenant di production.
- jika butuh impersonation tulis, buat workflow approval kedua dan reason mandatory.
