# La compilación corre en la arquitectura de quien construye (el .jar no depende del procesador);
# solo la imagen final se arma para la plataforma de destino (p. ej. linux/arm64 para la Raspberry).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./mvnw
# Las pruebas necesitan una base PostgreSQL real, por eso no corren dentro de la imagen.
# La caché de ~/.m2 sobrevive entre intentos: si la descarga se corta (Wi-Fi inestable), el siguiente intento continúa donde quedó.
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests -Daether.connector.http.retryHandler.count=10

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
