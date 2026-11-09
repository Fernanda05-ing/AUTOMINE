#!/bin/sh
# Restaura un respaldo en la base de AUTOMINE. Se ejecuta en el servidor, en la carpeta automine-platform:
#   sh deploy/restaurar.sh respaldos/automine_20261109_063000.sql.gz
# ATENCION: reemplaza los datos actuales por los del respaldo.
set -eu

if [ $# -ne 1 ] || [ ! -f "$1" ]; then
    echo "Uso: sh deploy/restaurar.sh <archivo .sql.gz>"
    echo "Respaldos disponibles:"
    ls -1t respaldos/ 2>/dev/null | head -20
    exit 1
fi

printf "Se reemplazaran TODOS los datos actuales por %s. Escriba SI para continuar: " "$1"
read -r RESPUESTA
[ "$RESPUESTA" = "SI" ] || { echo "Cancelado."; exit 1; }

docker compose stop app
gunzip -c "$1" | docker compose exec -T -e MYSQL_PWD="$(grep '^DB_ROOT_PASSWORD=' .env | cut -d= -f2-)" db \
    mysql -u root automine_db
docker compose start app
echo "Respaldo restaurado."
