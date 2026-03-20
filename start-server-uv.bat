@echo off
setlocal
chcp 65001 >nul
echo ========================================
echo   Meeting Feedback - Servidor Python (uv)
echo ========================================
echo/

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
cd /d "%BACKEND%"

if not exist "app.py" (
    echo [ERROR] No se encontro app.py en backend
    pause
    exit /b 1
)

uv --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] uv no esta instalado
    echo Instala uv con: pip install uv
    echo O desde: https://github.com/astral-sh/uv
    pause
    exit /b 1
)

set "VENV_PATH=venv"
if exist "venv\Scripts\python.exe" (
    echo [INFO] Usando entorno virtual existente: venv
    echo/
) else if exist ".venv\Scripts\python.exe" (
    echo [INFO] Detectado .venv, usando ese entorno...
    set "VENV_PATH=.venv"
    echo/
) else (
    echo [INFO] Creando entorno virtual con uv...
    uv venv venv
    if errorlevel 1 (
        echo [ERROR] Error al crear entorno virtual
        pause
        exit /b 1
    )
    set "VENV_PATH=venv"
    echo [OK] Entorno virtual creado en: venv
    echo/
)

if not exist "%VENV_PATH%\Scripts\python.exe" (
    echo [ERROR] No se encontro python.exe en %VENV_PATH%\Scripts\
    echo Recreando entorno virtual...
    rmdir /s /q venv 2>nul
    rmdir /s /q .venv 2>nul
    uv venv venv
    if errorlevel 1 (
        echo [ERROR] Error al crear entorno virtual
        pause
        exit /b 1
    )
    set "VENV_PATH=venv"
    echo [OK] Entorno virtual recreado
    echo/
)

echo [INFO] Instalando dependencias con uv (mas rapido)...
echo Esto puede tardar varios minutos la primera vez...
echo/

uv pip install --python "%VENV_PATH%\Scripts\python.exe" -r requirements.txt
if errorlevel 1 (
    echo [ERROR] Error al instalar dependencias
    pause
    exit /b 1
)
echo/

if not exist ".env" (
    echo [INFO] Creando archivo .env...
    echo PORT=3000>.env
    echo [OK] Archivo .env creado
    echo/
)

echo Iniciando servidor Python...
echo/
echo Primera vez: Se descargara Whisper (~500MB), puede tardar 3-5 minutos.
echo Presiona Ctrl+C para detener
echo/

echo [INFO] Entorno: %VENV_PATH%
"%VENV_PATH%\Scripts\python.exe" "app.py"

pause
