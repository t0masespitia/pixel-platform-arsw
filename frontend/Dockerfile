# ================================================================
# PixelPlatform — frontend/Dockerfile
# Build de React + Vite, servido como estatico via nginx
# ================================================================

FROM node:20-alpine AS builder
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Las URLs quedan vacias a proposito: en produccion todo vive detras de un
# solo origen (nginx en el puerto 80), asi que las peticiones del frontend
# quedan relativas (/api/..., /ws-canvas) y el navegador las resuelve contra
# el mismo dominio, sin hardcodear ningun host.
ARG VITE_API_GATEWAY_URL=""
ARG VITE_CANVAS_SERVICE_URL=""
ARG VITE_CHAT_SERVICE_URL=""
ARG VITE_SIGNALING_SERVICE_URL=""
ENV VITE_API_GATEWAY_URL=$VITE_API_GATEWAY_URL
ENV VITE_CANVAS_SERVICE_URL=$VITE_CANVAS_SERVICE_URL
ENV VITE_CHAT_SERVICE_URL=$VITE_CHAT_SERVICE_URL
ENV VITE_SIGNALING_SERVICE_URL=$VITE_SIGNALING_SERVICE_URL

RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
