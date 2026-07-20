# security-test - Pruebas de seguridad con k6

Bateria de pruebas de "caja negra" contra las APIs REST de `auth-service`,
`canvas-service` y `chat-service`, corriendo contra la pila real (no contra
un test double de JVM como los tests de Testcontainers).

## Division de responsabilidades con los tests de Testcontainers

- **Tests de integracion (Testcontainers, Java):** validan la logica de
  *autorizacion* (el dueno puede borrar su lienzo, un token que no coincide
  con el parametro da 403, etc.) usando tokens firmados correctamente.
- **Este script de k6:** valida la capa de *autenticacion* en si: que pasa si
  no hay token, si esta corrupto, si expiro o si alguien lo altero. Lo hace
  contra el sistema realmente corriendo.

## Requisitos

- k6 instalado. En Windows:
  `winget install k6 --source winget`
- La pila corriendo con `docker compose up --build`, o una instancia remota
  configurada con las variables de entorno de abajo.

## Ejecutar

Contra `docker compose` local (valores por defecto: 8081/8082/8083):

```bash
k6 run security-test/security-test.js
```

Contra URLs personalizadas:

```powershell
k6 run `
  -e AUTH_BASE_URL=http://localhost:8081 `
  -e CANVAS_BASE_URL=http://localhost:8082 `
  -e CHAT_BASE_URL=http://localhost:8083 `
  security-test/security-test.js
```

Si el entorno usa otro `jwt.secret`:

```bash
k6 run -e JWT_SECRET=mi-secreto security-test/security-test.js
```

## Test opcional con efecto secundario

Por defecto no se ejecuta el caso de correo duplicado porque puede dejar un
intento real de envio de correo via SMTP. Para activarlo explicitamente:

```bash
k6 run -e INCLUDE_DUPLICATE_EMAIL_TEST=true security-test/security-test.js
```

## Alcance

El script no toca archivos de `src/main` ni forma parte del build de Maven o
Node. Esta carpeta es independiente y sirve para validar seguridad basica de
autenticacion contra servicios ya levantados.
