# EN002 — Frontend container image for the Angular web app.
#
# Multi-stage (OD-2): the build stage compiles the Angular production bundle with Node; the runtime
# stage serves it with nginx and reverse-proxies /api to the backend. `ng serve` is never used in
# the runtime image. No Node, no source, no credentials in the final image.

# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Default configuration is `production` (angular.json).
RUN npm run build

# ---------- runtime ----------
FROM nginx:1.27-alpine AS runtime

# The `@angular/build:application` builder nests the client bundle under `browser/`.
COPY --from=build /app/dist/web/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# `nginx:alpine` ships BusyBox wget — used by the Compose health check.
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=6 \
    CMD wget -q -O - http://127.0.0.1/ >/dev/null 2>&1 || exit 1
