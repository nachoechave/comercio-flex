#!/usr/bin/env sh
set -eu

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${MYSQL_DATABASES:?MYSQL_DATABASES is required}"

backup_directory="${BACKUP_DIRECTORY:-/backups}"
retention_days="${BACKUP_RETENTION_DAYS:-14}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${backup_directory}/comercio-flex-${timestamp}.sql.gz"

mkdir -p "${backup_directory}"
export MYSQL_PWD="${MYSQL_PASSWORD}"

# --single-transaction crea una copia consistente para tablas InnoDB sin bloquear
# la operaciÃ³n normal de la tienda.
mysqldump \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT:-3306}" \
  --user="${MYSQL_USER}" \
  --single-transaction \
  --routines \
  --triggers \
  --set-gtid-purged=OFF \
  --databases ${MYSQL_DATABASES} | gzip -9 > "${backup_file}"

gzip -t "${backup_file}"
find "${backup_directory}" -type f -name 'comercio-flex-*.sql.gz' \
  -mtime "+${retention_days}" -delete

echo "Backup verified: ${backup_file}"
