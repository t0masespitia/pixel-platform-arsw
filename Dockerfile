# ================================================================
# PixelPlatform — Dockerfile (Backend Spring Boot)
# Java 21 | Spring Boot 3.5.15 | Multi-stage build
# ================================================================

# ── Etapa 1: Build ─────────────────────────────────────────────
# Compila el proyecto y genera el JAR
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copiar primero solo el pom.xml para aprovechar el cache de capas.
# Si el código cambia pero las dependencias no, Docker no re-descarga Maven.
COPY backend/pom.xml .
COPY backend/.mvn/ .mvn/
COPY backend/mvnw .
RUN chmod +x mvnw

# Descargar dependencias en capa separada (cache-friendly)
RUN ./mvnw dependency:go-offline -B

# Copiar el código fuente y compilar
COPY backend/src/ src/
RUN ./mvnw package -DskipTests -B

# ── Etapa 2: Runtime ───────────────────────────────────────────
# Imagen final liviana: solo JRE, sin Maven ni fuentes
FROM eclipse-temurin:21-jre-alpine AS runtime

# Metadatos
LABEL maintainer="Tomas Espitia Quiroga"
LABEL project="PixelPlatform ARSW 2026-1"

# Usuario no-root por seguridad (buena práctica en contenedores)
RUN addgroup -S pixelapp && adduser -S pixelapp -G pixelapp

WORKDIR /app

# Copiar solo el JAR generado en la etapa anterior
COPY --from=builder /build/target/pixelplatform-0.0.1-SNAPSHOT.jar app.jar

# Cambiar al usuario no-root
USER pixelapp

# Puerto que expone Spring Boot
EXPOSE 8080

# Arrancar la app con perfil prod y opciones JVM optimizadas para contenedor
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
