-- Test database setup. Mirrors deploy/postgres/initdb/10-roles-and-databases.sh
-- (same roles and ownership; test-only passwords).
CREATE ROLE core_neha_owner LOGIN PASSWORD 'test-owner';
CREATE ROLE core_neha_app   LOGIN PASSWORD 'test-app' NOINHERIT;
CREATE DATABASE core_neha OWNER core_neha_owner ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE core_neha FROM PUBLIC;
GRANT CONNECT ON DATABASE core_neha TO core_neha_app;
\connect core_neha
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO core_neha_owner;
