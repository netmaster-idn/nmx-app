# Billing Architecture Refactor

## 1. Root Cause Analysis

### Current failure

The customer payment history module and the finance invoice module behaved the same because they were reading from the same invoice-backed flow in all three layers:

| Layer | Before | Why it was wrong |
| --- | --- | --- |
| Database | `invoices` stored billing fields and payment fields together (`amount_paid`, `payment_date`, `payment_method`, `payment_notes`) | One table was acting as both invoice ledger and payment ledger |
| API | Customer routes under `PelangganController` exposed invoice creation, invoice payment, invoice filtering, and payment history endpoints | Customer module owned finance lifecycle |
| UI | `history-payment.html` and `finance/invoice.html` both called invoice endpoints and exposed overlapping actions | Both pages were effectively different skins over the same invoice data |

### Concrete root cause

1. `Invoice` was the only financial entity.
2. Payment history views queried invoice rows instead of payment transactions.
3. Customer page actions included finance responsibilities like invoice generation.
4. Finance invoice page actions included customer responsibilities like viewing customer payment history and customer account operations.

### Refactor decision

The system was in **CASE A: merged table design**.  
The fix was to introduce a dedicated `payments` domain and move the customer history flow onto payment records while keeping the finance page on invoice records.

## 2. Before vs After

| Concern | Before | After |
| --- | --- | --- |
| Payment storage | Inline on `invoices` | Dedicated `payments` table |
| Customer history source | Invoice rows | Payment rows |
| Finance invoice source | Invoice rows mixed with payment fields | Invoice rows with payment-backed settlement snapshot |
| Customer actions | View history, create invoice, pay invoice, print, suspend/delete customer | View payment history, view invoice, record payment |
| Finance actions | View customer history, create invoice, pay invoice, print history, suspend/delete customer | Create invoice, review invoice, mark paid |
| API ownership | `/pelanggan/api/invoices*` and `/api/pembayaran` | `/api/customers/{id}/payments*` and `/api/invoices*` |

## 3. New Database Schema

### Production target DDL

```sql
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address TEXT,
    service_plan VARCHAR(100),
    status VARCHAR(30) NOT NULL
);

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    billing_period DATE NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    tax DECIMAL(12,2) NOT NULL DEFAULT 0,
    due_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    amount_paid DECIMAL(12,2) NOT NULL,
    payment_date DATE NOT NULL,
    payment_method VARCHAR(50),
    reference_number VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Implemented in this refactor

```sql
CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    amount_paid DECIMAL(12,2) NOT NULL,
    payment_date DATE NOT NULL,
    payment_method VARCHAR(50),
    reference_number VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payments_invoice ON payments(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payments_customer ON payments(customer_id);
CREATE INDEX IF NOT EXISTS idx_payments_date ON payments(payment_date);
```

Note: legacy payment snapshot columns still exist on `invoices` for backward compatibility with untouched modules, but the new customer and finance flows now use `payments` as the transaction source of truth.

## 4. ERD

```text
customers (1) ----< invoices (many)
customers (1) ----< payments (many)
invoices  (1) ----< payments (many)
```

## 5. Refactored API Endpoints

### Customer module

- `GET /api/customers/{id}/payments`
- `GET /api/customers/{id}/payments/years`
- `GET /api/customers/{id}/payments/months?year=YYYY`
- `GET /api/customers/{id}/invoices`

### Finance module

- `GET /api/invoices`
- `GET /api/invoices/years`
- `GET /api/invoices/months?year=YYYY`
- `GET /api/invoices/{id}`
- `POST /api/invoices`
- `PATCH /api/invoices/{id}`
- `DELETE /api/invoices/{id}`
- `POST /api/invoices/{id}/cancel`
- `GET /api/invoices/{id}/payments`
- `POST /api/invoices/{id}/payments`

## 6. Refactored Service Logic

### Invoice management

```java
createInvoice(dto):
  customer = load customer
  service = resolve customer service
  validate one invoice per customer + billing period
  build invoice total
  save invoice
```

```java
getInvoices():
  load invoices
  load payments grouped by invoice
  recompute invoice.amountPaid/status from payments
  return invoice rows
```

### Payment management

```java
recordPayment(invoiceId, request):
  invoice = load invoice
  invoiceView = get invoice snapshot
  reject if cancelled or already paid
  reject if amount > outstanding
  insert payment row
  refresh invoice aggregate snapshot
  return updated invoice view
```

```java
getCustomerPayments(customerId):
  load payment rows by customer
  join invoice metadata
  return payment history ordered by payment_date desc
```

## 7. Files Changed

- `src/main/java/com/netmaster/nmx/model/Payment.java`
- `src/main/java/com/netmaster/nmx/repository/PaymentRepository.java`
- `src/main/java/com/netmaster/nmx/service/BillingInvoiceService.java`
- `src/main/java/com/netmaster/nmx/service/PaymentManagementService.java`
- `src/main/java/com/netmaster/nmx/controller/BillingInvoiceController.java`
- `src/main/java/com/netmaster/nmx/controller/CustomerPaymentController.java`
- `src/main/java/com/netmaster/nmx/config/BillingSchemaInitializer.java`
- `src/main/resources/templates/pelanggan/history-payment.html`
- `src/main/resources/templates/finance/invoice.html`
- `database/nmx_unified.sql`

