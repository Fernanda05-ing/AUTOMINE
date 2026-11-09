#!/bin/sh
# Respaldo automatico de la base de datos de AUTOMINE.
# Corre dentro del contenedor "backup" (imagen mysql): cada BACKUP_INTERVAL_SECONDS
# genera un .sql.gz en /backups y borra los que superan BACKUP_RETENTION_DAYS.
set -eu

export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
INTERVALO="${BACKUP_INTERVAL_SECONDS:-86400}"
RETENCION="${BACKUP_RETENTION_DAYS:-14}"
mkdir -p /backups

echo "Respaldo AUTOMINE: cada ${INTERVALO}s, se conservan ${RETENCION} dias"
while true; do
    ARCHIVO="/backups/automine_$(date +%Y%m%d_%H%M%S).sql.gz"
    if mysqldump -h db -u root --single-transaction --routines --triggers --no-tablespaces \
        --set-gtid-purged=OFF automine_db | gzip > "${ARCHIVO}.tmp"; then
        mv "${ARCHIVO}.tmp" "${ARCHIVO}"
        echo "$(date '+%F %T') respaldo creado: ${ARCHIVO} ($(du -h "${ARCHIVO}" | cut -f1))"
    else
        rm -f "${ARCHIVO}.tmp"
        echo "$(date '+%F %T') ERROR: no se pudo crear el respaldo" >&2
    fi
    find /backups -name 'automine_*.sql.gz' -mtime +"${RETENCION}" -delete
    sleep "${INTERVALO}"
done
