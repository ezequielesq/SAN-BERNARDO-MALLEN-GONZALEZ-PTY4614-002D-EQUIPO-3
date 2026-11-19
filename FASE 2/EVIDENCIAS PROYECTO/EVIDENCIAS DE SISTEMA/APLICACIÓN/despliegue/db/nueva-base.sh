#!/bin/sh
# Crea una base y un usuario para otra aplicación en el Postgres compartido (contenedor pg-central).
# Uso (en la Raspberry):   sh nueva-base.sh <nombre_base> <usuario>
# La contraseña se pide por teclado: no queda en el historial ni en el repositorio.
set -e

[ $# -eq 2 ] || { echo "Uso: sh nueva-base.sh <nombre_base> <usuario>"; exit 1; }

printf "Contraseña para el usuario %s: " "$2"
stty -echo 2>/dev/null || true; read -r DBPASS; stty echo 2>/dev/null || true; echo

docker exec -i -e DBPASS="$DBPASS" pg-central sh -c \
  'psql -v ON_ERROR_STOP=1 -U postgres -d postgres -v dbname="$1" -v dbuser="$2" -v dbpass="$DBPASS"' sh "$1" "$2" <<'EOSQL'
CREATE ROLE :"dbuser" LOGIN PASSWORD :'dbpass';
CREATE DATABASE :"dbname" OWNER :"dbuser";
REVOKE ALL ON DATABASE :"dbname" FROM PUBLIC;
EOSQL

echo "Listo: base $1 con usuario $2. Conéctate a pg-central:5432 desde la red red_bodega."
