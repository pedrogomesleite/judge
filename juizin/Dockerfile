# Frontend do Juizin: build do Vite servido pelo nginx, que encaminha /api para a API (ver nginx.conf).

# 1. Build (type-check + vite build)
FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# 2. Servidor
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
