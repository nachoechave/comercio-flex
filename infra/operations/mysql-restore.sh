#!/usr/bin/env sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${BACKUP_FILE:?BACKUP_FILE is required}"

if [ "${RESTORE_CONFIRMED:-no}" != "yes" ]; then
  echo "Restore blocked. Set RESTORE_CONFIRMED=yes only in an isolated recovery database." >&2
  exit 2
fi

gzip -t "${BACKUP_FILE}"
export MYSQL_PWD="${MYSQL_PASSWORD}"
gzip -dc "${BACKUP_FILE}" | mysql \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT:-3306}" \
  --user="${MYSQL_USER}"

echo "Restore completed. Validate Flyway, tenant count and critical order totals before promotion."
