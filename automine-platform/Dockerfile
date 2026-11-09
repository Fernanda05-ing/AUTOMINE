# =====================================================================
# AUTOMINE - imagen unica: API Spring Boot + plataforma web React.
# Construir desde la carpeta automine-platform:
#   docker build -t automine .
# =====================================================================

# 1) Plataforma web (React + Vite)
FROM node:22-alpine AS web
WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

# 2) API (Spring Boot). La web compilada se sirve desde /static.
FROM maven:3.9.9-eclipse-temurin-21 AS api
WORKDIR /app/backend
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY web/public /app/web/public
COPY --from=web /app/web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# 3) Imagen final liviana
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S automine && adduser -S automine -G automine
WORKDIR /app
COPY --from=api /app/backend/target/automine-api.jar app.jar
USER automine
ENV PORT=8081 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Bogota"
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -qO- http://localhost:${PORT}/AutomineApi/Health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
