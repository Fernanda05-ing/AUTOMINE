# AUTOMINE Platform

Plataforma empresarial SaaS para gestion administrativa, operativa y financiera de companias mineras.

## Stack
- Backend: Java 21, Spring Boot, Spring Security, JWT, JPA Hibernate, Maven
- Database: MySQL 8
- Frontend: Angular 18, TypeScript, Angular Material
- Mobile APK: Flutter
- DevOps: Docker Compose, GitHub Actions CI/CD

## Estructura
- `database/`: script SQL profesional con PK, FK, indices, constraints, auditoria y soft delete
- `backend/`: API REST con arquitectura limpia (Controller, Service, Repository, Entity, DTO, Security, Config, Exception)
- `frontend/`: SPA Angular con login, shell corporativo, dashboard y modulo empleados
- `mobile/`: app Flutter lista para compilar como APK
- `devops/`: docker-compose para levantar stack completo

## Fases implementadas
1. Base de datos: `database/01_schema_automine.sql`
2. Backend core: seguridad JWT, usuarios, empleados, nomina, swagger
3. Frontend core: login, dashboard, empleados
4. APK base: login + home con modulos empleados
5. Deploy: Dockerfiles + `devops/docker-compose.yml` + pipeline CI/CD

## Run local
### 1) Docker
```bash
cd automine-platform/devops
docker compose up --build
```

### 2) Backend standalone
```bash
cd automine-platform/backend
mvn spring-boot:run
```

### 3) Frontend standalone
```bash
cd automine-platform/frontend
npm install
npm start
```

### 4) Mobile Flutter
```bash
cd automine-platform/mobile
flutter pub get
flutter run
```

## Endpoints base
- `POST /api/auth/login`
- `POST /api/auth/forgot-password`
- `POST /api/auth/logout`
- `GET /api/users`
- `POST /api/users`
- `DELETE /api/users/{id}`
- `GET /api/employees`
- `POST /api/employees`
- `PATCH /api/employees/{id}/retire`
- `GET /api/payroll/entries`
- `POST /api/payroll/entries`

Swagger: `http://localhost:8080/swagger-ui`

## Funciones premium incluidas en base tecnica
- Notificaciones: entidad `notifications` para push y recordatorios de pagos
- Auditoria: tabla `audit_logs`
- Firma digital: campo `digital_signature_url` en entrega de EPP
- Dashboard IA predictivo: bloque inicial en frontend para recomendaciones
- Reportes PDF/Excel: base de datos y frontend preparados para exportaciones (siguiente sprint)

## Roadmap recomendado de endurecimiento productivo
- Implementar refresh tokens y revocacion persistente
- Agregar paginacion/filtros avanzados en todos los CRUD
- Integrar reporteria PDF/Excel en backend
- Implementar Firebase Cloud Messaging para push mobile
- Integrar observabilidad (Prometheus + Grafana + ELK)
- Despliegue SaaS multitenant con Kubernetes
