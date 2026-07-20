import http from 'k6/http';
import { check, group } from 'k6';
import encoding from 'k6/encoding';
import crypto from 'k6/crypto';

// -- Configuracion (parametrizable por variables de entorno) ----------------
const AUTH_BASE_URL = __ENV.AUTH_BASE_URL || 'http://localhost:8081';
const CANVAS_BASE_URL = __ENV.CANVAS_BASE_URL || 'http://localhost:8082';
const CHAT_BASE_URL = __ENV.CHAT_BASE_URL || 'http://localhost:8083';
// Cambiar solo si tu .env usa un jwt.secret distinto al valor por defecto
const JWT_SECRET = __ENV.JWT_SECRET || 'dev-secret-key-pixelplatform-auth-service-arsw-2026';
// En true, corre tambien el test de registro duplicado (deja un intento
// real de envio de correo via SMTP). Por defecto queda apagado.
const INCLUDE_DUPLICATE_EMAIL_TEST = (__ENV.INCLUDE_DUPLICATE_EMAIL_TEST || 'false') === 'true';

export const options = {
  vus: 1,
  iterations: 1,
};

// -- Utilidades para forjar/alterar JWT sin depender de un login real -------
function base64UrlJson(obj) {
  return encoding.b64encode(JSON.stringify(obj), 'rawurl');
}

function forgeToken(userId, { expired = false } = {}) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const nowSeconds = Math.floor(Date.now() / 1000);
  const payload = {
    sub: `${userId}@test.com`,
    username: userId,
    userId: userId,
    iat: nowSeconds,
    exp: expired ? nowSeconds - 3600 : nowSeconds + 3600,
  };
  const unsigned = `${base64UrlJson(header)}.${base64UrlJson(payload)}`;
  const signature = crypto.hmac('sha256', JWT_SECRET, unsigned, 'base64rawurl');
  return `${unsigned}.${signature}`;
}

// Corrompe el ultimo caracter de la firma sin necesidad de conocer el
// secreto -- simula a un atacante que intercepto/adivino un token y lo
// modifico, o un token simplemente corrupto en transito.
function tamperToken(token) {
  const parts = token.split('.');
  const sig = parts[2];
  const lastChar = sig.charAt(sig.length - 1);
  parts[2] = sig.slice(0, -1) + (lastChar === 'A' ? 'B' : 'A');
  return parts.join('.');
}

function authHeader(token) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

export default function () {
  const validToken = forgeToken('k6-security-user');
  const tamperedToken = tamperToken(validToken);
  const expiredToken = forgeToken('k6-security-user', { expired: true });

  group('auth-service: login', () => {
    const res = http.post(`${AUTH_BASE_URL}/api/auth/login`, JSON.stringify({
      email: 'usuario-que-no-existe-k6@example.com',
      password: 'ClaveIncorrecta1',
    }), { headers: { 'Content-Type': 'application/json' } });
    check(res, {
      'login con credenciales inexistentes -> 401': (r) => r.status === 401,
      'login no revela si fue el email o la contrasena': (r) =>
        JSON.parse(r.body).error === 'Credenciales invalidas',
    });
  });

  group('auth-service: registro invalido (sin efectos secundarios)', () => {
    const passwordDebil = http.post(`${AUTH_BASE_URL}/api/auth/register`, JSON.stringify({
      firstName: 'K6', lastName: 'Test', email: 'k6-debil@example.com',
      password: 'debil', confirmPassword: 'debil',
    }), { headers: { 'Content-Type': 'application/json' } });
    check(passwordDebil, {
      'registro con contrasena debil -> 400': (r) => r.status === 400,
    });

    const noCoincide = http.post(`${AUTH_BASE_URL}/api/auth/register`, JSON.stringify({
      firstName: 'K6', lastName: 'Test', email: 'k6-nocoincide@example.com',
      password: 'Password1', confirmPassword: 'Password2Distinta',
    }), { headers: { 'Content-Type': 'application/json' } });
    check(noCoincide, {
      'registro con confirmPassword distinto -> 400': (r) => r.status === 400,
    });

    if (INCLUDE_DUPLICATE_EMAIL_TEST) {
      const email = `k6-duplicado-${Date.now()}@example.com`;
      const payload = JSON.stringify({
        firstName: 'K6', lastName: 'Test', email,
        password: 'Password1', confirmPassword: 'Password1',
      });
      http.post(`${AUTH_BASE_URL}/api/auth/register`, payload,
        { headers: { 'Content-Type': 'application/json' } });
      const duplicado = http.post(`${AUTH_BASE_URL}/api/auth/register`, payload,
        { headers: { 'Content-Type': 'application/json' } });
      check(duplicado, {
        'registro con correo duplicado -> 400': (r) => r.status === 400,
      });
    }
  });

  group('auth-service: endpoints protegidos por token manual', () => {
    const sinToken = http.get(`${AUTH_BASE_URL}/api/auth/users`);
    check(sinToken, { 'GET /users sin token -> 401': (r) => r.status === 401 });

    const malformado = http.get(`${AUTH_BASE_URL}/api/auth/users`,
      { headers: { Authorization: 'Bearer sopa-de-letras-no-es-un-jwt' } });
    check(malformado, { 'GET /users con token malformado -> 401': (r) => r.status === 401 });

    const alterado = http.get(`${AUTH_BASE_URL}/api/auth/users`, authHeader(tamperedToken));
    check(alterado, { 'GET /users con firma alterada -> 401': (r) => r.status === 401 });

    const expirado = http.get(`${AUTH_BASE_URL}/api/auth/users`, authHeader(expiredToken));
    check(expirado, { 'GET /users con token expirado -> 401': (r) => r.status === 401 });

    const valido = http.get(`${AUTH_BASE_URL}/api/auth/users`, authHeader(validToken));
    check(valido, { 'GET /users con token valido -> 200 (control positivo)': (r) => r.status === 200 });

    const idInyeccion = http.get(
      `${AUTH_BASE_URL}/api/auth/users/${encodeURIComponent("' OR '1'='1")}`,
      authHeader(validToken));
    check(idInyeccion, {
      'GET /users/{id} con id no numerico no revienta con 500': (r) => r.status !== 500,
      'GET /users/{id} con id no numerico -> 404': (r) => r.status === 404,
    });
  });

  group('canvas-service: filtro JWT', () => {
    const body = JSON.stringify({ name: 'k6', width: 64, height: 64, ownerId: 'k6-security-user' });
    const sinToken = http.post(`${CANVAS_BASE_URL}/api/canvases`, body,
      { headers: { 'Content-Type': 'application/json' } });
    check(sinToken, { 'POST /canvases sin token -> 401': (r) => r.status === 401 });

    const malformado = http.get(`${CANVAS_BASE_URL}/api/canvases/general`,
      { headers: { Authorization: 'Bearer sopa-de-letras-no-es-un-jwt' } });
    check(malformado, { 'GET /canvases/general con token malformado -> 401': (r) => r.status === 401 });

    const valido = http.get(`${CANVAS_BASE_URL}/api/canvases/general`, authHeader(validToken));
    check(valido, { 'GET /canvases/general con token valido -> 200 (control positivo)': (r) => r.status === 200 });
  });

  group('chat-service: filtro JWT', () => {
    const body = JSON.stringify({ toUserId: 'otro-usuario', content: 'hola desde k6' });
    const sinToken = http.post(`${CHAT_BASE_URL}/api/messages`, body,
      { headers: { 'Content-Type': 'application/json' } });
    check(sinToken, { 'POST /messages sin token -> 401': (r) => r.status === 401 });

    const malformado = http.post(`${CHAT_BASE_URL}/api/messages`, body,
      { headers: { 'Content-Type': 'application/json', Authorization: 'Bearer sopa-de-letras-no-es-un-jwt' } });
    check(malformado, { 'POST /messages con token malformado -> 401': (r) => r.status === 401 });

    const valido = http.post(`${CHAT_BASE_URL}/api/messages`, body, authHeader(validToken));
    check(valido, { 'POST /messages con token valido -> 201 (control positivo)': (r) => r.status === 201 });
  });
}
