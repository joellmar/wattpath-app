# ETAPA 1: Compilar Angular en modo producción
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# --legacy-peer-deps: resuelve conflictos de versiones cruzadas de PrimeNG 21
RUN npm ci --legacy-peer-deps
COPY . .
# Los dobles guiones pasan el flag a ng build, no a npm
RUN npm run build -- --configuration=production

# ETAPA 2: Servidor estático ligero con enrutamiento SPA
FROM nginx:alpine
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
# Sobrescribe el default.conf de la imagen para activar try_files (SPA routing)
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
