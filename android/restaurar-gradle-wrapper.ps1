# Script para restaurar gradle-wrapper.jar (necesario para que Gradle funcione)
# Ejecutar desde PowerShell en la carpeta android: .\restaurar-gradle-wrapper.ps1

$ErrorActionPreference = "Stop"
$wrapperDir = Join-Path $PSScriptRoot "gradle\wrapper"
$jarPath = Join-Path $wrapperDir "gradle-wrapper.jar"
$zipPath = Join-Path $env:TEMP "gradle-8.6-bin.zip"
$extractPath = Join-Path $env:TEMP "gradle-8.6-extract"

Write-Host "Descargando Gradle 8.6..." -ForegroundColor Cyan
try {
    Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.6-bin.zip" -OutFile $zipPath -UseBasicParsing
} catch {
    Write-Host "Error de descarga. Prueba con: gradle wrapper --gradle-version=8.6 (si tienes Gradle instalado)" -ForegroundColor Yellow
    exit 1
}

Write-Host "Extrayendo gradle-wrapper.jar..." -ForegroundColor Cyan
if (Test-Path $extractPath) { Remove-Item $extractPath -Recurse -Force }
Expand-Archive -Path $zipPath -DestinationPath $extractPath -Force
$srcJar = Join-Path $extractPath "gradle-8.6\lib\gradle-wrapper-8.6.jar"
Copy-Item $srcJar -Destination $jarPath -Force

Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
Remove-Item $extractPath -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Listo. gradle-wrapper.jar instalado en $wrapperDir" -ForegroundColor Green
Write-Host "Puedes ejecutar: .\gradlew.bat tasks" -ForegroundColor Green
