-- Usage example:
-- psql -h <host> -U <admin_user> -d <tenant_db> -v tenant_user='nmx_user' -f sql/postgres/grant_tenant_privileges.sql

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'tenant_user');
END $$;
GRANT USAGE ON SCHEMA public TO :"tenant_user";

GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
ON ALL TABLES IN SCHEMA public
TO :"tenant_user";

GRANT USAGE, SELECT, UPDATE
ON ALL SEQUENCES IN SCHEMA public
TO :"tenant_user";

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
ON TABLES TO :"tenant_user";

ALTER DEFAULT PRIVILEGES IN SCHEMA public
GRANT USAGE, SELECT, UPDATE
ON SEQUENCES TO :"tenant_user";
