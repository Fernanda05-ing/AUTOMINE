# AUTOMINE · Gestion minera integral

Plataforma para empresas mineras construida a partir de los documentos del proyecto:
arquitectura, SRS, historias de usuario, casos de uso y documento de servicios.
Cubre los 10 modulos (RF001 a RF050) y las reglas de negocio RN001 a RN006.

| Componente | Tecnologia | Carpeta |
|---|---|---|
| API REST | Spring Boot 3.5, Java 21, Spring Security + JWT, BCrypt, Flyway | `backend/` |
| Base de datos | MySQL 8 (esquema versionado con Flyway) | `backend/src/main/resources/db/migration` |
| Plataforma web | React 18 + Vite + TypeScript (la sirve la misma API) | `web/` |
| App movil | Flutter, APK Android 8+ | `mobile/` |
| Correo | Gmail SMTP (recuperacion de contrasena, bienvenida) | |
| Reportes | PDF (OpenPDF) y Excel (Apache POI) | |

La web se compila dentro de la API, asi que **se despliega una sola imagen Docker**
(un solo dominio y sin CORS) mas una base MySQL. Los archivos (documentos de empleados y
comprobantes) se guardan en la base y los PDF se generan al vuelo: el servidor no necesita disco persistente.

---

## Modulos

| Modulo | Requisitos | Que hace |
|---|---|---|
| Seguridad y acceso | RF001-RF005 | Login JWT, recuperacion por correo, 8 roles, cierre de sesion con revocacion del token, bitacora de accesos, bloqueo tras 5 intentos fallidos |
| Empleados | RF006-RF010 | Registro, edicion, inactivacion, historial laboral, contratos, documentos adjuntos (RN003, RN004) |
| Nomina | RF011-RF016 | Salario proporcional, horas extra, auxilio de transporte, salud, pension, solidaridad, neto, desprendible PDF, historial (RN001) |
| Consignaciones | RF017-RF021 | Registro individual o por lote, referencia bancaria, comprobante, aprobacion solo por Contabilidad (RN002, RN005) |
| Certificados | RF022-RF025 | Certificado PDF con firma institucional, codigo de validacion y pagina publica `/validar` (RN006) |
| Inventario | RF026-RF030 | Productos, entradas, salidas, ajustes, kardex, costo promedio, alertas de stock, maquinaria y mantenimientos |
| Produccion | RF031-RF035 | Toneladas por frente y turno, personal asignado, reporte diario, indicadores y cumplimiento de meta |
| SG-SST | RF036-RF040 | Accidentes, incidentes, entrega de EPP (descuenta inventario), capacitaciones con asistencia, matriz de riesgos |
| Contabilidad | RF041-RF045 | Ingresos, gastos, caja menor, balance general, estado de resultados |
| Reportes | RF046-RF050 | Exportacion PDF y Excel, dashboard gerencial, informe mensual, KPI con variacion mensual |

Ademas: auditoria de todas las acciones (RNF005), API documentada en Swagger (RNF010), parametros legales editables.

### Roles

| Rol | Ve |
|---|---|
| ADMIN | Todo, mas usuarios, bitacora, auditoria y parametros |
| GERENTE | Dashboard, reportes, produccion y consulta de los demas modulos |
| CONTADOR | Nomina, consignaciones (unico que aprueba), contabilidad, reportes |
| RRHH | Empleados, certificados, consulta de nomina |
| SUPERVISOR | Produccion y turnos, reporte de incidentes |
| SGSST | Accidentes, EPP, capacitaciones, riesgos |
| ALMACENISTA | Inventario y maquinaria |
| EMPLEADO | Sus pagos, consignaciones, certificados y turnos (web "Mi portal" y app movil) |

---

## Ejecutar en local (sin Docker)

Requisitos: JDK 21+, Maven, Node 18+, MySQL 8 en `localhost:3306`.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\iniciar-local.ps1 -Demo
```

El script pide la contrasena de MySQL, crea la base `automine_db` (Flyway crea las tablas),
levanta la API en `http://localhost:8081` y la web en `http://localhost:5173`.
Con `-Demo` carga datos de ejemplo de todos los modulos. Para detener todo: `scripts\detener-local.ps1`.

Usuarios (contrasena = `ADMIN_PASSWORD`, por defecto `Automine2026*`):
`admin@`, y con datos demo tambien `gerente@`, `contador@`, `rrhh@`, `supervisor@`, `sgsst@`, `almacen@`, `empleado@` (todos `@automine.co`).

Documentacion de la API: `http://localhost:8081/AutomineApi/swagger-ui.html`

### Pruebas

```powershell
cd backend; mvn test
```

Hay 14 pruebas automaticas: el calculo de nomina, las reglas RN001 a RN006, los permisos por rol,
el alcance del empleado, la revocacion del token y los reportes. Corren sobre H2, sin necesitar MySQL.

---

## Desplegar

**Guía completa paso a paso: [DESPLIEGUE.md](DESPLIEGUE.md)** (dominio, servidor, DNS, Gmail e instalación).

Resumen: en un servidor Ubuntu con el dominio apuntando a su IP,

```bash
git clone https://github.com/Fernanda05-ing/AUTOMINE.git
cd AUTOMINE/automine-platform
sudo sh deploy/instalar.sh
```

El instalador instala Docker, genera el archivo `.env` con claves aleatorias y levanta cuatro servicios
(`docker-compose.yml`): MySQL, AUTOMINE, Caddy (HTTPS automático con Let's Encrypt para el dominio) y un
servicio de respaldos que guarda una copia diaria de la base en `respaldos/` durante 14 días.

Alternativa sin servidor propio: `render.yaml` (Render + MySQL administrado externo).

### Seguridad en producción

- `APP_ENV=production` exige una `JWT_SECRET` propia y no carga datos de demostración (`APP_ENV=demo` sí los permite).
- El administrador inicial y los usuarios creados por otra persona deben cambiar su contraseña temporal al primer ingreso;
  hasta entonces la API solo les permite cambiarla.
- Bloqueo de 15 minutos tras 5 intentos fallidos de inicio de sesión, tokens revocados al cerrar sesión,
  bitácora de accesos y auditoría de acciones.

### Variables de entorno

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DOMAIN`, `PUBLIC_URL` | Sí | Dominio del sitio y su URL (enlaces de correos y certificados) |
| `APP_ENV` | Sí | `production` o `demo` |
| `DB_PASSWORD`, `DB_ROOT_PASSWORD` | Sí | Claves de MySQL |
| `JWT_SECRET` | Sí | 32+ caracteres aleatorios |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Sí | Administrador inicial (solo con la base vacía) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Para correo | Cuenta de Gmail y su contraseña de aplicación |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_SSL`, `MAIL_STARTTLS`, `MAIL_FROM` | No | Otro servidor SMTP o puerto 465 |
| `COMPANY_NAME`, `COMPANY_NIT`, `COMPANY_CITY` | No | Datos en certificados, PDF y correos |
| `DEMO_DATA` | No | `true` carga datos de ejemplo (solo con `APP_ENV=demo` o en desarrollo) |
| `BACKUP_RETENTION_DAYS` | No | Días que se conservan los respaldos (14) |

### Correos que envía

Recuperación de contraseña, bienvenida con contraseña temporal, nómina lista para aprobar (contador),
desprendible disponible (cada empleado), pago consignado (empleado), accidente o incidente reportado
(SG-SST y gerencia) y un resumen diario de stock bajo y mantenimientos (almacén). Se prueban en
*Administración > Correo* y las notificaciones se apagan con el parámetro `NOTIFICACIONES_CORREO = 0`.

---

## App movil (APK)

```powershell
cd mobile
flutter build apk --release --dart-define=API_URL=https://su-dominio.com
```

El APK queda en `mobile/build/app/outputs/flutter-apk/app-release.apk`. Sin `API_URL` apunta a
`http://10.0.2.2:8081`, que es la API local vista desde el emulador de Android. En un celular fisico conectado
a la misma red WiFi use la IP del computador (`http://192.168.x.x:8081`).

Funciones: inicio de sesion y recuperacion de contrasena, pagos con desprendible PDF, consignaciones,
certificados laborales, turnos asignados, perfil con cambio de contrasena y, para supervisores, el reporte
diario y el registro de produccion.

---

## Logo

Guarde el logo como **`web/public/logo.png`** y copielo tambien a **`mobile/assets/logo.png`**.
La web lo muestra en el inicio de sesion y en el menu, los PDF lo incluyen en el encabezado (Maven lo copia
al compilar) y la app movil lo usa en el inicio de sesion y el perfil. Mientras no exista se muestra el icono por defecto.

---

## Estructura

```
automine-platform/
  backend/            API Spring Boot (paquetes por modulo: security, empleados, nomina, consignaciones,
                      certificados, inventario, produccion, sgsst, contabilidad, reportes)
  web/                Plataforma web React
  mobile/             App Flutter
  scripts/            Arranque y parada en local (PowerShell)
  Dockerfile          Imagen unica API + web
  docker-compose.yml  Despliegue con MySQL incluido
  render.yaml         Blueprint para Render
```

Los parametros legales de nomina (salario minimo, auxilio de transporte, horas al mes, recargos, porcentajes)
estan en la tabla `parametros` y se editan desde **Administracion > Parametros**. Revise que correspondan al anio vigente.
