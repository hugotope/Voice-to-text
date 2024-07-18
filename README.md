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
   - Los informes se generan con análisis de reglas (sin descargas adicionales)
   - Esto puede tardar 3-5 minutos

3. **El servidor estará disponible en:**
   - http://localhost:3000

## 📋 Endpoints

- `GET /health` - Verificar estado del servidor
- `POST /transcribe` - Transcribir audio (Whisper)
- `POST /feedback` - Generar informe estructurado (GPT-2)
- `POST /process` - Transcribir y generar informe en una llamada
- `GET /files/list` - Listar archivos guardados
- `GET /files/audio/{filename}` - Descargar archivo de audio
- `GET /files/transcription/{filename}` - Descargar transcripción

## 🔧 Configuración

### Variables de entorno (.env)

```env
PORT=3000
```

## 📱 App Android

La app Android está en la carpeta `android/`. Configura la URL del servidor en `MainActivity.kt`:

```kotlin
private val backendBaseUrl = "http://TU_IP:3000"
```

## 📝 Funcionalidad

1. **Transcripción**: Usa Whisper para transcribir audio a texto
2. **Generación de Informes**: Usa análisis basado en reglas (sin ML) para extraer información y generar un informe estructurado. Identifica automáticamente:
   - Decisiones tomadas
   - Acciones pendientes
   - Temas importantes
   - Participantes
   - Puntos clave
   - Áreas de mejora

Los archivos de audio y transcripciones se guardan en `backend/uploads/`.

## ⚡ Ventajas del sistema de reglas

- **Instantáneo**: No requiere procesamiento ML pesado
- **Sin errores de memoria**: No usa GPU/CPU intensivo
- **Confiable**: Siempre funciona, sin crashes
- **Sin descargas**: Solo Whisper (~500MB)

## ⚡ Tecnologías

- **Backend**: Python + FastAPI
- **Transcripción**: faster-whisper (Whisper local)
- **Generación de Informes**: Análisis basado en reglas (sin ML)
- **App Android**: Kotlin
