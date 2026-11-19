export const environment = {
  // Compilación de producción: la API se pide al mismo origen (/api) y nginx la reenvía al backend.
  // En desarrollo (ng serve) se usa environment.development.ts, que apunta a http://localhost:8080/api.
  apiUrl: '/api',
};
