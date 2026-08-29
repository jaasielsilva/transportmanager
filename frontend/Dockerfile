# Molde do kit — copiar para frontend/Dockerfile
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# O nome da pasta em dist/ segue o "project" do angular.json — ajuste se necessario.
COPY --from=build /app/dist/transportmanager/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Roda como nao-root
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx \
 && sed -i 's|/var/run/nginx.pid|/tmp/nginx.pid|' /etc/nginx/nginx.conf \
 && sed -i '/user  nginx;/d' /etc/nginx/nginx.conf
USER nginx

EXPOSE 80
