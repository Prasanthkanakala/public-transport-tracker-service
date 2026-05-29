# ── Build stage ───────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

# Dependency layer cache
COPY package.json ./
RUN npm install --silent

# Build the React app
COPY public/ public/
COPY src/ src/
ENV REACT_APP_API_URL=/api/v1/transport
RUN npm run build

# ── Nginx serve stage ──────────────────────────────────────────────────────────
FROM nginx:1.25-alpine AS runtime

LABEL maintainer="transport-tracker@example.com"
LABEL description="Public Transport Tracker – React frontend"

# Copy built assets
COPY --from=builder /app/build /usr/share/nginx/html

# Custom nginx config (handles React Router + API proxy)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/index.html || exit 1