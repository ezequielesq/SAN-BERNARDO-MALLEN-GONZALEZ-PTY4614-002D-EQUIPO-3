# La compilación corre en la arquitectura de quien construye (los archivos de Angular no dependen del procesador);
# solo la imagen final se arma para la plataforma de destino (p. ej. linux/arm64 para la Raspberry).
FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Con "npm ci" falla: el package-lock.json se generó en Windows y no trae los binarios de Linux.
# La caché de npm sobrevive entre intentos: si la descarga se corta, el siguiente intento continúa donde quedó.
RUN --mount=type=cache,target=/root/.npm npm install --fetch-retries=5 --fetch-retry-mintimeout=20000
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
# nginx reemplaza ${BACKEND_URL} al arrancar (carpeta templates) y deja el resultado en conf.d/default.conf.
ENV BACKEND_URL=http://backend:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
