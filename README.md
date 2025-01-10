# Meeting Feedback - Sistema de Transcripción e Informes

Sistema para transcribir reuniones y generar informes estructurados automáticamente.

## 🚀 Inicio Rápido

### Requisitos

- Python 3.8 o superior
- `uv` (gestor de paquetes rápido) - Opcional pero recomendado
- ~2GB de espacio libre (para modelos)

### Instalación y Ejecución

1. **Ejecutar el script de inicio:**
   ```bash
   start-server-uv.bat
   ```

2. **Primera ejecución:**
   - Se descargará Whisper (~500MB) para transcripción
   - Los informes se generan con la **API gratuita de Google Gemini** (configura `GEMINI_API_KEY` en `.env`) o con análisis por reglas si no la configuras
   - Cada informe se genera también en **PDF** y se puede descargar desde `/files/report/{filename}`

3. **El servidor estará disponible en:**
   - http://localhost:3000

## 📋 Endpoints

- `GET /health` - Verificar estado del servidor
- `POST /transcribe` - Transcribir audio (Whisper)
- `POST /feedback` - Generar informe (API Gemini o reglas) y **PDF**
- `POST /process` - Transcribir + generar informe + **PDF** en una llamada
- `GET /files/list` - Listar archivos guardados
- `GET /files/audio/{filename}` - Descargar archivo de audio
- `GET /files/transcription/{filename}` - Descargar transcripción
- `GET /files/report/{filename}` - Descargar **informe en PDF**

## 🔧 Configuración

### Variables de entorno (.env)

```env
PORT=3000
# Recomendado: API gratuita de Google Gemini para mejores informes
# https://aistudio.google.com/apikey
GEMINI_API_KEY=tu_clave_aqui
```

## 📱 App Android

La app Android está en la carpeta `android/`. Configura la URL del servidor en `MainActivity.kt`.

### Si Gradle no arranca (falta gradle-wrapper.jar)

En la carpeta `android/` ejecuta una vez en PowerShell:

```powershell
.\restaurar-gradle-wrapper.ps1
```

O, si tienes Gradle instalado globalmente: `gradle wrapper --gradle-version=8.6`. Luego usa `.\gradlew.bat` para compilar.

```kotlin
private val backendBaseUrl = "http://TU_IP:3000"
```

## 📝 Funcionalidad

1. **Transcripción**: Whisper (local) transcribe el audio a texto.
2. **Informes**: Se envía el texto a la **API gratuita de Google Gemini**, que extrae:
   - Resumen, puntos clave, decisiones, acciones, temas importantes
   - Participantes, siguientes pasos, aspectos positivos y áreas de mejora
3. **PDF**: Con esa información se genera un **informe en PDF** que se guarda y puede descargarse.

Si no configuras `GEMINI_API_KEY`, se usa un análisis por reglas (menos preciso pero sin API).

Los archivos de audio, transcripciones e informes PDF se guardan en `backend/uploads/`.

## ⚡ Tecnologías

- **Backend**: Python + FastAPI
- **Transcripción**: faster-whisper (Whisper local)
- **Informes**: API Google Gemini (gratuita) o análisis por reglas
- **PDF**: ReportLab
- **App Android**: Kotlin
