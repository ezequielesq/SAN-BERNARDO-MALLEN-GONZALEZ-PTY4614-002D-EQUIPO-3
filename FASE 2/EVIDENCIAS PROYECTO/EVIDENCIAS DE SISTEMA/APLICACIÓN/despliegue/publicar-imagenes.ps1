# Construye las imagenes de Smart RDP (ARM64) en este PC y las carga en la Raspberry por SSH.
#
# Uso (PowerShell, desde cualquier carpeta):
#   .\publicar-imagenes.ps1                                  # construye, envia y carga
#   .\publicar-imagenes.ps1 -Destino otro@192.168.18.99      # otro usuario o direccion
#   .\publicar-imagenes.ps1 -SoloConstruir                   # solo construye, no envia
#   .\publicar-imagenes.ps1 -SinPausa                        # no espera Enter al final
#
# Requisitos en este PC: Docker Desktop en marcha y el cliente OpenSSH (viene con Windows).
# SSH pedira la contrasena de la Raspberry dos veces (copia y carga), o ninguna si configuraste una llave.
# El script no guarda ni imprime credenciales.
#
# Despues, en Portainer: stack "smartrdp" > Pull and redeploy (o Update the stack).

param(
    [string]$Destino = "ezequiel@192.168.18.82",
    [switch]$SoloConstruir,
    [switch]$SinPausa
)

$ErrorActionPreference = "Stop"

# La carpeta APLICACION es la que contiene backend/, frontend/ y despliegue/.
$raiz = Split-Path $PSScriptRoot -Parent
$imagenes = @("smartrdp-backend:latest", "smartrdp-frontend:latest")
$tar = Join-Path $env:TEMP "smartrdp-imagenes-arm64.tar"
$nombreRemoto = "smartrdp-imagenes-arm64.tar"
$exito = $false

function Paso($texto) { Write-Host ""; Write-Host "==> $texto" -ForegroundColor Cyan }

function Verificar($codigo, $mensaje) {
    if ($codigo -ne 0) { throw $mensaje }
}

try {
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
        $exito = $true
    }
    else {
        Paso "Empaquetando las imagenes"
        docker save -o $tar $imagenes
        Verificar $LASTEXITCODE "Fallo el empaquetado de las imagenes."
        $mb = [math]::Round((Get-Item $tar).Length / 1MB)
        Write-Host "Archivo de $mb MB."

        Paso "Copiando a $Destino (veras una barra de progreso; SSH puede pedir la contrasena)"
        scp $tar "${Destino}:$nombreRemoto"
        Verificar $LASTEXITCODE "Fallo la copia a la Raspberry."

        Paso "Cargando las imagenes en Docker de la Raspberry (SSH puede pedir la contrasena otra vez)"
        ssh $Destino "docker load -i $nombreRemoto && rm $nombreRemoto"
        Verificar $LASTEXITCODE "Fallo la carga en la Raspberry."

        Paso "Comprobando que quedaron cargadas"
        ssh $Destino "docker image ls --format '{{.Repository}}:{{.Tag}}  {{.Size}}' | grep smartrdp"
        Verificar $LASTEXITCODE "No aparecen las imagenes smartrdp en la Raspberry."

        Write-Host ""
        Write-Host "LISTO: las imagenes estan cargadas en la Raspberry." -ForegroundColor Green
        Write-Host "Ahora, en Portainer: stack smartrdp > Pull and redeploy (o Update the stack)."
        $exito = $true
    }
}
catch {
    Write-Host ""
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    Remove-Item $tar -ErrorAction SilentlyContinue
    if (-not $SinPausa) {
        Write-Host ""
        Read-Host "Pulsa Enter para cerrar" | Out-Null
    }
}

if (-not $exito) { exit 1 }
