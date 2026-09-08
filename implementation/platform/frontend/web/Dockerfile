# syntax=docker/dockerfile:1

# --- Build stage -------------------------------------------------------------
FROM node:20.19-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Runtime stage --------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/web/browser /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=3s --start-period=15s --retries=10 \
    CMD wget -q -O - http://127.0.0.1:8080/healthz || exit 1
