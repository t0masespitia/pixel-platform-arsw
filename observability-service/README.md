# observability-service

Stack de observabilidad para PixelPlatform: métricas con Prometheus + Grafana, y logs centralizados con Loki + Promtail.

## Prerequisitos

Los 6 microservicios deben estar corriendo en sus puertos normales antes de levantar este stack:

| Servicio          | Puerto |
|-------------------|--------|
| api-gateway       | 8080   |
| auth-service      | 8081   |
| canvas-service    | 8082   |
| chat-service      | 8083   |
| signaling-service | 8084   |
| ai-service        | 8085   |

## Levantar el stack

```bash
cd observability-service
docker-compose up -d
```

## URLs de acceso

| Herramienta | URL                    | Credenciales  |
|-------------|------------------------|---------------|
| Prometheus  | http://localhost:9090  | —             |
| Grafana     | http://localhost:3000  | admin / admin |
| Loki        | http://localhost:3100  | —             |

El dashboard **PixelPlatform Overview** está provisionado automáticamente en Grafana bajo la carpeta **PixelPlatform**.

## Qué incluye

- **Prometheus** scrape cada 10 s el endpoint `/actuator/prometheus` de los 6 servicios.
- **Grafana** con dos datasources provisionados (Prometheus y Loki) y un dashboard listo con:
  - Estado UP/DOWN de los 6 servicios
  - Memoria JVM usada (heap) por servicio
  - Requests HTTP por segundo por servicio
  - KPIs de negocio: usuarios registrados, lienzos creados, mensajes enviados por canal, plantillas IA generadas
- **Loki + Promtail** recolectan los archivos `logs/*.log` que genera cada servicio y los indexan por label `service`.

## Detener el stack

```bash
docker-compose down
```

Para eliminar también los volúmenes de datos:

```bash
docker-compose down -v
```
