# Publicar AUTOMINE gratis: Supabase + Render + Vercel

Esquema para **mostrar el proyecto** sin costo:

```
Navegador / celular ──► Vercel (web)  ──/AutomineApi──►  Render (API Java)  ──►  Supabase
                         automine.vercel.app               automine-api.onrender.com   PostgreSQL + Storage
```

- **Supabase**: base de datos PostgreSQL y archivos (fotos, certificados) en un bucket privado.
- **Render**: la API (Spring Boot) en Docker, plan gratis.
- **Vercel**: la web; reenvía `/AutomineApi` a Render, así el navegador ve un solo sitio
  (sin problemas de CORS y el inicio con Google funciona).

> Límites del plan gratis: Render apaga la API tras 15 min sin uso (la primera visita tarda
> ~1 minuto en despertar) y Supabase pausa la base tras 7 días sin actividad. El flujo
> `.github/workflows/mantener-activo.yml` las mantiene despiertas (paso 5).
> Base gratis: 500 MB; archivos: 1 GB.

Nunca escriba contraseñas ni llaves en archivos del repositorio: solo en los paneles de
Supabase, Render, Vercel y GitHub.

---

## 1. Supabase (base de datos y archivos)

1. Cree una cuenta en https://supabase.com y un proyecto **automine** (región *East US* o *South America (São Paulo)*).
   Guarde la **contraseña de la base de datos** que le pide.
2. **Connect** (arriba) → **JDBC** → tipo **Session pooler** (funciona con IPv4, que es lo que usa Render). Anote:
   - `DB_URL` = `jdbc:postgresql://aws-0-REGION.pooler.supabase.com:5432/postgres?sslmode=require`
   - `DB_USERNAME` = `postgres.XXXXXXXX` (usuario con la referencia del proyecto)
   - `DB_PASSWORD` = la contraseña del punto 1
3. **Project Settings → API**: anote
   - `SUPABASE_URL` = *Project URL* (`https://XXXXXXXX.supabase.co`)
   - `SUPABASE_SERVICE_KEY` = la llave **service_role** (secreta: solo va en Render).

No hace falta crear tablas ni el bucket: la API los crea al arrancar (migraciones Flyway en
`db/migration/postgresql` y bucket privado `automine`).

## 2. Subir el código a GitHub

Render y Vercel despliegan desde GitHub. Suba la rama con los cambios (el repositorio es
público: verifique que no haya archivos `.env` ni contraseñas).

## 3. Render (API)

1. Cree una cuenta en https://render.com con su GitHub.
2. **New → Blueprint** → elija el repositorio. Render lee `automine-platform/render.yaml` y crea
   el servicio **automine-api** (plan Free).
3. Le pedirá los valores marcados como secretos:

| Variable | Valor |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | los de Supabase (paso 1.2) |
| `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` | los de Supabase (paso 1.3) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | el administrador inicial (contraseña fuerte: 8+ caracteres, letras y números) |
| `PUBLIC_URL` y `CORS_ORIGINS` | la dirección de Vercel (paso 4), p. ej. `https://automine.vercel.app` |
| `MAIL_PASSWORD` | la contraseña de aplicación de Gmail de autominesas@gmail.com |
| `GOOGLE_CLIENT_ID` | el ID de cliente OAuth de Google (opcional) |
| `COMPANY_NIT` | el NIT de la empresa |

4. Espere el primer despliegue (5–10 min). Pruebe `https://automine-api.onrender.com/AutomineApi/Health`.
   Si Render le asignó otro nombre, cámbielo en `web/vercel.json` (línea `destination`).

`APP_ENV=demo` y `DEMO_DATA=true` cargan empleados, nómina, producción e inventario de ejemplo
para la demostración. Los usuarios de ejemplo usan la misma contraseña del administrador.
Para usarlo en serio cambie a `APP_ENV=production` y `DEMO_DATA=false` con una base nueva.

## 4. Vercel (web)

1. Cree una cuenta en https://vercel.com con su GitHub → **Add New → Project** → el repositorio.
2. **Root Directory**: `automine-platform/web`. Framework: Vite (lo detecta solo). No necesita variables.
3. **Deploy**. Obtendrá `https://automine-XXXX.vercel.app` (puede cambiar el nombre en Settings → Domains).
4. Vuelva a Render y ponga esa dirección en `PUBLIC_URL` y `CORS_ORIGINS` (se redespliega solo).

## 5. Mantenerla despierta (opcional, recomendado)

GitHub → el repositorio → **Settings → Secrets and variables → Actions → Variables → New**:
`AUTOMINE_URL` = `https://automine-api.onrender.com`. El flujo *Mantener activo* consulta la API
cada 10 minutos.

## 6. Google y celular

- **Google**: en Google Cloud → Credenciales → su cliente OAuth web → *Orígenes de JavaScript
  autorizados* → agregue la dirección de Vercel.
- **App móvil**: compílela apuntando a Vercel:
  ```
  flutter build apk --release --dart-define=API_URL=https://automine-XXXX.vercel.app
  ```

## Problemas frecuentes

| Síntoma | Causa / solución |
|---|---|
| La web carga pero no inicia sesión | La API está despertando (espere 1 min) o `vercel.json` apunta a otro nombre de Render |
| Render: `Connection refused` / `Network unreachable` a Supabase | Use la cadena **Session pooler** (no la *Direct connection*, que es solo IPv6) |
| Render reinicia por memoria | Ya está ajustado (`JAVA_OPTS`); evite cargar muchos datos de demostración |
| Fotos o certificados no abren | Revise `SUPABASE_URL` y `SUPABASE_SERVICE_KEY` (llave *service_role*, no la *anon*) |

## Instalación local (sin cambios)

En su computador AUTOMINE sigue usando MySQL (`scripts/desplegar-local.ps1`) y guarda los
archivos dentro de la base. Las migraciones de cada motor están en `db/migration/mysql` y
`db/migration/postgresql`; las pruebas validan ambos (`PostgresCompatibilidadTest`).
