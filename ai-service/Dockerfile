# ================================================================
# PixelPlatform — ai-service/Dockerfile
# Java 21 | Spring Boot 3.3.4 | Multi-stage build
# ================================================================

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine AS runtime
LABEL maintainer="Tomas Espitia Quiroga"
LABEL project="PixelPlatform ARSW 2026-1 - ai-service"

RUN addgroup -S pixelapp && adduser -S pixelapp -G pixelapp
WORKDIR /app
COPY --from=builder /build/target/ai-service-0.0.1-SNAPSHOT.jar app.jar
USER pixelapp

EXPOSE 8085

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
