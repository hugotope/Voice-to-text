@echo off
chcp 65001 >nul
echo ========================================
echo   Meeting Feedback - Servidor Python (uv)
echo ========================================
echo.

cd /d "%~dp0backend"
if not exist "app.py" (
    echo [ERROR] No se encontró app.py
    pause
    exit /b 1
)

REM Verificar uv
uv --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] uv no está instalado
    echo Instala uv con: pip install uv
    echo O desde: https://github.com/astral-sh/uv
    pause
    exit /b 1
)

REM Detectar o crear venv
set VENV_PATH=venv

if exist "venv\Scripts\python.exe" (
    echo [INFO] Usando entorno virtual existente: venv
    echo.
) else if exist ".venv\Scripts\python.exe" (
    echo [INFO] Detectado .venv, usando ese entorno...
    set VENV_PATH=.venv
    echo.
) else (
    echo [INFO] Creando entorno virtual con uv...
    uv venv venv
    if %errorlevel% neq 0 (
        echo [ERROR] Error al crear entorno virtual
        pause
        exit /b 1
    )
    echo [OK] Entorno virtual creado en: venv
    echo.
)

REM Verificar que python.exe existe en el venv
if not exist "%VENV_PATH%\Scripts\python.exe" (
    echo [ERROR] No se encontró python.exe en %VENV_PATH%\Scripts\
    echo Recreando entorno virtual...
    rmdir /s /q venv 2>nul
    rmdir /s /q .venv 2>nul
    uv venv venv
    if %errorlevel% neq 0 (
        echo [ERROR] Error al crear entorno virtual
        pause
        exit /b 1
    )
    set VENV_PATH=venv
    echo [OK] Entorno virtual recreado
    echo.
)

REM Instalar dependencias con uv
echo [INFO] Instalando dependencias con uv (más rápido)...
echo Esto puede tardar varios minutos la primera vez...
echo.

uv pip install --python %VENV_PATH%\Scripts\python.exe -r requirements.txt

if %errorlevel% neq 0 (
    echo [ERROR] Error al instalar dependencias
    pause
    exit /b 1
)
echo.

REM Verificar archivo .env
if not exist ".env" (
    echo [INFO] Creando archivo .env...
    echo PORT=3000 > .env
    echo [OK] Archivo .env creado
    echo.
)

echo Iniciando servidor Python...
echo.
echo ⚠️  Primera vez: Se descargará Whisper (~500MB)
echo    - Whisper: ~500MB para transcripción
echo    - Análisis de reglas: Sin descargas, instantáneo
echo    Esto puede tardar 3-5 minutos...
echo.
echo Presiona Ctrl+C para detener
echo.

REM Activar venv y ejecutar
echo [INFO] Activando entorno virtual: %VENV_PATH%
call %VENV_PATH%\Scripts\activate.bat
python app.py

pause
