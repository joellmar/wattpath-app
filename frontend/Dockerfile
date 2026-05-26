# ETAPA 1: Construcción
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Compila Angular en modo producción
RUN npm run build --configuration=production

# ETAPA 2: Servidor estático
FROM nginx:alpine
# Copiamos los archivos compilados de la carpeta browser a la ruta de Nginx
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
