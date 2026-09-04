# FD004 E2E — Finnhub boundary stub. Tiny, dependency-free Node HTTP server.
FROM node:20-alpine
WORKDIR /app
COPY server.js ./
EXPOSE 8080
USER node
CMD ["node", "server.js"]
