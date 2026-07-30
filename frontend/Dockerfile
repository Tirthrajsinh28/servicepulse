FROM node:24.15.0-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY index.html tsconfig*.json vite.config.ts eslint.config.js ./
COPY src/ src/
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.29.4-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/ /usr/share/nginx/html/

USER 101

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
