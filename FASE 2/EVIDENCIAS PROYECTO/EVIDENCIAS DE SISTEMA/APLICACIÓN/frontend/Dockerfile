FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Con "npm ci" falla: el package-lock.json se generó en Windows y no trae los binarios de Linux.
RUN npm install
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
# nginx reemplaza ${BACKEND_URL} al arrancar (carpeta templates) y deja el resultado en conf.d/default.conf.
ENV BACKEND_URL=http://backend:8080
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
