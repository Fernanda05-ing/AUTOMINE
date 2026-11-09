#!/bin/sh
# Instalador de AUTOMINE para un servidor Ubuntu 22.04/24.04.
# En el servidor, dentro de la carpeta automine-platform:
#   sudo sh deploy/instalar.sh
# Instala Docker, crea el archivo .env con claves aleatorias y levanta la plataforma.
set -eu

cd "$(dirname "$0")/.."

if ! command -v docker >/dev/null 2>&1; then
    echo ">> Instalando Docker..."
    curl -fsSL https://get.docker.com | sh
fi

if [ ! -f .env ]; then
    echo ">> Configuracion inicial de AUTOMINE"
    printf "Dominio (ej. automine.com.co): "; read -r DOMINIO
    printf "Correo del administrador (ej. admin@%s): " "$DOMINIO"; read -r ADMIN
    printf "Cuenta de Gmail para enviar correos (vacio = sin correo por ahora): "; read -r GMAIL
    CLAVE_GMAIL=""
    if [ -n "$GMAIL" ]; then
        printf "Contrasena de aplicacion de Gmail (16 letras, sin espacios): "; read -r CLAVE_GMAIL
    fi
    printf "Nombre legal de la empresa [AUTOMINE S.A.S.]: "; read -r EMPRESA
    printf "NIT de la empresa [900.123.456-7]: "; read -r NIT
    printf "Cargar datos de demostracion para presentar? (s/N): "; read -r DEMO

    aleatorio() { tr -dc 'A-Za-z0-9' </dev/urandom | head -c "$1"; }
    ADMIN_CLAVE="Am$(aleatorio 10)7*"
    MODO=production; DEMO_DATA=false
    case "$DEMO" in s|S|si|SI) MODO=demo; DEMO_DATA=true ;; esac

    cat > .env <<EOF
DOMAIN=${DOMINIO}
PUBLIC_URL=https://${DOMINIO}
APP_ENV=${MODO}
DEMO_DATA=${DEMO_DATA}
DB_PASSWORD=$(aleatorio 28)
DB_ROOT_PASSWORD=$(aleatorio 28)
JWT_SECRET=$(aleatorio 48)
ADMIN_EMAIL=${ADMIN}
ADMIN_PASSWORD=${ADMIN_CLAVE}
MAIL_USERNAME=${GMAIL}
MAIL_PASSWORD=${CLAVE_GMAIL}
COMPANY_NAME=${EMPRESA:-AUTOMINE S.A.S.}
COMPANY_NIT=${NIT:-900.123.456-7}
COMPANY_CITY=Medellín, Antioquia
BACKUP_RETENTION_DAYS=14
EOF
    chmod 600 .env
    echo ""
    echo "================================================================"
    echo " Administrador: ${ADMIN}"
    echo " Contrasena temporal: ${ADMIN_CLAVE}"
    echo " (se pedira cambiarla al primer ingreso; guardela ahora)"
    echo "================================================================"
fi

echo ">> Construyendo y levantando AUTOMINE (la primera vez tarda varios minutos)..."
docker compose up -d --build

# Firewall: solo SSH, HTTP y HTTPS
if command -v ufw >/dev/null 2>&1; then
    ufw allow OpenSSH >/dev/null; ufw allow 80/tcp >/dev/null; ufw allow 443 >/dev/null
    ufw --force enable >/dev/null || true
fi

DOMINIO=$(grep '^DOMAIN=' .env | cut -d= -f2 | cut -d, -f1)
echo ""
echo "Listo. En uno o dos minutos abra: https://${DOMINIO}"
echo "Estado:    docker compose ps"
echo "Registros: docker compose logs -f app"
echo "Respaldos: carpeta ./respaldos (uno diario, se conservan 14 dias)"
