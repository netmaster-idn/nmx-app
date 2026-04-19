-- Usage example:
-- psql -h <host> -U <admin_user> -d <tenant_db> -v tenant_user='nmx_user' -f sql/postgres/check_tenant_privileges.sql

SELECT current_database() AS tenant_database,
       current_user AS executed_as,
       :'tenant_user' AS checked_user;

SELECT has_database_privilege(:'tenant_user', current_database(), 'CONNECT') AS can_connect;

SELECT has_schema_privilege(:'tenant_user', 'public', 'USAGE') AS schema_usage,
       has_schema_privilege(:'tenant_user', 'public', 'CREATE') AS schema_create;

SELECT table_schema,
       table_name,
       has_table_privilege(:'tenant_user', format('%I.%I', table_schema, table_name), 'SELECT') AS can_select,
       has_table_privilege(:'tenant_user', format('%I.%I', table_schema, table_name), 'INSERT') AS can_insert,
       has_table_privilege(:'tenant_user', format('%I.%I', table_schema, table_name), 'UPDATE') AS can_update,
       has_table_privilege(:'tenant_user', format('%I.%I', table_schema, table_name), 'DELETE') AS can_delete,
       has_table_privilege(:'tenant_user', format('%I.%I', table_schema, table_name), 'TRUNCATE') AS can_truncate,
       has_table_privilege(:'tenant_user', format('%I.%I', table_schema, table_name), 'REFERENCES') AS can_references,
       has_table_privilege(:'tenant_user', format('%I.%I', table_schema, table_name), 'TRIGGER') AS can_trigger
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

SELECT sequence_schema,
       sequence_name,
       has_sequence_privilege(:'tenant_user', format('%I.%I', sequence_schema, sequence_name), 'USAGE') AS can_usage,
       has_sequence_privilege(:'tenant_user', format('%I.%I', sequence_schema, sequence_name), 'SELECT') AS can_select,
       has_sequence_privilege(:'tenant_user', format('%I.%I', sequence_schema, sequence_name), 'UPDATE') AS can_update
FROM information_schema.sequences
WHERE sequence_schema = 'public'
ORDER BY sequence_name;

