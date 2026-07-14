# load-test — Prueba de carga del WebSocket de pintado

Simula N usuarios concurrentes pintando pixeles en tiempo real sobre el lienzo
General de PixelPlatform, vía STOMP/SockJS directo a `canvas-service`.

## Requisitos
- `canvas-service` corriendo (por defecto en `http://localhost:8082`).
- Redis corriendo (lo usa `canvas-service` para el cooldown).

## Compilar
```
cd load-test
.\mvnw.cmd clean package
```

## Ejecutar
```
java -jar target/load-test.jar [baseUrl] [canvasId] [users] [durationSeconds] [width] [height] [cooldownMillis] [jwtSecret]
```

Ejemplo con los valores por defecto (20 usuarios, 30 segundos, lienzo General):
```
java -jar target/load-test.jar
```

Ejemplo con 100 usuarios durante 60 segundos:
```
java -jar target/load-test.jar http://localhost:8082 00000000-0000-0000-0000-000000000001 100 60
```

## Cómo leer el reporte
- **Pixeles enviados vs confirmaciones recibidas**: si hay una brecha grande,
  el servidor está descartando o perdiendo mensajes bajo carga (o el
  broadcast no llegó a tiempo antes de que terminara la ventana de gracia).
- **Latencia p95/p99**: son las que importan para la experiencia real — un
  promedio bajo puede esconder que 1 de cada 20 usuarios espera mucho más.
- **Errores de conexión/STOMP**: conexiones rechazadas o caídas — si suben
  cuando aumentás `users`, es una señal de que se está agotando algún
  recurso (threads, conexiones a Redis, etc.) en `canvas-service`.

Para el documento de Quality Attributes: correr esto con una progresión de
usuarios (ej. 10, 50, 100, 200) y anotar cómo evolucionan p95/p99 y la tasa de
error — eso es justamente la evidencia empírica que valida (o refuta) los
atributos de calidad ya definidos en el documento.

## Verificación esperada
1. `cd load-test && .\mvnw.cmd clean package` debe compilar y generar
   `target/load-test.jar`.
2. Con `canvas-service` y Redis corriendo, `java -jar target/load-test.jar`
   (valores por defecto) debe conectar 20 usuarios e imprimir un reporte con
   latencias y 0 (o casi 0) errores.
3. Si `mvn`/`javac` marca algún error de compilación en las clases del cliente
   STOMP (`WebSocketStompClient`, `SockJsClient`, etc.), avisame el mensaje
   exacto del error — es la parte del prompt que no pude verificar
   compilando yo mismo, así que puede necesitar un ajuste.
