# Despliegue en la Raspberry con Portainer

Smart RDP corre en la Raspberry como dos stacks de Portainer (`db` y `smartrdp`; un tercero, `edge`, es opcional y hoy no se usa). El tráfico llega por un túnel de Cloudflare, así que no se abre ningún puerto del router. En producción la aplicación está en `https://bodega.ezsuarez.org`.

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
- Repository reference: `refs/heads/main` (mientras el despliegue solo esté en `develop`, usa `refs/heads/develop`).
- Authentication: desactivada (el repositorio es público).
- Compose path: `FASE 2/EVIDENCIAS PROYECTO/EVIDENCIAS DE SISTEMA/APLICACIÓN/despliegue/db/docker-compose.yml`
- Environment variables: `PG_SUPERUSER_PASSWORD` y `BACKUP_DIR`.

### 3. Crear la base de Smart RDP
En la Raspberry, descarga el script (no hace falta clonar el repositorio; ajusta `develop` por `main` si corresponde), revísalo y ejecútalo:

```bash
curl -fsSL "https://raw.githubusercontent.com/ezequielesq/SAN-BERNARDO-MALLEN-GONZALEZ-PTY4614-002D-EQUIPO-3/develop/FASE%202/EVIDENCIAS%20PROYECTO/EVIDENCIAS%20DE%20SISTEMA/APLICACI%C3%93N/despliegue/db/nueva-base.sh" -o nueva-base.sh
cat nueva-base.sh
sh nueva-base.sh smartrdp smartrdp_app
```

Pide la contraseña por teclado: escribe la de `SMARTRDP_DB_PASSWORD`. Sirve igual para cada aplicación nueva (`sh nueva-base.sh <base> <usuario>`).

### 4. Construir las imágenes (en tu PC) y cargarlas en la Raspberry
La Raspberry tiene poca memoria (≈1 GB) y compilar Maven y Angular allí es lento y falla. Por eso las imágenes se construyen en un PC con Docker y se llevan ya hechas. La compilación corre en la arquitectura del PC y solo la imagen final se arma para ARM64, así que no hay emulación lenta.

**Con el script (recomendado).** En el PC, con Docker Desktop abierto, desde PowerShell:

```powershell
.\despliegue\publicar-imagenes.ps1
```

Construye las dos imágenes, las copia por SCP a la Raspberry (con barra de progreso) y las carga en Docker. SSH pide la contraseña dos veces, o ninguna si configuraste una llave. La Raspberry no está escrita en el repositorio: indícala con `-Destino usuario@direccion`, o guárdala una sola vez en tu PC con `setx SMARTRDP_DESTINO "usuario@direccion"` (y abre una terminal nueva). Con `-SoloConstruir` solo construye.

**A mano**, si prefieres ver cada paso. En el PC, desde la carpeta `APLICACIÓN`:

```bash
docker buildx build --platform linux/arm64 --provenance=false --load -t smartrdp-backend:latest ./backend
docker buildx build --platform linux/arm64 --provenance=false --load -t smartrdp-frontend:latest ./frontend
docker save -o smartrdp-imagenes-arm64.tar smartrdp-backend:latest smartrdp-frontend:latest
```

Copia el archivo a la Raspberry y cárgalo:

```bash
scp smartrdp-imagenes-arm64.tar USUARIO@IP_DE_LA_RASPBERRY:~/
docker load -i ~/smartrdp-imagenes-arm64.tar
```

El stack usa `pull_policy: never`, así que toma esas imágenes locales y no intenta descargarlas.

### 5. Stack `smartrdp`
Igual que el anterior, con Compose path `.../despliegue/smartrdp/docker-compose.yml` y las variables de `smartrdp/.env.example` (`APP_URL` con la dirección pública, por ejemplo `https://bodega.ezsuarez.org`). Las imágenes deben estar cargadas antes (paso 4). `SMARTRDP_DB_PASSWORD` debe ser exactamente la que escribiste en `nueva-base.sh`.

El backend crea las tablas solo (Flyway) y, si definiste `SMARTRDP_ADMIN_EMAIL` y `SMARTRDP_ADMIN_PASSWORD`, el administrador inicial. En la Raspberry el arranque tarda cerca de 90 segundos; mientras tanto nginx responde `502` en `/api`.

### 6. Ruta del túnel de Cloudflare
En Cloudflare: **Zero Trust > Networks > Connectors (Tunnels) > (tu túnel) > Published application routes > Add a published application route** (aplicación pública, no «nombre de host privado»). El dominio debe estar en la misma cuenta de Cloudflare que el túnel.
- Subdominio y dominio: por ejemplo `bodega` y `ezsuarez.org`. Ruta: vacía.
- Tipo de servicio `HTTP` y URL (el campo exige el protocolo) según dónde corre `cloudflared`:
  - Como servicio en la Raspberry (nuestro caso): `http://localhost:8081`
  - Dentro de Docker (stack `edge`, no se usa en esta instalación): `http://smartrdp-frontend:80`

Solo se expone el frontend. El backend y la base de datos no se publican: nginx reenvía `/api` por la red interna.

### 7. Comprobar
Abre el dominio, inicia sesión con el administrador y crea un producto. Si falla, revisa los registros de `smartrdp-backend` en Portainer (Containers > Logs).

## Datos de demostración

- `poblar-demo.mjs` carga datos de hoy por la API (4 proveedores de ejemplo, productos, lotes con su proveedor, solicitudes) en cualquier instancia, incluida la de la Raspberry. Los proveedores se crean por la API (si ya existen por RUT, se reutilizan) y cada compra los usa; la leche y el aceite se compran a dos proveedores con costos distintos, para ver la comparación de precios.
- `poblar-historial-demo.sql` carga 12 semanas de historial con fechas pasadas directo en una base **local** (no sirve para la Raspberry: allí no se toca la base por SQL). Se ejecuta con `psql -h localhost -U <usuario> -d smartrdp -v ON_ERROR_STOP=1 -f poblar-historial-demo.sql`; requiere un usuario con rol `BODEGUERO`, el esquema con la migración V5 (proveedores) y, si la categoría «Demo analítica» ya existe, se detiene sin tocar nada. Inserta los mismos 4 proveedores y asigna uno a cada lote de compra (solo la harina se compra a dos proveedores, y el de las semanas impares cobra un 7 % menos; los costos de los demás productos no cambian). Los empleados de ejemplo que crea no pueden iniciar sesión.

## Respaldo

`pg-backup` guarda un volcado de cada base en `/srv/backups/postgres` al arrancar y luego cada 24 horas, y borra los de más de 14 días. Para restaurar una base vacía:

```bash
docker exec -i pg-central pg_restore -U postgres -d smartrdp --clean --if-exists < /srv/backups/postgres/smartrdp_AAAA-MM-DD_HHMM.dump
```

Copia esa carpeta también a otro equipo o disco: un respaldo en la misma tarjeta de la Raspberry no protege si la tarjeta falla.

## Actualizar la aplicación

| Qué cambió | Qué hacer |
|---|---|
| Código del backend o del frontend (incluye las migraciones de la base) | Ejecutar `publicar-imagenes.ps1` y luego, en Portainer, **Pull and redeploy** del stack `smartrdp` |
| Solo configuración (el `docker-compose.yml` del stack o una variable de entorno) | Solo Portainer: **Pull and redeploy** (o **Update the stack** si cambiaste una variable) |
| Documentos, presentaciones, README | Nada |

- El script construye lo que haya en la carpeta del PC, no lo que esté en GitHub: sube el commit antes, para que el repositorio y lo que corre sean lo mismo.
- Cargar la imagen no cambia los contenedores en marcha: el redeploy los recrea con la nueva. Habrá un corte de unos 90 segundos mientras arranca el backend.
- Cada actualización deja la imagen anterior sin nombre. De vez en cuando, en la Raspberry: `docker image prune -f`.

### Versión con panel de analítica (migración V4)

La versión con panel de analítica incluye la migración **V4** (`productos_clave`). **Antes de publicar en la Raspberry, haz un respaldo de la base** (`pg_dump`); Flyway aplicará V4 solo al iniciar el backend nuevo.

```bash
docker exec pg-central pg_dump -U postgres -Fc smartrdp > ~/smartrdp_antes-de-V4.dump
```

El archivo se restaura con `pg_restore`, igual que los respaldos automáticos (ver «Respaldo»). Publicar esta versión es un paso aparte, que decide la persona responsable del despliegue.

- Desde esta versión una entrada de tipo **Compra** exige el costo unitario (también al importar entradas desde Excel). Las entradas anteriores sin costo se conservan; el historial de precios solo tiene datos desde que se exige el costo.
- **Zona horaria:** el backend corre en UTC en la Raspberry. El panel agrupa por día con esa hora, así que una entrega registrada después de las 21:00 hora de Chile (20:00 en horario de invierno) puede aparecer en el día siguiente. Es una limitación conocida; no se cambió la configuración del contenedor.

## Notas de la Raspberry
- **Memoria:** tiene ≈1 GB. Por eso no se compila allí (paso 4).
- **Wi-Fi y QUIC:** `cloudflared` por defecto usa QUIC (UDP), que se cortaba por el Wi-Fi. Se fuerza HTTP/2 con una variable en un archivo aparte, sin tocar el token:
  ```bash
  sudo mkdir -p /etc/systemd/system/cloudflared.service.d
  printf '[Service]\nEnvironment=TUNNEL_TRANSPORT_PROTOCOL=http2\n' | sudo tee /etc/systemd/system/cloudflared.service.d/protocolo.conf
  sudo systemctl daemon-reload && sudo systemctl restart cloudflared
  ```
- **No pegues la salida de `systemctl status cloudflared`:** muestra el token completo. Para ver el estado usa `journalctl -u cloudflared -n 15 --no-pager`.
- **Réplicas del túnel:** si el panel muestra dos réplicas, comprueba que haya un solo proceso (`ps -C cloudflared -o pid,ppid,etime,user,comm`). Tras un reinicio, la réplica anterior puede tardar unos minutos en desaparecer sola.
- **Contraseña de Portainer olvidada:** `docker stop portainer`, luego `docker run --rm -v portainer_data:/data portainer/helper-reset-password` (muestra una contraseña nueva) y `docker start portainer`.

## Seguridad
- La vista pública del empleado pide nombre y contraseña sin límite de intentos: protege el dominio con **Cloudflare Access** o limita quién puede entrar.
- Cambia las contraseñas si alguna se compartió por un medio inseguro (chat, correo).
- Mantén la Raspberry actualizada y accede por SSH con llaves.
- Portainer se publica solo en `9443` (HTTPS), accesible en la red local en `https://IP_DE_LA_RASPBERRY:9443`. El `9000` (HTTP sin cifrar) no se publica: al crear el contenedor no uses `-p 9000:9000`.
