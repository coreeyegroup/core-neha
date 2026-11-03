#!/bin/sh
# Runs once, on an empty data directory only.
#   core_neha_owner : owns the application database and schemas; used by Flyway
#   core_neha_app   : runtime role; data access only, no DDL, subject to row-level security
#   keycloak        : owns the identity database
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
     -v owner_pw="$CN_OWNER_PASSWORD" -v app_pw="$CN_APP_PASSWORD" -v kc_pw="$KC_DB_PASSWORD" <<'SQL'
CREATE ROLE core_neha_owner LOGIN PASSWORD :'owner_pw';
CREATE ROLE core_neha_app   LOGIN PASSWORD :'app_pw' NOINHERIT;
CREATE ROLE keycloak        LOGIN PASSWORD :'kc_pw';
CREATE DATABASE core_neha OWNER core_neha_owner ENCODING 'UTF8' TEMPLATE template0;
CREATE DATABASE keycloak  OWNER keycloak        ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE core_neha FROM PUBLIC;
REVOKE ALL ON DATABASE keycloak  FROM PUBLIC;
GRANT CONNECT ON DATABASE core_neha TO core_neha_app;
SQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname core_neha <<'SQL'
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO core_neha_owner;
SQL
echo "core-neha: roles and databases created"
