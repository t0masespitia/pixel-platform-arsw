# PixelPlatform — ARSW 2026-1

Plataforma colaborativa de pixeles en tiempo real, inspirada en r/place.
Multiples usuarios pintan pixeles en un lienzo compartido, se comunican por voz y texto,
colaboran en lienzos privados, generan patrones desde imagenes (PNG, JPG, GIF, BMP) y
pueden revisar el historial de construccion de un lienzo.

**Autor:** Tomas Espitia Quiroga — Ingenieria de Sistemas, ECI
**Curso:** Arquitecturas de Software (ARSW) 2026-1
**Arquitectura:** Microservicios
**Azure DevOps:** https://dev.azure.com/tomasespitia-q/PixelPlatform-ARSW-2026

---

## Stack tecnologico

| Capa | Tecnologia |
|---|---|
| Backend | Java 21 + Spring Boot 3.3.4 (un microservicio por capacidad) |
| API Gateway | Spring Cloud Gateway (JWT, rate limiting con Redis) |
| Resiliencia | Resilience4j (circuit breaker + retry) entre microservicios |
| Tiempo real | WebSockets (STOMP/SockJS) + WebRTC (voz) |
| Cache / Estado | Redis (cooldown de pintado, rate limiting, historial de eventos) |
| Mensajeria entre servicios | Redis Streams (eventos de dominio, historial por lienzo) |
| Persistencia | PostgreSQL (una base de datos por microservicio que la necesita) |
| Frontend | React + Vite |
| Modulo de imagenes | Java AWT — grises / color con dithering / clarito |
| Observabilidad | Prometheus + Grafana + Loki + Promtail |
| Infraestructura | Docker Compose + Nginx (punto de entrada unico) |
| Pruebas de carga | Modulo Java standalone (STOMP/SockJS) en `load-test/` |

---

## Microservicios

| Servicio | Puerto | Responsabilidad |
|---|---|---|
| nginx | 80 | Punto de entrada unico: sirve el frontend y enruta API/WebSockets |
| api-gateway | 8080 | Enrutamiento, validacion JWT, rate limiting |
| auth-service | 8081 | Registro, login, JWT, avatares, verificacion de email |
| canvas-service | 8082 | Lienzo colaborativo, WebSocket STOMP, invitaciones, historial |
| chat-service | 8083 | Mensajes directos, notificaciones de invitacion |
| signaling-service | 8084 | Senalizacion WebRTC para voz |
| ai-service | 8085 | Plantillas desde imagen (grises / color / clarito) |
| observability-service | — | Prometheus, Grafana, Loki (stack aparte, ver su propio docker-compose) |

---

## Como correr el proyecto

### Opcion 1 — Cada servicio desde el IDE (desarrollo)

Levantar solo infraestructura:

```bash
docker compose up postgres redis -d
```

Y correr cada microservicio con su wrapper de Maven, por ejemplo:

```bash
cd auth-service
./mvnw spring-boot:run
```

### Opcion 2 — Stack completo con Docker Compose

```bash
docker compose up --build
```

La aplicacion completa queda disponible en `http://localhost` (puerto 80,
via nginx). Todo el trafico (frontend, API REST, y los 3 WebSockets de
canvas/chat/voz) pasa por ese unico punto de entrada.

**Primera vez / cambio de esquema de base de datos:** si el volumen de
Postgres ya existia de una corrida anterior, el script de
`postgres-init/init-databases.sql` no se vuelve a ejecutar (Postgres solo
corre los scripts de init en un volumen vacio). Para forzar una base limpia:

```bash
docker compose down -v
docker compose up --build
```

### Variables de entorno para despliegue real

Por defecto todo corre apuntando a `http://localhost`. Para desplegar en un
dominio o IP publica, crear un archivo `.env` en la raiz (ya esta en
`.gitignore`, nunca se sube) con:

```
PUBLIC_ORIGIN=http://tu-dominio-o-ip
JWT_SECRET=un-secreto-largo-y-real-de-produccion
MAIL_USERNAME=tu-correo@gmail.com
MAIL_APP_PASSWORD=tu-app-password-de-gmail
```

---

## Prueba de carga

`load-test/` es una herramienta Java standalone que simula N usuarios
concurrentes pintando en tiempo real vía STOMP/SockJS, para medir latencia y
tasa de confirmacion del WebSocket de pintado. Ver `load-test/README.md`.

---

## Estructura del monorepo

```
pixel-platform-arsw/
├── api-gateway/              Spring Cloud Gateway (JWT, rate limiting)
├── auth-service/              Registro, login, JWT, avatares
├── canvas-service/            Lienzo colaborativo (WebSocket + Redis + Postgres)
├── chat-service/               Mensajes directos
├── signaling-service/         Senalizacion WebRTC
├── ai-service/                 Plantillas desde imagen
├── observability-service/     Prometheus, Grafana, Loki
├── load-test/                  Prueba de carga del WebSocket de pintado
├── frontend/                   React + Vite
├── postgres-init/               Script de creacion de las 3 bases de datos
├── nginx/nginx.conf             Punto de entrada unico (puerto 80)
├── docker-compose.yml           Orquestacion completa
└── .gitignore
```
