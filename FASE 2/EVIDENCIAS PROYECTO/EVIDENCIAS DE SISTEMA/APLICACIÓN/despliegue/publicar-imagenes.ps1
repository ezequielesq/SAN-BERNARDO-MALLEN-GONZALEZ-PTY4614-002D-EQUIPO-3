# Construye las imagenes de Smart RDP (ARM64) en este PC y las carga en la Raspberry por SSH.
#
# Uso (PowerShell, desde cualquier carpeta):
#   .\publicar-imagenes.ps1                                  # construye, envia y carga
#   .\publicar-imagenes.ps1 -Destino otro@192.168.18.99      # otro usuario o direccion
#   .\publicar-imagenes.ps1 -SoloConstruir                   # solo construye, no envia
#
# Requisitos en este PC: Docker Desktop en marcha y el cliente OpenSSH (viene con Windows).
# SSH pedira la contrasena de la Raspberry una sola vez (o usa tu llave si la configuraste).
# El script no guarda ni imprime credenciales.
#
# Despues, en Portainer: stack "smartrdp" > Pull and redeploy (o Update the stack).

param(
    [string]$Destino = "ezequiel@192.168.18.82",
    [switch]$SoloConstruir
)

$ErrorActionPreference = "Stop"

# La carpeta APLICACION es la que contiene backend/, frontend/ y despliegue/.
$raiz = Split-Path $PSScriptRoot -Parent
$imagenes = @("smartrdp-backend:latest", "smartrdp-frontend:latest")

function Paso($texto) { Write-Host ""; Write-Host "==> $texto" -ForegroundColor Cyan }

function Verificar($codigo, $mensaje) {
    if ($codigo -ne 0) { throw $mensaje }
}

Paso "Comprobando Docker"
docker info *> $null
Verificar $LASTEXITCODE "Docker no responde. Abre Docker Desktop y espera a que este en marcha."

Paso "Construyendo el backend (ARM64)"
docker buildx build --platform linux/arm64 --provenance=false --load -t smartrdp-backend:latest "$raiz\backend"
Verificar $LASTEXITCODE "Fallo la construccion del backend."

Paso "Construyendo el frontend (ARM64)"
docker buildx build --platform linux/arm64 --provenance=false --load -t smartrdp-frontend:latest "$raiz\frontend"
Verificar $LASTEXITCODE "Fallo la construccion del frontend."

if ($SoloConstruir) {
    Write-Host ""
    Write-Host "Imagenes construidas. No se envio nada (-SoloConstruir)." -ForegroundColor Green
    exit 0
}

Paso "Enviando las imagenes a $Destino (SSH pedira la contrasena)"
# Se usa cmd para la tuberia porque PowerShell 5 corrompe los datos binarios.
cmd /c "docker save $($imagenes -join ' ') | ssh $Destino docker load"
Verificar $LASTEXITCODE "Fallo el envio o la carga en la Raspberry."

Write-Host ""
Write-Host "Listo: las imagenes estan cargadas en la Raspberry." -ForegroundColor Green
Write-Host "Ahora, en Portainer: stack smartrdp > Pull and redeploy (o Update the stack)."
