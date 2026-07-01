# PixelPlatform — ARSW 2026-1

Plataforma colaborativa de pixeles en tiempo real, inspirada en r/place.
Multiples usuarios pintan pixeles en un lienzo compartido, se comunican por voz y texto,
colaboran en lienzos privados y generan patrones desde imagenes usando IA.

**Autor:** Tomas Espitia Quiroga — Ingenieria de Sistemas, ECI  
**Curso:** Arquitecturas de Software (ARSW) 2026-1  
**Arquitectura:** Microservicios  
**Azure DevOps:** https://dev.azure.com/tomasespitia-q/PixelPlatform-ARSW-2026

---

## Stack tecnologico

| Capa | Tecnologia |
|---|---|
| Backend | Java 21 + Spring Boot 3.3.4 (un microservicio por capacidad) |
| API Gateway | Spring Cloud Gateway |
| Tiempo real | WebSockets (STOMP) + WebRTC (simple-peer) |
| Cache / Estado | Redis |
| Mensajeria entre servicios | Redis Streams (eventos asincronos) |
| Persistencia | PostgreSQL (una base de datos por microservicio) |
| Frontend | React + Vite |
| Modulo IA | Java AWT — imagen a patron de pixeles |
| Infraestructura | Docker Compose + Amazon EC2 + Nginx |

---

## Microservicios

| Servicio | Puerto | Responsabilidad | Estado |
|---|---|---|---|
| api-gateway | 8080 | Enrutamiento + validacion JWT | Pendiente |
| auth-service | 8081 | Registro, login, emision de JWT | Completo |
| canvas-service | 8082 | Lienzo colaborativo, WebSocket STOMP | Pendiente |
| chat-service | 8083 | Canales de texto e historial | Pendiente |
| signaling-service | 8084 | Senalizacion WebRTC para voz | Pendiente |
| ai-service | 8085 | Conversion de imagen a patron de pixeles | Pendiente |
| observability-service | 8086 | Consumo de eventos, metricas y KPIs | Pendiente |

---

## Requisitos previos

- Docker Desktop instalado y corriendo
- Java 21
- Maven 3.9+
- Node.js 20+ (para el frontend, cuando se implemente)

---

## Como correr el proyecto

### Opcion 1 — Solo infraestructura (desarrollo local)

Levanta PostgreSQL y Redis sin compilar ningun microservicio.
Util para correr cada servicio desde el IDE o con `mvn spring-boot:run`.

```bash
docker compose up postgres redis -d
```

Verifica que esten healthy:

```bash
docker compose ps
```

### Opcion 2 — Infraestructura + microservicio especifico

Levanta solo lo necesario para trabajar en un servicio en particular.
Ejemplo para auth-service:

```bash
# Terminal 1: infraestructura
docker compose up postgres redis -d

# Terminal 2: auth-service en modo dev
cd auth-service
./mvnw spring-boot:run -Dspring.profiles.active=dev
```

El servicio queda disponible en `http://localhost:8081`.

### Opcion 3 — Stack completo con Docker Compose

> Requiere que todos los microservicios esten implementados y que
> exista la carpeta frontend/ con su Dockerfile.

```bash
docker compose up --build
```

La aplicacion queda disponible en `http://localhost` (puerto 80, via Nginx).

---

## Probar auth-service

Con el servicio corriendo en `http://localhost:8081`:

### Registro de usuario

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "tomas",
    "email": "tomas@eci.edu.co",
    "password": "password123"
  }'
```

Respuesta esperada (201 Created):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "tomas",
  "email": "tomas@eci.edu.co",
  "expiresIn": 86400000
}
```

### Login

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "tomas@eci.edu.co",
    "password": "password123"
  }'
```

Respuesta esperada (200 OK):
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "tomas",
  "email": "tomas@eci.edu.co",
  "expiresIn": 86400000
}
```

### Health check

```bash
curl http://localhost:8081/actuator/health
```

---

## Correr los tests

Cada microservicio tiene sus propios tests unitarios que NO requieren
infraestructura levantada (usan Mockito).

```bash
# auth-service
cd auth-service
./mvnw test
```

Resultado esperado: 4 tests pasando en AuthServiceTest + 1 en AuthServiceApplicationTests.

---

## Estructura del monorepo

```
pixel-platform-arsw/
├── api-gateway/              Spring Cloud Gateway
├── auth-service/             Registro, login, JWT
│   ├── src/
│   │   ├── main/java/...     Codigo fuente
│   │   └── test/java/...     Tests unitarios
│   ├── pom.xml
│   └── mvnw
├── canvas-service/           Lienzo colaborativo (WebSocket + Redis)
├── chat-service/             Canales de texto
├── signaling-service/        Senalizacion WebRTC
├── ai-service/               IA: imagen a patron de pixeles
├── observability-service/    Metricas, KPIs, dashboard
├── frontend/                 React + Vite
├── nginx/
│   └── nginx.conf            Configuracion del reverse proxy
├── docker-compose.yml        Orquestacion de todos los servicios
├── Dockerfile                Build del backend (multi-stage)
└── .dockerignore
```

---

## Variables de entorno

En desarrollo los valores estan en `application-dev.properties` de cada servicio.
En produccion (Docker Compose) se inyectan como variables de entorno:

| Variable | Descripcion | Ejemplo |
|---|---|---|
| DB_HOST | Host de PostgreSQL | postgres |
| DB_PORT | Puerto de PostgreSQL | 5432 |
| DB_NAME | Nombre de la base de datos | auth_db |
| DB_USER | Usuario de PostgreSQL | pixel_user |
| DB_PASS | Contrasena de PostgreSQL | pixel_pass |
| REDIS_HOST | Host de Redis | redis |
| REDIS_PORT | Puerto de Redis | 6379 |
| JWT_SECRET | Secreto para firmar JWT (min 32 chars) | — |
| JWT_EXPIRATION_MS | Tiempo de vida del token en ms | 86400000 |
| SPRING_PROFILES_ACTIVE | Perfil activo | prod |
