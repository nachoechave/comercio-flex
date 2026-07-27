#!/usr/bin/env bash
set -Eeuo pipefail

required_variables=(
  MYSQL_ROOT_PASSWORD
  MYSQL_APP_USER
  MYSQL_APP_PASSWORD
  MYSQL_MIGRATION_USER
  MYSQL_MIGRATION_PASSWORD
)

for variable_name in "${required_variables[@]}"; do
  if [[ -z "${!variable_name:-}" ]]; then
    echo "Missing required environment variable: ${variable_name}" >&2
    exit 1
  fi
done

# These values are interpolated into SQL identifiers/literals. Restricting them
# keeps this development bootstrap script predictable and avoids SQL injection.
safe_value_pattern='^[A-Za-z0-9_@%+=:,.-]+$'
for variable_name in MYSQL_APP_USER MYSQL_APP_PASSWORD MYSQL_MIGRATION_USER MYSQL_MIGRATION_PASSWORD; do
  if [[ ! "${!variable_name}" =~ ${safe_value_pattern} ]]; then
    echo "${variable_name} contains unsupported characters." >&2
    echo "Use only letters, numbers, and _ @ % + = : , . -" >&2
    exit 1
  fi
done

mysql --protocol=socket --user=root --password="${MYSQL_ROOT_PASSWORD}" <<-EOSQL
  CREATE DATABASE IF NOT EXISTS comercio_flex_control
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  CREATE DATABASE IF NOT EXISTS comercio_flex_tenant_a
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  CREATE DATABASE IF NOT EXISTS comercio_flex_tenant_b
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

  CREATE USER IF NOT EXISTS '${MYSQL_APP_USER}'@'%'
    IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
  ALTER USER '${MYSQL_APP_USER}'@'%'
    IDENTIFIED BY '${MYSQL_APP_PASSWORD}';

  GRANT SELECT, INSERT, UPDATE, DELETE
    ON comercio_flex_control.* TO '${MYSQL_APP_USER}'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE
    ON comercio_flex_tenant_a.* TO '${MYSQL_APP_USER}'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE
    ON comercio_flex_tenant_b.* TO '${MYSQL_APP_USER}'@'%';

  CREATE USER IF NOT EXISTS '${MYSQL_MIGRATION_USER}'@'%'
    IDENTIFIED BY '${MYSQL_MIGRATION_PASSWORD}';
  ALTER USER '${MYSQL_MIGRATION_USER}'@'%'
    IDENTIFIED BY '${MYSQL_MIGRATION_PASSWORD}';

  GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX,
        REFERENCES, CREATE VIEW, SHOW VIEW, TRIGGER
    ON comercio_flex_control.* TO '${MYSQL_MIGRATION_USER}'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX,
        REFERENCES, CREATE VIEW, SHOW VIEW, TRIGGER
    ON comercio_flex_tenant_a.* TO '${MYSQL_MIGRATION_USER}'@'%';
  GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX,
        REFERENCES, CREATE VIEW, SHOW VIEW, TRIGGER
    ON comercio_flex_tenant_b.* TO '${MYSQL_MIGRATION_USER}'@'%';
EOSQL
