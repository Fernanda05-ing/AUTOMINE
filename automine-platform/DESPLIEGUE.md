# Guía de despliegue de AUTOMINE

Resultado final: AUTOMINE en **https://su-dominio** con certificado HTTPS automático, base de datos MySQL,
correos desde Gmail y una copia de seguridad diaria. Todo corre en un solo servidor con Docker.

Tiempo estimado: 1 hora, la mayor parte esperando que el dominio se active.

---

## Paso 1. Comprar el dominio

Compre el dominio en un registrador: para `.com.co` o `.co`, cualquier registrador acreditado
(GoDaddy, Namecheap, Hostinger, entre otros); para `.com`, cualquiera de ellos también.

Ejemplos de nombres: `automine.com.co`, `automine.co`, `automine-sas.com`.

No compre "hosting" ni "correo" adicionales: solo el dominio.

## Paso 2. Contratar el servidor (VPS)

Cree un servidor con estas características en cualquier proveedor (DigitalOcean, Hetzner, Vultr, AWS Lightsail, Contabo...):

| Opción | Valor |
|---|---|
| Sistema operativo | **Ubuntu 24.04 LTS** |
| Memoria | **2 GB** mínimo (recomendado 4 GB) |
| Disco | 25 GB o más |
| Región | La más cercana a Colombia (por ejemplo, Estados Unidos este o São Paulo) |

Al crearlo el proveedor le da una **dirección IP pública** (ejemplo `203.0.113.25`) y un usuario `root`.
Guárdelos.

## Paso 3. Apuntar el dominio al servidor (DNS)

En el panel del registrador del dominio, sección **DNS**, cree estos registros:

| Tipo | Nombre / Host | Valor | TTL |
|---|---|---|---|
| A | `@` | IP del servidor | 600 |
| A | `www` | IP del servidor | 600 |

Borre otros registros A o "parking" que el registrador haya creado para `@` o `www`.
El cambio tarda entre 5 minutos y unas horas. Para comprobarlo, en una terminal:

```bash
nslookup su-dominio.com.co
```

Cuando responda con la IP del servidor, continúe.

## Paso 4. Preparar Gmail para enviar correos

AUTOMINE envía correos (recuperación de contraseña, bienvenida, nómina, pagos, accidentes) desde una cuenta de Gmail.
Se recomienda una cuenta exclusiva, por ejemplo `notificaciones.automine@gmail.com`.

1. Entre a esa cuenta y active la **Verificación en dos pasos**: *Cuenta de Google > Seguridad > Verificación en dos pasos*.
2. Abra **https://myaccount.google.com/apppasswords**.
3. Escriba el nombre `AUTOMINE` y pulse **Crear**.
4. Google muestra una **contraseña de 16 letras**. Cópiela (sin espacios): es la que pide el instalador.
   No es la contraseña normal de la cuenta.

## Paso 5. Instalar AUTOMINE en el servidor

Conéctese al servidor (en Windows sirve PowerShell):

```bash
ssh root@IP-DEL-SERVIDOR
```

Descargue el código y ejecute el instalador:

```bash
git clone https://github.com/Fernanda05-ing/AUTOMINE.git
cd AUTOMINE/automine-platform
sudo sh deploy/instalar.sh
```

Si el repositorio es privado, GitHub pedirá usuario y un *token* personal
(GitHub > Settings > Developer settings > Personal access tokens) en lugar de la contraseña.

El instalador pregunta:

- **Dominio**: el del paso 1 (sin `https://`). Para incluir `www`, escriba `su-dominio.com.co, www.su-dominio.com.co`.
- **Correo del administrador**: con el que va a ingresar.
- **Cuenta y contraseña de aplicación de Gmail**: las del paso 4 (puede dejarlas vacías y configurarlas después).
- **Nombre y NIT de la empresa**: aparecen en certificados, desprendibles y correos.
- **Datos de demostración**: responda `s` solo si es para una presentación. En la operación real responda `N`.

Al final muestra la **contraseña temporal del administrador**: anótela.
La primera construcción tarda de 5 a 10 minutos.

## Paso 6. Verificar

1. Abra **https://su-dominio** (el candado de HTTPS aparece solo; si tarda, espere 1 o 2 minutos).
2. Ingrese con el correo del administrador y la contraseña temporal; el sistema le pedirá crear la suya.
3. Vaya a **Administración > Correo** y envíe un **correo de prueba** a su correo personal.
4. Vaya a **Administración > Parámetros** y revise el salario mínimo y el auxilio de transporte del año vigente.
5. Cree los usuarios reales en **Administración > Usuarios** o al registrar empleados (casilla *Crear usuario*).
   Cada persona recibe un correo con su contraseña temporal.

## Paso 7. App móvil apuntando al servidor

En el computador de desarrollo:

```powershell
cd automine-platform\mobile
flutter build apk --release --dart-define=API_URL=https://su-dominio
```

El APK queda en `mobile\build\app\outputs\flutter-apk\app-release.apk` y se puede compartir a los trabajadores.

---

## Operación diaria

Todos los comandos se ejecutan en el servidor, dentro de `AUTOMINE/automine-platform`.

| Tarea | Comando |
|---|---|
| Ver el estado | `docker compose ps` |
| Ver registros de la aplicación | `docker compose logs -f app` |
| Actualizar a una nueva versión | `git pull && docker compose up -d --build` |
| Reiniciar | `docker compose restart app` |
| Ver respaldos | `ls -lh respaldos/` |
| Restaurar un respaldo | `sh deploy/restaurar.sh respaldos/automine_AAAAMMDD_HHMMSS.sql.gz` |
| Cambiar correo, NIT, etc. | Editar `.env` y luego `docker compose up -d` |

**Respaldos:** se crea uno cada día en la carpeta `respaldos/` y se conservan 14 días
(`BACKUP_RETENTION_DAYS` en `.env`). Se recomienda copiar esa carpeta periódicamente fuera del servidor
(por ejemplo, a Google Drive o activando los *snapshots* del proveedor del VPS).

## Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| El navegador dice que el sitio no es seguro | El DNS aún no apunta al servidor o se abrió antes de tiempo. Verifique el paso 3 y ejecute `docker compose restart caddy`. |
| No llegan los correos | Revise en *Administración > Correo*. Si dice "no configurado", complete `MAIL_USERNAME` y `MAIL_PASSWORD` en `.env`. Si el proveedor bloquea el puerto 587, agregue `MAIL_PORT=465`, `MAIL_SSL=true` y `MAIL_STARTTLS=false`. Revise también la carpeta de spam. |
| La aplicación no arranca | `docker compose logs app`. Si menciona `JWT_SECRET`, la clave en `.env` es la de ejemplo: cámbiela. |
| Se acaba la memoria al construir | Use un servidor de 4 GB o agregue memoria de intercambio: `fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile`. |
