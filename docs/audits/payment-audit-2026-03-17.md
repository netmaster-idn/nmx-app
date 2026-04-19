# Payment Audit Report

- Generated at: 2026-03-17 23:45:33
- Database URL: `jdbc:postgresql://localhost:5432/nmx_db`
- Audit date: `2026-03-17`

## Executive Summary

- Invoice rows audited: `134`
- Customers audited: `67`
- Customer services audited: `67`
- Overdue invoices found: `6`
- Strict database anomalies found: `3`
- Notable finding: `1` invoice has `payment_date` in the future relative to audit date.

## High Priority Findings

- Future payment date anomaly detected. This indicates data can be written as already paid even though payment happens after the audit date.
- Paid invoice with future due date exists. This is consistent with activation/backfill logic writing payment state from service dates.

## Table Counts

Jumlah baris tabel utama yang berhubungan dengan pembayaran.

- Row count: `3`

| table_name | rows | 
| --- | --- | 
| customer_services | 67 | 
| customers | 67 | 
| invoices | 134 | 

## Invoice Status Distribution

Distribusi status invoice yang tersimpan di database.

- Row count: `3`

| status | total | 
| --- | --- | 
| paid | 67 | 
| pending | 61 | 
| overdue | 6 | 

## Customer Status Distribution

Distribusi status pelanggan.

- Row count: `1`

| status | total | 
| --- | --- | 
| active | 67 | 

## Service Status Distribution

Distribusi status layanan pelanggan.

- Row count: `1`

| status | total | 
| --- | --- | 
| active | 67 | 

## Anomaly Checks

## Anomaly: Paid But Not Fully Paid

Invoice berstatus paid tetapi amount_paid masih kurang dari total_amount.

- Row count: `0`

_No rows found._

## Anomaly: Paid Without Payment Date

Invoice berstatus paid tetapi payment_date kosong.

- Row count: `0`

_No rows found._

## Anomaly: Overdue With Future Due Date

Invoice berstatus overdue tetapi due_date masih di masa depan.

- Row count: `0`

_No rows found._

## Anomaly: Pending Or Partial That Should Be Overdue

Invoice pending/partial yang due_date-nya sudah lewat atau hari ini dan belum lunas.

- Row count: `0`

_No rows found._

## Anomaly: Cancelled With Payment Footprint

Invoice cancelled tetapi masih menyimpan payment_date atau amount_paid.

- Row count: `0`

_No rows found._

## Anomaly: Duplicate Customer Billing Month

Duplikasi invoice pada customer dan billing_month yang sama.

- Row count: `0`

_No rows found._

## Anomaly: Invoice Customer Service Mismatch

Invoice menunjuk customer_service milik customer lain.

- Row count: `0`

_No rows found._

## Anomaly: Null Core Fields

Field inti invoice yang kosong padahal seharusnya terisi.

- Row count: `0`

_No rows found._

## Anomaly: Payment Date In Future

Invoice dengan payment_date lebih besar dari current_date database.

- Row count: `1`

| id | invoice_number | customer_code | full_name | status | payment_date | due_date | billing_month | payment_method | 
| --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 131 | INV-2026030119 | CSM-20260316-062 | NASIR | paid | 2026-03-22 | 2026-03-22 | 2026-03-01 | Aktivasi Pelanggan | 

## Anomaly: Paid Invoice With Future Billing Or Due Date

Invoice paid yang due_date atau billing timeline-nya berada setelah tanggal hari ini.

- Row count: `1`

| id | invoice_number | customer_code | full_name | status | billing_month | due_date | payment_date | 
| --- | --- | --- | --- | --- | --- | --- | --- | 
| 131 | INV-2026030119 | CSM-20260316-062 | NASIR | paid | 2026-03-01 | 2026-03-22 | 2026-03-22 | 

## Anomaly: Customer Service Future Dates

Layanan dengan installation_date atau activation_date di masa depan.

- Row count: `1`

| id | customer_code | full_name | installation_date | activation_date | status | 
| --- | --- | --- | --- | --- | --- | 
| 99 | CSM-20260316-062 | NASIR | 2026-03-22 | 2026-03-17 | active | 

## Overdue Invoice List

Daftar invoice overdue saat audit dijalankan.

- Row count: `6`

| id | invoice_number | customer_code | full_name | billing_month | due_date | total_amount | amount_paid | status | 
| --- | --- | --- | --- | --- | --- | --- | --- | --- | 
| 56 | INV-2026030044 | CSM-20260316-004 | WITTO | 2026-03-04 | 2026-03-04 | 200000 | 0 | overdue | 
| 58 | INV-2026030046 | CSM-20260316-009 | RIKI | 2026-03-07 | 2026-03-07 | 200000 | 0 | overdue | 
| 60 | INV-2026030048 | CSM-20260316-015 | HABIB | 2026-03-12 | 2026-03-12 | 200000 | 0 | overdue | 
| 62 | INV-2026030050 | CSM-20260316-020 | DINDA | 2026-03-13 | 2026-03-13 | 200000 | 0 | overdue | 
| 64 | INV-2026030052 | CSM-20260316-027 | ANGGUT | 2026-03-16 | 2026-03-16 | 200000 | 0 | overdue | 
| 66 | INV-2026030054 | CSM-20260316-028 | USU | 2026-03-17 | 2026-03-17 | 200000 | 0 | overdue | 

## Top Outstanding Customers

Pelanggan dengan outstanding terbesar berdasarkan invoice saat ini.

- Row count: `25`

| id | customer_code | full_name | invoice_count | paid_count | pending_like_count | overdue_count | outstanding_total | 
| --- | --- | --- | --- | --- | --- | --- | --- | 
| 69 | CSM-20260316-026 | ISMAIL | 2 | 1 | 1 | 0 | 300000 | 
| 68 | CSM-20260316-025 | SUTIYONO | 2 | 1 | 1 | 0 | 250000 | 
| 84 | CSM-20260316-041 | KADES | 2 | 1 | 1 | 0 | 250000 | 
| 47 | CSM-20260316-004 | WITTO | 2 | 1 | 0 | 1 | 200000 | 
| 52 | CSM-20260316-009 | RIKI | 2 | 1 | 0 | 1 | 200000 | 
| 58 | CSM-20260316-015 | HABIB | 2 | 1 | 0 | 1 | 200000 | 
| 63 | CSM-20260316-020 | DINDA | 2 | 1 | 0 | 1 | 200000 | 
| 70 | CSM-20260316-027 | ANGGUT | 2 | 1 | 0 | 1 | 200000 | 
| 71 | CSM-20260316-028 | USU | 2 | 1 | 0 | 1 | 200000 | 
| 44 | CSM-20260316-001 | SEGAR | 2 | 1 | 1 | 0 | 200000 | 
| 45 | CSM-20260316-002 | GITA | 2 | 1 | 1 | 0 | 200000 | 
| 46 | CSM-20260316-003 | DIAN | 2 | 1 | 1 | 0 | 200000 | 
| 48 | CSM-20260316-005 | KANG PRI | 2 | 1 | 1 | 0 | 200000 | 
| 49 | CSM-20260316-006 | Toko Takim | 2 | 1 | 1 | 0 | 200000 | 
| 50 | CSM-20260316-007 | SONY | 2 | 1 | 1 | 0 | 200000 | 
| 51 | CSM-20260316-008 | BEJAN | 2 | 1 | 1 | 0 | 200000 | 
| 53 | CSM-20260316-010 | JEMUAT | 2 | 1 | 1 | 0 | 200000 | 
| 54 | CSM-20260316-011 | YUSUP | 2 | 1 | 1 | 0 | 200000 | 
| 55 | CSM-20260316-012 | ANO | 2 | 1 | 1 | 0 | 200000 | 
| 56 | CSM-20260316-013 | BUDI TOMO | 2 | 1 | 1 | 0 | 200000 | 
| 57 | CSM-20260316-014 | PARUJI | 2 | 1 | 1 | 0 | 200000 | 
| 59 | CSM-20260316-016 | EMANUEL | 2 | 1 | 1 | 0 | 200000 | 
| 60 | CSM-20260316-017 | KAYAT | 2 | 1 | 1 | 0 | 200000 | 
| 61 | CSM-20260316-018 | NOVEN | 2 | 1 | 1 | 0 | 200000 | 
| 62 | CSM-20260316-019 | TAKIM | 2 | 1 | 1 | 0 | 200000 | 

## Logic Review Notes

- Read methods in `CustomerServiceImpl` are not pure reads. `getCustomerDataRows`, `getHistoryPaymentRowsByStatus`, and `getAllInvoiceRows` call backfill/synchronization logic before returning data.
- Backfill path lives in `backfillPaymentHistoryForEligibleCustomers` and `backfillPaymentHistoryForCustomerIfNeeded`. Opening a page can therefore create missing invoices.
- Status synchronization path lives in `synchronizeInvoiceStatuses` and `synchronizeInvoiceStatus`. Opening a page can also rewrite `status` values in `invoices`.
- Activation/backfill writer in `createOrUpdateActivationInvoice` can mark invoice as `paid` using service timeline fields. If service installation date is set in the future, payment date can also be written in the future.
- This means a mismatch may come from logic side even if raw invoice rows look structurally valid.

## Recommendation

1. Pisahkan proses backfill/migration dari endpoint read agar halaman tidak mengubah database.
2. Tambahkan guard pada activation/backfill supaya `payment_date` tidak boleh lebih besar dari `current_date` saat invoice ditandai paid.
3. Audit data service dengan tanggal masa depan, terutama `installation_date`, karena nilai ini ikut dipakai saat membuat activation invoice.
4. Tambahkan test untuk kasus `future installation_date`, `due_date == today`, dan status aggregation customer/history.
