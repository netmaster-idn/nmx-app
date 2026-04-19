import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PaymentAudit {

    private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/nmx_db";
    private static final String DEFAULT_DB_USERNAME = "postgres";
    private static final String DEFAULT_DB_PASSWORD = "Domboot007@";

    public static void main(String[] args) throws Exception {
        String dbUrl = env("NMX_DB_URL", DEFAULT_DB_URL);
        String dbUsername = env("NMX_DB_USERNAME", DEFAULT_DB_USERNAME);
        String dbPassword = env("NMX_DB_PASSWORD", DEFAULT_DB_PASSWORD);
        String outputPath = args.length > 0
                ? args[0]
                : "docs/audits/payment-audit-" + LocalDate.now() + ".md";

        Class.forName("org.postgresql.Driver");

        try (Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
            ReportBuilder report = new ReportBuilder(connection, dbUrl);
            report.write(Path.of(outputPath));
            System.out.println("Audit report written to: " + Path.of(outputPath).toAbsolutePath());
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record QueryResult(String title, String description, List<String> columns, List<List<String>> rows) {
        int count() {
            return rows.size();
        }
    }

    private static final class ReportBuilder {
        private final Connection connection;
        private final String dbUrl;
        private final LocalDate today;

        private ReportBuilder(Connection connection, String dbUrl) {
            this.connection = connection;
            this.dbUrl = dbUrl;
            this.today = LocalDate.now();
        }

        void write(Path output) throws Exception {
            QueryResult tableCounts = query(
                    "Table Counts",
                    "Jumlah baris tabel utama yang berhubungan dengan pembayaran.",
                    """
                    select 'customers' as table_name, count(*)::bigint as rows from customers
                    union all
                    select 'customer_services', count(*)::bigint from customer_services
                    union all
                    select 'invoices', count(*)::bigint from invoices
                    order by table_name
                    """
            );

            QueryResult invoiceStatus = query(
                    "Invoice Status Distribution",
                    "Distribusi status invoice yang tersimpan di database.",
                    """
                    select lower(coalesce(status,'<null>')) as status, count(*) as total
                    from invoices
                    group by 1
                    order by 2 desc, 1
                    """
            );

            QueryResult customerStatus = query(
                    "Customer Status Distribution",
                    "Distribusi status pelanggan.",
                    """
                    select lower(coalesce(status,'<null>')) as status, count(*) as total
                    from customers
                    group by 1
                    order by 2 desc, 1
                    """
            );

            QueryResult serviceStatus = query(
                    "Service Status Distribution",
                    "Distribusi status layanan pelanggan.",
                    """
                    select lower(coalesce(status,'<null>')) as status, count(*) as total
                    from customer_services
                    group by 1
                    order by 2 desc, 1
                    """
            );

            QueryResult paidButNotFull = query(
                    "Anomaly: Paid But Not Fully Paid",
                    "Invoice berstatus paid tetapi amount_paid masih kurang dari total_amount.",
                    """
                    select id, invoice_number, customer_id, total_amount, amount_paid, status, payment_date
                    from invoices
                    where lower(coalesce(status,''))='paid'
                      and coalesce(amount_paid,0) < coalesce(total_amount,0)
                    order by id
                    """
            );

            QueryResult paidWithoutPaymentDate = query(
                    "Anomaly: Paid Without Payment Date",
                    "Invoice berstatus paid tetapi payment_date kosong.",
                    """
                    select id, invoice_number, customer_id, total_amount, amount_paid, status, payment_date
                    from invoices
                    where lower(coalesce(status,''))='paid'
                      and payment_date is null
                    order by id
                    """
            );

            QueryResult overdueButFutureDue = query(
                    "Anomaly: Overdue With Future Due Date",
                    "Invoice berstatus overdue tetapi due_date masih di masa depan.",
                    """
                    select id, invoice_number, customer_id, due_date, total_amount, amount_paid, status
                    from invoices
                    where lower(coalesce(status,''))='overdue'
                      and due_date > current_date
                    order by due_date, id
                    """
            );

            QueryResult pendingThatShouldBeOverdue = query(
                    "Anomaly: Pending Or Partial That Should Be Overdue",
                    "Invoice pending/partial yang due_date-nya sudah lewat atau hari ini dan belum lunas.",
                    """
                    select id, invoice_number, customer_id, due_date, total_amount, amount_paid, status
                    from invoices
                    where lower(coalesce(status,'')) in ('pending','partial')
                      and due_date <= current_date
                      and coalesce(amount_paid,0) < coalesce(total_amount,0)
                    order by due_date, id
                    """
            );

            QueryResult cancelledWithPayment = query(
                    "Anomaly: Cancelled With Payment Footprint",
                    "Invoice cancelled tetapi masih menyimpan payment_date atau amount_paid.",
                    """
                    select id, invoice_number, customer_id, total_amount, amount_paid, status, payment_date
                    from invoices
                    where lower(coalesce(status,''))='cancelled'
                      and (coalesce(amount_paid,0) > 0 or payment_date is not null)
                    order by id
                    """
            );

            QueryResult duplicateBillingMonth = query(
                    "Anomaly: Duplicate Customer Billing Month",
                    "Duplikasi invoice pada customer dan billing_month yang sama.",
                    """
                    select customer_id, billing_month, count(*) as duplicate_count,
                           string_agg(id::text || ':' || invoice_number, ', ' order by id) as invoices
                    from invoices
                    where billing_month is not null
                    group by customer_id, billing_month
                    having count(*) > 1
                    order by duplicate_count desc, customer_id, billing_month
                    """
            );

            QueryResult customerServiceMismatch = query(
                    "Anomaly: Invoice Customer Service Mismatch",
                    "Invoice menunjuk customer_service milik customer lain.",
                    """
                    select i.id, i.invoice_number, i.customer_id, i.customer_service_id, cs.customer_id as service_customer_id
                    from invoices i
                    join customer_services cs on cs.id = i.customer_service_id
                    where cs.customer_id <> i.customer_id
                    order by i.id
                    """
            );

            QueryResult nullCoreFields = query(
                    "Anomaly: Null Core Fields",
                    "Field inti invoice yang kosong padahal seharusnya terisi.",
                    """
                    select id, invoice_number, customer_id, billing_month, due_date, total_amount, amount_paid, status
                    from invoices
                    where invoice_number is null
                       or customer_id is null
                       or total_amount is null
                       or status is null
                    order by id
                    """
            );

            QueryResult futurePaymentDate = query(
                    "Anomaly: Payment Date In Future",
                    "Invoice dengan payment_date lebih besar dari current_date database.",
                    """
                    select i.id, i.invoice_number, c.customer_code, c.full_name, i.status, i.payment_date, i.due_date, i.billing_month, i.payment_method
                    from invoices i
                    join customers c on c.id = i.customer_id
                    where i.payment_date > current_date
                    order by i.payment_date, i.id
                    """
            );

            QueryResult paidWithFutureSchedule = query(
                    "Anomaly: Paid Invoice With Future Billing Or Due Date",
                    "Invoice paid yang due_date atau billing timeline-nya berada setelah tanggal hari ini.",
                    """
                    select i.id, i.invoice_number, c.customer_code, c.full_name, i.status, i.billing_month, i.due_date, i.payment_date
                    from invoices i
                    join customers c on c.id = i.customer_id
                    where lower(coalesce(i.status,''))='paid'
                      and (i.billing_month > current_date or i.due_date > current_date)
                    order by i.due_date, i.id
                    """
            );

            QueryResult futureServiceDates = query(
                    "Anomaly: Customer Service Future Dates",
                    "Layanan dengan installation_date atau activation_date di masa depan.",
                    """
                    select cs.id, c.customer_code, c.full_name, cs.installation_date, cs.activation_date, cs.status
                    from customer_services cs
                    join customers c on c.id = cs.customer_id
                    where cs.installation_date > current_date or cs.activation_date > current_date
                    order by greatest(coalesce(cs.installation_date, date '1900-01-01'), coalesce(cs.activation_date, date '1900-01-01'))
                    """
            );

            QueryResult overdueList = query(
                    "Overdue Invoice List",
                    "Daftar invoice overdue saat audit dijalankan.",
                    """
                    select i.id, i.invoice_number, c.customer_code, c.full_name, i.billing_month, i.due_date, i.total_amount, i.amount_paid, i.status
                    from invoices i
                    join customers c on c.id = i.customer_id
                    where lower(coalesce(i.status,''))='overdue'
                    order by i.due_date, i.id
                    """
            );

            QueryResult topOutstanding = query(
                    "Top Outstanding Customers",
                    "Pelanggan dengan outstanding terbesar berdasarkan invoice saat ini.",
                    """
                    select c.id, c.customer_code, c.full_name,
                           count(i.*) as invoice_count,
                           sum(case when lower(coalesce(i.status,''))='paid' then 1 else 0 end) as paid_count,
                           sum(case when lower(coalesce(i.status,'')) in ('pending','partial') then 1 else 0 end) as pending_like_count,
                           sum(case when lower(coalesce(i.status,''))='overdue' then 1 else 0 end) as overdue_count,
                           sum(greatest(coalesce(i.total_amount,0) - coalesce(i.amount_paid,0), 0)) as outstanding_total
                    from customers c
                    left join invoices i on i.customer_id = c.id
                    group by c.id, c.customer_code, c.full_name
                    having count(i.*) > 0
                    order by outstanding_total desc, overdue_count desc, c.id
                    limit 25
                    """
            );

            List<QueryResult> anomalyChecks = List.of(
                    paidButNotFull,
                    paidWithoutPaymentDate,
                    overdueButFutureDue,
                    pendingThatShouldBeOverdue,
                    cancelledWithPayment,
                    duplicateBillingMonth,
                    customerServiceMismatch,
                    nullCoreFields,
                    futurePaymentDate,
                    paidWithFutureSchedule,
                    futureServiceDates
            );

            StringBuilder markdown = new StringBuilder();
            markdown.append("# Payment Audit Report\n\n");
            markdown.append("- Generated at: ")
                    .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .append('\n');
            markdown.append("- Database URL: `").append(dbUrl).append("`\n");
            markdown.append("- Audit date: `").append(today).append("`\n\n");

            markdown.append("## Executive Summary\n\n");
            markdown.append("- Invoice rows audited: `").append(findCount(tableCounts, "invoices")).append("`\n");
            markdown.append("- Customers audited: `").append(findCount(tableCounts, "customers")).append("`\n");
            markdown.append("- Customer services audited: `").append(findCount(tableCounts, "customer_services")).append("`\n");
            markdown.append("- Overdue invoices found: `").append(overdueList.count()).append("`\n");
            markdown.append("- Strict database anomalies found: `").append(totalRows(anomalyChecks)).append("`\n");
            markdown.append("- Notable finding: `").append(futurePaymentDate.count()).append("` invoice has `payment_date` in the future relative to audit date.\n\n");

            markdown.append("## High Priority Findings\n\n");
            if (futurePaymentDate.count() == 0 && paidWithFutureSchedule.count() == 0) {
                markdown.append("- No high-priority anomaly detected from payment date or future paid scheduling checks.\n\n");
            } else {
                if (futurePaymentDate.count() > 0) {
                    markdown.append("- Future payment date anomaly detected. This indicates data can be written as already paid even though payment happens after the audit date.\n");
                }
                if (paidWithFutureSchedule.count() > 0) {
                    markdown.append("- Paid invoice with future due date exists. This is consistent with activation/backfill logic writing payment state from service dates.\n");
                }
                markdown.append('\n');
            }

            markdown.append(renderSection(tableCounts));
            markdown.append(renderSection(invoiceStatus));
            markdown.append(renderSection(customerStatus));
            markdown.append(renderSection(serviceStatus));

            markdown.append("## Anomaly Checks\n\n");
            for (QueryResult result : anomalyChecks) {
                markdown.append(renderSection(result));
            }

            markdown.append(renderSection(overdueList));
            markdown.append(renderSection(topOutstanding));

            markdown.append("## Logic Review Notes\n\n");
            markdown.append("- Read methods in `CustomerServiceImpl` are not pure reads. `getCustomerDataRows`, `getHistoryPaymentRowsByStatus`, and `getAllInvoiceRows` call backfill/synchronization logic before returning data.\n");
            markdown.append("- Backfill path lives in `backfillPaymentHistoryForEligibleCustomers` and `backfillPaymentHistoryForCustomerIfNeeded`. Opening a page can therefore create missing invoices.\n");
            markdown.append("- Status synchronization path lives in `synchronizeInvoiceStatuses` and `synchronizeInvoiceStatus`. Opening a page can also rewrite `status` values in `invoices`.\n");
            markdown.append("- Activation/backfill writer in `createOrUpdateActivationInvoice` can mark invoice as `paid` using service timeline fields. If service installation date is set in the future, payment date can also be written in the future.\n");
            markdown.append("- This means a mismatch may come from logic side even if raw invoice rows look structurally valid.\n\n");

            markdown.append("## Recommendation\n\n");
            markdown.append("1. Pisahkan proses backfill/migration dari endpoint read agar halaman tidak mengubah database.\n");
            markdown.append("2. Tambahkan guard pada activation/backfill supaya `payment_date` tidak boleh lebih besar dari `current_date` saat invoice ditandai paid.\n");
            markdown.append("3. Audit data service dengan tanggal masa depan, terutama `installation_date`, karena nilai ini ikut dipakai saat membuat activation invoice.\n");
            markdown.append("4. Tambahkan test untuk kasus `future installation_date`, `due_date == today`, dan status aggregation customer/history.\n");

            Files.createDirectories(output.getParent());
            Files.writeString(output, markdown.toString(), StandardCharsets.UTF_8);
        }

        private String renderSection(QueryResult result) {
            StringBuilder sb = new StringBuilder();
            sb.append("## ").append(result.title).append("\n\n");
            sb.append(result.description).append("\n\n");
            sb.append("- Row count: `").append(result.count()).append("`\n\n");

            if (result.rows.isEmpty()) {
                sb.append("_No rows found._\n\n");
                return sb.toString();
            }

            sb.append("| ");
            for (String column : result.columns) {
                sb.append(column).append(" | ");
            }
            sb.append("\n| ");
            for (int i = 0; i < result.columns.size(); i++) {
                sb.append("--- | ");
            }
            sb.append('\n');

            for (List<String> row : result.rows) {
                sb.append("| ");
                for (String cell : row) {
                    sb.append(escapeMarkdown(cell)).append(" | ");
                }
                sb.append('\n');
            }
            sb.append('\n');
            return sb.toString();
        }

        private QueryResult query(String title, String description, String sql) throws Exception {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(sql)) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                int columnCount = metadata.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metadata.getColumnLabel(i));
                }

                List<List<String>> rows = new ArrayList<>();
                while (resultSet.next()) {
                    List<String> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        Object value = resultSet.getObject(i);
                        row.add(value == null ? "" : formatValue(value));
                    }
                    rows.add(row);
                }

                return new QueryResult(title, description, columns, rows);
            }
        }

        private String formatValue(Object value) {
            if (value instanceof BigDecimal decimal) {
                return decimal.stripTrailingZeros().toPlainString();
            }
            return String.valueOf(value);
        }

        private String findCount(QueryResult result, String tableName) {
            return result.rows.stream()
                    .filter(row -> row.size() >= 2 && tableName.equals(row.get(0)))
                    .map(row -> row.get(1))
                    .findFirst()
                    .orElse("0");
        }

        private int totalRows(List<QueryResult> results) {
            return results.stream().mapToInt(QueryResult::count).sum();
        }

        private String escapeMarkdown(String value) {
            return value
                    .replace("|", "\\|")
                    .replace("\n", "<br>");
        }
    }
}
