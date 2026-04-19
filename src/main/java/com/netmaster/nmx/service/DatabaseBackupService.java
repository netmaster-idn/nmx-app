package com.netmaster.nmx.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataAccessException;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    private static final String SCHEMA_NAME = "public";
    private static final int BACKUP_VERSION = 1;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BackupFile exportBackup() {
        List<String> tables = getAllBaseTables();
        Map<String, List<ColumnMeta>> metadata = getColumnMetadata(tables);

        BackupPayload payload = new BackupPayload();
        payload.setVersion(BACKUP_VERSION);
        payload.setSchema(SCHEMA_NAME);
        payload.setCreatedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        payload.setTables(new ArrayList<>());

        long totalRows = 0L;
        for (String table : tables) {
            TableBackup tableBackup = new TableBackup();
            tableBackup.setName(table);
            tableBackup.setColumns(metadata.getOrDefault(table, List.of()).stream()
                    .map(ColumnMeta::toExportColumn)
                    .toList());

            JsonNode rowsNode = readTableRows(table, getPrimaryKeyColumns(table));
            tableBackup.setRows(rowsNode);
            tableBackup.setSequences(readTableSequences(table, metadata.getOrDefault(table, List.of())));
            payload.getTables().add(tableBackup);
            totalRows += rowsNode.size();
        }

        try {
            byte[] content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
            String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT));
            return new BackupFile(
                    "nmx-database-backup-" + timestamp + ".json",
                    content,
                    payload.getTables().size(),
                    totalRows
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal membuat file backup database", ex);
        }
    }

    @Transactional
    public BackupImportResult importBackup(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File backup wajib dipilih");
        }

        BackupPayload payload;
        try {
            payload = objectMapper.readValue(file.getInputStream(), BackupPayload.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("File backup tidak valid atau tidak dapat dibaca", ex);
        }

        validatePayload(payload);

        List<String> currentTables = getAllBaseTables();
        Map<String, TableBackup> backupTables = payload.getTables().stream()
                .collect(Collectors.toMap(TableBackup::getName, table -> table, (left, right) -> right, LinkedHashMap::new));

        if (!backupTables.keySet().equals(new LinkedHashSet<>(currentTables))) {
            Set<String> missing = new TreeSet<>(currentTables);
            missing.removeAll(backupTables.keySet());

            Set<String> extra = new TreeSet<>(backupTables.keySet());
            extra.removeAll(currentTables);

            StringBuilder message = new StringBuilder("Struktur backup tidak cocok dengan database aktif.");
            if (!missing.isEmpty()) {
                message.append(" Tabel hilang: ").append(String.join(", ", missing)).append('.');
            }
            if (!extra.isEmpty()) {
                message.append(" Tabel tidak dikenal: ").append(String.join(", ", extra)).append('.');
            }
            throw new IllegalArgumentException(message.toString());
        }

        Map<String, List<ColumnMeta>> metadata = getColumnMetadata(currentTables);
        truncateAllTables(currentTables);

        List<String> insertOrder = sortTablesForInsert(currentTables);
        int importedTables = 0;
        long importedRows = 0L;

        Connection connection = null;
        try {
            connection = DataSourceUtils.getConnection(dataSource);
            for (String table : insertOrder) {
                TableBackup tableBackup = backupTables.get(table);
                importedRows += restoreTable(connection, table, tableBackup, metadata.getOrDefault(table, List.of()));
                importedTables++;
            }

            for (String table : currentTables) {
                restoreSequences(backupTables.get(table));
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Gagal mengimpor backup database", ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        return new BackupImportResult(importedTables, importedRows, payload.getCreatedAt());
    }

    private void validatePayload(BackupPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Isi file backup kosong");
        }
        if (payload.getVersion() != BACKUP_VERSION) {
            throw new IllegalArgumentException("Versi backup tidak didukung");
        }
        if (!SCHEMA_NAME.equalsIgnoreCase(payload.getSchema())) {
            throw new IllegalArgumentException("Schema backup tidak sesuai dengan database aplikasi");
        }
        if (payload.getTables() == null || payload.getTables().isEmpty()) {
            throw new IllegalArgumentException("File backup tidak berisi data tabel");
        }
    }

    private List<String> getAllBaseTables() {
        return jdbcTemplate.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = ?
                  and table_type = 'BASE TABLE'
                order by table_name
                """, String.class, SCHEMA_NAME);
    }

    private Map<String, List<ColumnMeta>> getColumnMetadata(Collection<String> tables) {
        Map<String, List<ColumnMeta>> result = new LinkedHashMap<>();
        for (String table : tables) {
            List<ColumnMeta> columns = jdbcTemplate.query("""
                    select column_name,
                           data_type,
                           udt_name,
                           ordinal_position,
                           is_identity,
                           identity_generation
                    from information_schema.columns
                    where table_schema = ?
                      and table_name = ?
                    order by ordinal_position
                    """, (rs, rowNum) -> new ColumnMeta(
                    rs.getString("column_name"),
                    rs.getString("data_type"),
                    rs.getString("udt_name"),
                    "YES".equalsIgnoreCase(rs.getString("is_identity")),
                    rs.getString("identity_generation")
            ), SCHEMA_NAME, table);
            result.put(table, columns);
        }
        return result;
    }

    private List<String> getPrimaryKeyColumns(String table) {
        return jdbcTemplate.queryForList("""
                select kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name
                 and tc.table_schema = kcu.table_schema
                where tc.table_schema = ?
                  and tc.table_name = ?
                  and tc.constraint_type = 'PRIMARY KEY'
                order by kcu.ordinal_position
                """, String.class, SCHEMA_NAME, table);
    }

    private JsonNode readTableRows(String table, List<String> primaryKeys) {
        String quotedTable = qualifiedTable(table);
        String orderBy = primaryKeys.isEmpty()
                ? ""
                : " order by " + primaryKeys.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String sql = "select coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb)::text from (select * from " + quotedTable + orderBy + ") t";
        String json = jdbcTemplate.queryForObject(sql, String.class);
        try {
            return objectMapper.readTree(Objects.requireNonNullElse(json, "[]"));
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal membaca data tabel " + table, ex);
        }
    }

    private List<SequenceState> readTableSequences(String table, List<ColumnMeta> columns) {
        List<SequenceState> sequences = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ColumnMeta column : columns) {
            String sequenceName;
            try {
                sequenceName = jdbcTemplate.queryForObject(
                        "select pg_get_serial_sequence(?, ?)",
                        String.class,
                        SCHEMA_NAME + "." + table,
                        column.getName()
                );
            } catch (DataAccessException ex) {
                return List.of();
            }
            if (sequenceName == null || !seen.add(sequenceName)) {
                continue;
            }

            try {
                Map<String, Object> state = jdbcTemplate.queryForMap(
                        "select last_value, is_called from " + quoteQualifiedName(sequenceName)
                );
                sequences.add(new SequenceState(
                        sequenceName,
                        ((Number) state.get("last_value")).longValue(),
                        Boolean.TRUE.equals(state.get("is_called"))
                ));
            } catch (DataAccessException ex) {
                return List.of();
            }
        }
        return sequences;
    }

    private void truncateAllTables(List<String> tables) {
        if (tables.isEmpty()) {
            return;
        }
        String truncateSql = "truncate table " + tables.stream()
                .map(this::qualifiedTable)
                .collect(Collectors.joining(", ")) + " restart identity cascade";
        jdbcTemplate.execute(truncateSql);
    }

    private List<String> sortTablesForInsert(List<String> tables) {
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (String table : tables) {
            dependencies.put(table, new LinkedHashSet<>());
        }

        jdbcTemplate.query("""
                select tc.table_name as child_table,
                       ccu.table_name as parent_table
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on tc.constraint_name = kcu.constraint_name
                 and tc.table_schema = kcu.table_schema
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name
                 and ccu.table_schema = tc.table_schema
                where tc.table_schema = ?
                  and tc.constraint_type = 'FOREIGN KEY'
                """, rs -> {
            String child = rs.getString("child_table");
            String parent = rs.getString("parent_table");
            if (dependencies.containsKey(child) && dependencies.containsKey(parent) && !child.equals(parent)) {
                dependencies.get(child).add(parent);
            }
        }, SCHEMA_NAME);

        ArrayDeque<String> ready = dependencies.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toCollection(ArrayDeque::new));

        List<String> ordered = new ArrayList<>();
        Map<String, Set<String>> remaining = new LinkedHashMap<>();
        dependencies.forEach((key, value) -> remaining.put(key, new LinkedHashSet<>(value)));

        while (!ready.isEmpty()) {
            String table = ready.removeFirst();
            if (!remaining.containsKey(table)) {
                continue;
            }
            ordered.add(table);
            remaining.remove(table);
            remaining.forEach((name, deps) -> {
                deps.remove(table);
                if (deps.isEmpty() && !ordered.contains(name) && !ready.contains(name)) {
                    ready.add(name);
                }
            });
        }

        if (!remaining.isEmpty()) {
            List<String> unresolved = new ArrayList<>(remaining.keySet());
            unresolved.sort(Comparator.naturalOrder());
            ordered.addAll(unresolved);
        }
        return ordered;
    }

    private long restoreTable(Connection connection, String table, TableBackup tableBackup, List<ColumnMeta> currentColumns) throws Exception {
        if (tableBackup == null || tableBackup.getRows() == null || !tableBackup.getRows().isArray() || tableBackup.getRows().isEmpty()) {
            return 0L;
        }

        List<String> columnNames = currentColumns.stream().map(ColumnMeta::getName).toList();
        String insertColumns = columnNames.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String placeholders = columnNames.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        boolean hasAlwaysIdentity = currentColumns.stream()
                .anyMatch(column -> column.isIdentity() && "ALWAYS".equalsIgnoreCase(column.getIdentityGeneration()));

        String sql = "insert into " + qualifiedTable(table) +
                " (" + insertColumns + ")" +
                (hasAlwaysIdentity ? " overriding system value" : "") +
                " values (" + placeholders + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            long rowCount = 0L;
            for (JsonNode row : tableBackup.getRows()) {
                for (int index = 0; index < currentColumns.size(); index++) {
                    ColumnMeta column = currentColumns.get(index);
                    bindValue(statement, index + 1, column, row.get(column.getName()), connection);
                }
                statement.addBatch();
                rowCount++;
            }
            statement.executeBatch();
            return rowCount;
        }
    }

    private void restoreSequences(TableBackup tableBackup) {
        if (tableBackup == null || tableBackup.getSequences() == null) {
            return;
        }
        for (SequenceState sequence : tableBackup.getSequences()) {
            try {
                jdbcTemplate.update(
                        "select setval(?, ?, ?)",
                        sequence.getName(),
                        sequence.getLastValue(),
                        sequence.isCalled()
                );
            } catch (DataAccessException ex) {
                return;
            }
        }
    }

    private void bindValue(PreparedStatement statement, int parameterIndex, ColumnMeta column, JsonNode value, Connection connection) throws Exception {
        if (value == null || value.isNull()) {
            statement.setNull(parameterIndex, resolveSqlType(column));
            return;
        }

        if ("json".equalsIgnoreCase(column.getDataType()) || "jsonb".equalsIgnoreCase(column.getDataType())) {
            if (isPostgreSql(connection)) {
                PGobject jsonObject = new PGobject();
                jsonObject.setType(column.getDataType().toLowerCase(Locale.ROOT));
                jsonObject.setValue(value.toString());
                statement.setObject(parameterIndex, jsonObject);
            } else {
                statement.setString(parameterIndex, value.toString());
            }
            return;
        }

        if ("ARRAY".equalsIgnoreCase(column.getDataType())) {
            Array sqlArray = connection.createArrayOf(resolveArrayType(column.getUdtName()), convertArrayValues(value));
            statement.setArray(parameterIndex, sqlArray);
            return;
        }

        if (value.isBoolean()) {
            statement.setBoolean(parameterIndex, value.asBoolean());
            return;
        }

        if (value.isIntegralNumber()) {
            statement.setLong(parameterIndex, value.longValue());
            return;
        }

        if (value.isFloatingPointNumber() || value.isBigDecimal()) {
            statement.setBigDecimal(parameterIndex, value.decimalValue());
            return;
        }

        if ("bytea".equalsIgnoreCase(column.getUdtName())) {
            statement.setBytes(parameterIndex, value.binaryValue());
            return;
        }

        if (value.isTextual()) {
            statement.setObject(parameterIndex, value.asText());
            return;
        }

        statement.setObject(parameterIndex, value.toString());
    }

    private Object[] convertArrayValues(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return new Object[0];
        }
        List<Object> values = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            if (node == null || node.isNull()) {
                values.add(null);
            } else if (node.isBoolean()) {
                values.add(node.asBoolean());
            } else if (node.isIntegralNumber()) {
                values.add(node.longValue());
            } else if (node.isFloatingPointNumber() || node.isBigDecimal()) {
                values.add(node.decimalValue());
            } else {
                values.add(node.asText());
            }
        }
        return values.toArray();
    }

    private int resolveSqlType(ColumnMeta column) {
        String dataType = column.getDataType() == null ? "" : column.getDataType().toLowerCase(Locale.ROOT);
        return switch (dataType) {
            case "boolean" -> Types.BOOLEAN;
            case "smallint" -> Types.SMALLINT;
            case "integer" -> Types.INTEGER;
            case "bigint" -> Types.BIGINT;
            case "real" -> Types.REAL;
            case "double precision" -> Types.DOUBLE;
            case "numeric", "decimal" -> Types.NUMERIC;
            case "date" -> Types.DATE;
            case "timestamp without time zone", "timestamp with time zone" -> Types.TIMESTAMP;
            case "time without time zone", "time with time zone" -> Types.TIME;
            case "json", "jsonb" -> Types.OTHER;
            case "array" -> Types.ARRAY;
            case "bytea" -> Types.BINARY;
            default -> Types.VARCHAR;
        };
    }

    private String resolveArrayType(String udtName) {
        if (udtName == null || udtName.isBlank()) {
            return "text";
        }
        return switch (udtName) {
            case "_int2" -> "int2";
            case "_int4" -> "int4";
            case "_int8" -> "int8";
            case "_float4" -> "float4";
            case "_float8" -> "float8";
            case "_numeric" -> "numeric";
            case "_bool" -> "bool";
            case "_uuid" -> "uuid";
            case "_varchar" -> "varchar";
            case "_text" -> "text";
            case "_json", "_jsonb" -> "jsonb";
            default -> udtName.startsWith("_") ? udtName.substring(1) : udtName;
        };
    }

    private boolean isPostgreSql(Connection connection) {
        try {
            return connection != null
                    && connection.getMetaData() != null
                    && connection.getMetaData().getDatabaseProductName() != null
                    && connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgres");
        } catch (Exception ex) {
            return false;
        }
    }

    private String qualifiedTable(String tableName) {
        return quoteIdentifier(SCHEMA_NAME) + "." + quoteIdentifier(tableName);
    }

    private String quoteQualifiedName(String qualifiedName) {
        String[] parts = qualifiedName.split("\\.");
        return java.util.Arrays.stream(parts)
                .map(this::stripQuotes)
                .map(this::quoteIdentifier)
                .collect(Collectors.joining("."));
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + stripQuotes(identifier).replace("\"", "\"\"") + "\"";
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BackupPayload {
        private int version;
        private String schema;
        private String createdAt;
        private List<TableBackup> tables;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TableBackup {
        private String name;
        private List<ExportColumn> columns;
        private JsonNode rows;
        private List<SequenceState> sequences;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportColumn {
        private String name;
        private String dataType;
        private String udtName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SequenceState {
        private String name;
        private long lastValue;
        private boolean called;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackupFile {
        private String filename;
        private byte[] content;
        private int tableCount;
        private long rowCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackupImportResult {
        private int tableCount;
        private long rowCount;
        private String sourceCreatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ColumnMeta {
        private String name;
        private String dataType;
        private String udtName;
        private boolean identity;
        private String identityGeneration;

        private ExportColumn toExportColumn() {
            return new ExportColumn(name, dataType, udtName);
        }
    }
}
