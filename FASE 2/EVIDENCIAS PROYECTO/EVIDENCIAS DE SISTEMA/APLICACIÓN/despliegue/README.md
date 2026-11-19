# Despliegue en la Raspberry con Portainer

Smart RDP corre en la Raspberry como tres stacks de Portainer. El tráfico llega por un túnel de Cloudflare, así que no se abre ningún puerto del router.

```
Internet ─► Cloudflare Tunnel ─► smartrdp-frontend (nginx) ─► /api ─► smartrdp-backend ─► pg-central (PostgreSQL 18)
                                      └─ sirve la app Angular                              └─ pg-backup (volcado diario)
```

## Nombres definidos

| Qué | Nombre |
|---|---|
| Stacks de Portainer | `db`, `smartrdp`, `edge` (opcional) |
| Redes de Docker | `red_bodega` (aplicaciones ↔ base de datos), `red_web` (túnel ↔ frontends) |
| Contenedores | `pg-central`, `pg-backup`, `smartrdp-backend`, `smartrdp-frontend`, `cloudflared` |
| Volumen de la base | `pgdata` |
| Carpeta de respaldos (Raspberry) | `/srv/backups/postgres` |
| Base de datos y usuario de Smart RDP | `smartrdp` / `smartrdp_app` |
| Superusuario de PostgreSQL | `postgres` (solo administración; ninguna aplicación lo usa) |
| Puerto local del frontend | `127.0.0.1:8081` (no accesible desde la red local) |

## Credenciales y secretos

Ninguna credencial va en el repositorio (es público). Los valores reales se escriben en Portainer, en **Environment variables** de cada stack. Cada archivo `.env.example` lista los nombres que hay que definir.

Genera los secretos en la Raspberry, uno por cada uno:

```bash
openssl rand -base64 24   # PG_SUPERUSER_PASSWORD y SMARTRDP_DB_PASSWORD (dos distintas)
openssl rand -base64 48   # SMARTRDP_JWT_SECRET
```

| Variable | Stack | Quién la define |
|---|---|---|
| `PG_SUPERUSER_PASSWORD` | `db` | Generada con `openssl` |
| `SMARTRDP_DB_PASSWORD` | `smartrdp` (y se usa al crear la base) | Generada con `openssl` |
| `SMARTRDP_JWT_SECRET` | `smartrdp` | Generado con `openssl`; nunca uno que haya estado en el repositorio |
| `SMARTRDP_ADMIN_EMAIL` / `SMARTRDP_ADMIN_PASSWORD` | `smartrdp` | La persona que administra la aplicación |
| `CLOUDFLARE_TUNNEL_TOKEN` | `edge` | Panel de Cloudflare (solo si `cloudflared` corre en Docker) |

Guarda los valores en un gestor de contraseñas.

## Pasos

### 0. Requisitos
Docker y Portainer instalados en la Raspberry, con el usuario administrador de Portainer ya creado. Portainer solo debe ser accesible desde la red local o detrás de Cloudflare Access: da control total de Docker.

### 1. Redes y carpeta de respaldos (una sola vez)

```bash
docker network create red_bodega
docker network create red_web
sudo mkdir -p /srv/backups/postgres
```

### 2. Stack `db`
Portainer > **Stacks > Add stack > Repository**:
- Repository URL: la de este repositorio en GitHub.
- Repository reference: `refs/heads/main`.
- Compose path: `FASE 2/EVIDENCIAS PROYECTO/EVIDENCIAS DE SISTEMA/APLICACIÓN/despliegue/db/docker-compose.yml`
- Environment variables: `PG_SUPERUSER_PASSWORD` y `BACKUP_DIR`.

### 3. Crear la base de Smart RDP
En la Raspberry, desde una copia del repositorio (o pegando el contenido de `db/nueva-base.sh`):

```bash
sh nueva-base.sh smartrdp smartrdp_app
```

Pide la contraseña por teclado: escribe la de `SMARTRDP_DB_PASSWORD`. Sirve igual para cada aplicación nueva (`sh nueva-base.sh <base> <usuario>`).

### 4. Stack `smartrdp`
Igual que el anterior, con Compose path `.../despliegue/smartrdp/docker-compose.yml` y las variables de `smartrdp/.env.example`. Portainer construye las imágenes desde el código; en la Raspberry la primera construcción tarda varios minutos.

El backend crea las tablas solo (Flyway) y, si definiste `SMARTRDP_ADMIN_EMAIL` y `SMARTRDP_ADMIN_PASSWORD`, el administrador inicial.

### 5. Ruta del túnel de Cloudflare
En Cloudflare: **Zero Trust > Networks > Tunnels > (tu túnel) > Public hostname > Add**.
- Hostname: el dominio elegido para la aplicación.
- Service: `HTTP` y la URL según dónde corre `cloudflared`:
  - Como servicio en la Raspberry (nuestro caso): `http://localhost:8081`
  - Dentro de Docker (stack `edge`, no se usa en esta instalación): `http://smartrdp-frontend:80`

Solo se expone el frontend. El backend y la base de datos no se publican: nginx reenvía `/api` por la red interna.

### 6. Comprobar
Abre el dominio, inicia sesión con el administrador y crea un producto. Si falla, revisa los registros de `smartrdp-backend` en Portainer (Containers > Logs).

## Respaldo

`pg-backup` guarda un volcado de cada base en `/srv/backups/postgres` al arrancar y luego cada 24 horas, y borra los de más de 14 días. Para restaurar una base vacía:

```bash
docker exec -i pg-central pg_restore -U postgres -d smartrdp --clean --if-exists < /srv/backups/postgres/smartrdp_AAAA-MM-DD_HHMM.dump
```

Copia esa carpeta también a otro equipo o disco: un respaldo en la misma tarjeta de la Raspberry no protege si la tarjeta falla.

## Actualizar la aplicación
En Portainer, abre el stack `smartrdp` > **Pull and redeploy** (con la opción de reconstruir las imágenes).

## Seguridad
- La vista pública del empleado pide nombre y contraseña sin límite de intentos: protege el dominio con **Cloudflare Access** o limita quién puede entrar.
- Cambia las contraseñas si alguna se compartió por un medio inseguro (chat, correo).
- Mantén la Raspberry actualizada y accede por SSH con llaves.
