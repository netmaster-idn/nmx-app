package com.netmaster.nmx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseBackupServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exportBackup_skipsSequenceInspectionWhenDatabaseDoesNotSupportIt() throws Exception {
        DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate, dataSource, objectMapper);

        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("from information_schema.tables"), eq(String.class), eq("public")))
                .thenReturn(List.of("company_profiles"));
        doAnswer(invocation -> {
            RowMapper<?> rowMapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("column_name")).thenReturn("id");
            when(rs.getString("data_type")).thenReturn("bigint");
            when(rs.getString("udt_name")).thenReturn("int8");
            when(rs.getString("is_identity")).thenReturn("NO");
            when(rs.getString("identity_generation")).thenReturn(null);
            return List.of(rowMapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(
                ArgumentMatchers.contains("from information_schema.columns"),
                ArgumentMatchers.<RowMapper<Object>>any(),
                eq("public"),
                eq("company_profiles")
        );
        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("constraint_type = 'PRIMARY KEY'"), eq(String.class), eq("public"), eq("company_profiles")))
                .thenReturn(List.of("id"));
        when(jdbcTemplate.queryForObject(ArgumentMatchers.startsWith("select coalesce(jsonb_agg"), eq(String.class)))
                .thenReturn("[{\"id\":1}]");
        when(jdbcTemplate.queryForObject(eq("select pg_get_serial_sequence(?, ?)"), eq(String.class), eq("public.company_profiles"), eq("id")))
                .thenThrow(new BadSqlGrammarException("sequence", "select pg_get_serial_sequence", new SQLException("unsupported")));

        DatabaseBackupService.BackupFile backup = service.exportBackup();

        assertThat(backup.getFilename()).startsWith("nmx-database-backup-");
        assertThat(backup.getTableCount()).isEqualTo(1);
        assertThat(backup.getRowCount()).isEqualTo(1);
        assertThat(new String(backup.getContent())).contains("\"name\" : \"company_profiles\"");
        assertThat(new String(backup.getContent())).contains("\"sequences\" : [ ]");
    }

    @Test
    void importBackup_skipsSequenceRestoreWhenDatabaseDoesNotSupportIt() throws Exception {
        DatabaseBackupService service = new DatabaseBackupService(jdbcTemplate, dataSource, objectMapper);

        String payload = """
                {
                  "version": 1,
                  "schema": "public",
                  "createdAt": "2026-04-06T16:00:00+08:00",
                  "tables": [
                    {
                      "name": "company_profiles",
                      "columns": [],
                      "rows": [],
                      "sequences": [
                        {
                          "name": "company_profiles_id_seq",
                          "lastValue": 5,
                          "called": true
                        }
                      ]
                    }
                  ]
                }
                """;

        when(jdbcTemplate.queryForList(ArgumentMatchers.contains("from information_schema.tables"), eq(String.class), eq("public")))
                .thenReturn(List.of("company_profiles"));
        when(jdbcTemplate.query(
                ArgumentMatchers.contains("from information_schema.columns"),
                ArgumentMatchers.<RowMapper<Object>>any(),
                eq("public"),
                eq("company_profiles")
        )).thenReturn(List.of());
        when(dataSource.getConnection()).thenReturn(connection);
        when(jdbcTemplate.update(eq("select setval(?, ?, ?)"), eq("company_profiles_id_seq"), eq(5L), eq(true)))
                .thenThrow(new BadSqlGrammarException("setval", "select setval", new SQLException("unsupported")));

        DatabaseBackupService.BackupImportResult result = service.importBackup(
                new MockMultipartFile("file", "backup.json", "application/json", payload.getBytes())
        );

        assertThat(result.getTableCount()).isEqualTo(1);
        assertThat(result.getRowCount()).isEqualTo(0);
        assertThat(result.getSourceCreatedAt()).isEqualTo("2026-04-06T16:00:00+08:00");
    }
}
