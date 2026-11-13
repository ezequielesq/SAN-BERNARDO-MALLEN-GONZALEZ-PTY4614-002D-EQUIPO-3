FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# Con "npm ci" falla: el package-lock.json se generó en Windows y no trae los binarios de Linux.
RUN npm install
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
