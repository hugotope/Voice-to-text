"""
Backend para transcripción de audio y generación de informes.
- Whisper (local): transcripción de audio.
- API Gemini (gratuita): extracción de informe desde el texto.
- PDF: generado con la información extraída.
"""
import os
import json
import re
import time
from pathlib import Path
from typing import Optional, List, Any
from datetime import datetime
from io import BytesIO

from fastapi import FastAPI, File, Form, UploadFile, HTTPException
from fastapi.responses import FileResponse, Response
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

# Modelo de transcripción
from faster_whisper import WhisperModel

# API Gemini y PDF (opcionales)
try:
    import google.generativeai as genai
    HAS_GEMINI = True
except ImportError:
    HAS_GEMINI = False
try:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
    from reportlab.lib.units import cm
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, ListFlowable, ListItem
    HAS_REPORTLAB = True
except ImportError:
    HAS_REPORTLAB = False

# Cargar variables de entorno (.env)
from dotenv import load_dotenv
load_dotenv()

# Configuración
app = FastAPI(title="Meeting Feedback API", version="3.0.0")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Directorio de uploads
UPLOADS_DIR = Path(__file__).parent / "uploads"
UPLOADS_DIR.mkdir(exist_ok=True)

# Variables globales para cache de modelos
whisper_model: Optional[WhisperModel] = None

# Modelos de Whisper disponibles (de menor a mayor tamaño/calidad)
WHISPER_MODELS = ["tiny", "base", "small", "medium", "large-v2", "large-v3"]


def initialize_whisper(model_size: str = "small") -> WhisperModel:
    """Inicializa el modelo de Whisper (se carga una vez)"""
    global whisper_model
    
    if whisper_model is not None:
        return whisper_model
    
    print(f"🔄 Cargando modelo Whisper '{model_size}' (esto puede tardar unos minutos la primera vez)...")
    
    try:
        # faster-whisper es más rápido que whisper original
        # device="cpu" o "cuda" si tienes GPU
        # compute_type="int8" para reducir memoria
        whisper_model = WhisperModel(
            model_size,
            device="cpu",
            compute_type="int8"
        )
        print("✅ Modelo Whisper cargado correctamente")
        return whisper_model
    except Exception as error:
        print(f"❌ Error al cargar modelo Whisper: {error}")
        raise


# Estructura esperada del informe (para API y fallback)
def _normalize_feedback_payload(data: dict) -> dict:
    """Asegura que el payload tenga la estructura esperada (listas, no null)."""
    fb = data.get("feedback") or {}
    if isinstance(fb, list):
        fb = {"positivo": fb[:10], "mejoras": [], "siguientesPasos": []}
    return {
        "resumen": data.get("resumen") or fb.get("resumen") or "Reunión registrada.",
        "puntosClave": _list(data.get("puntosClave") or fb.get("puntosClave")),
        "decisiones": _list(data.get("decisiones") or fb.get("decisiones")),
        "acciones": _list(data.get("acciones") or fb.get("acciones")),
        "temasImportantes": _list(data.get("temasImportantes") or fb.get("temasImportantes")),
        "feedback": {
            "positivo": _list(fb.get("positivo") if isinstance(fb, dict) else []),
            "mejoras": _list(fb.get("mejoras") if isinstance(fb, dict) else []),
            "siguientesPasos": _list(fb.get("siguientesPasos") if isinstance(fb, dict) else []),
        },
        "participantes": _list(data.get("participantes") or (fb.get("participantes") if isinstance(fb, dict) else [])),
    }


def _list(v: Any) -> List[str]:
    if v is None:
        return []
    if isinstance(v, list):
        return [str(x).strip() for x in v if x]
    return [str(v).strip()]


def generate_report_via_gemini(text: str) -> Optional[dict]:
    """
    Genera el informe estructurado llamando a la API gratuita de Google Gemini.
    Devuelve el mismo formato que generate_report_from_text o None si falla.
    """
    api_key = (os.getenv("GEMINI_API_KEY") or "").strip()
    if not api_key or not HAS_GEMINI:
        return None
    start_time = time.time()
    # Limitar tamaño para no exceder límites de la API
    text_for_api = text.strip()[:28000]
    prompt = f"""Eres un asistente que extrae información estructurada de transcripciones de reuniones en español.
A partir de la siguiente transcripción, extrae y devuelve ÚNICAMENTE un JSON válido (sin markdown, sin explicaciones) con estas claves en español:
- "resumen": string con un resumen breve de la reunión (2-4 frases).
- "puntosClave": lista de strings con los puntos clave tratados.
- "decisiones": lista de strings con las decisiones tomadas.
- "acciones": lista de strings con acciones o tareas asignadas.
- "temasImportantes": lista de strings con temas importantes.
- "participantes": lista de strings con nombres de participantes si se mencionan (si no, lista vacía).
- "feedback": objeto con "positivo" (lista de strings), "mejoras" (lista de strings), "siguientesPasos" (lista de strings).

Transcripción:
---
{text_for_api}
---
Responde solo con el JSON, sin texto adicional."""

    try:
        genai.configure(api_key=api_key)
        model = genai.GenerativeModel("gemini-1.5-flash")
        response = model.generate_content(prompt)
        raw = (response.text or "").strip()
        # Quitar posibles bloques markdown
        if raw.startswith("```"):
            raw = re.sub(r"^```(?:json)?\s*", "", raw)
            raw = re.sub(r"\s*```\s*$", "", raw)
        data = json.loads(raw)
        feedback = _normalize_feedback_payload(data)
        if isinstance(data.get("feedback"), dict):
            inner = data["feedback"]
            feedback["feedback"] = {
                "positivo": _list(inner.get("positivo")),
                "mejoras": _list(inner.get("mejoras")),
                "siguientesPasos": _list(inner.get("siguientesPasos")),
            }
        elapsed = time.time() - start_time
        print(f"✅ Informe generado con Gemini en {elapsed:.2f}s")
        return {
            "feedback": feedback,
            "generation_time": round(elapsed, 2),
            "model": "gemini-1.5-flash",
        }
    except Exception as e:
        print(f"⚠️ Gemini API error (usando fallback): {e}")
        return None


def build_pdf(feedback: dict, title: str = "Informe de reunión") -> Optional[BytesIO]:
    """Genera un PDF con la información del informe. Devuelve BytesIO o None."""
    if not HAS_REPORTLAB:
        return None
    buffer = BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A4, rightMargin=2*cm, leftMargin=2*cm, topMargin=2*cm, bottomMargin=2*cm)
    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(name="Title", parent=styles["Heading1"], fontSize=16, spaceAfter=12)
    heading_style = styles["Heading2"]
    body_style = styles["Normal"]
    story = []
    story.append(Paragraph(title, title_style))
    story.append(Paragraph(f"Fecha: {datetime.now().strftime('%d/%m/%Y %H:%M')}", body_style))
    story.append(Spacer(1, 0.5*cm))

    def section(name: str, items: List[str]) -> None:
        story.append(Paragraph(name, heading_style))
        if items:
            list_items = [ListItem(Paragraph(x.replace("<", "&lt;").replace(">", "&gt;"), body_style)) for x in items if x]
            story.append(ListFlowable(list_items))
        story.append(Spacer(1, 0.3*cm))

    story.append(Paragraph("Resumen", heading_style))
    story.append(Paragraph((feedback.get("resumen") or "").replace("<", "&lt;"), body_style))
    story.append(Spacer(1, 0.5*cm))

    section("Puntos clave", _list(feedback.get("puntosClave")))
    section("Decisiones", _list(feedback.get("decisiones")))
    section("Acciones / Tareas", _list(feedback.get("acciones")))
    section("Temas importantes", _list(feedback.get("temasImportantes")))
    section("Participantes", _list(feedback.get("participantes")))
    inner = feedback.get("feedback") or {}
    section("Siguientes pasos", _list(inner.get("siguientesPasos")))
    section("Aspectos positivos", _list(inner.get("positivo")))
    section("Áreas de mejora", _list(inner.get("mejoras")))

    try:
        doc.build(story)
        buffer.seek(0)
        return buffer
    except Exception as e:
        print(f"⚠️ Error generando PDF: {e}")
        return None


def generate_report_from_text(text: str) -> dict:
    """
    Genera un informe estructurado: intenta API Gemini; si falla, usa análisis por reglas.
    """
    result = generate_report_via_gemini(text)
    if result is not None:
        return result
    return _generate_report_rules(text)


def _generate_report_rules(text: str) -> dict:
    """
    Genera un informe estructurado desde texto usando análisis simple (sin ML).
    Extrae información clave, decisiones, acciones, etc. usando patrones y reglas.
    """
    start_time = time.time()
    print(f"📊 Analizando transcripción ({len(text)} caracteres)...")
    
    # Normalizar texto
    text_lower = text.lower()
    sentences = re.split(r'[.!?]+', text)
    sentences = [s.strip() for s in sentences if len(s.strip()) > 10]
    
    # Palabras clave para identificar diferentes tipos de contenido
    keywords_decision = ['decidimos', 'acordamos', 'vamos a', 'haremos', 'decidió', 'acuerdo', 'resolución', 'aprobado']
    keywords_action = ['debe', 'tiene que', 'hay que', 'necesita', 'pendiente', 'tarea', 'responsable', 'deadline', 'plazo', 'asignado']
    keywords_important = ['importante', 'clave', 'fundamental', 'crítico', 'esencial', 'prioritario', 'urgente', 'significativo']
    keywords_positive = ['bien', 'éxito', 'logro', 'avance', 'completado', 'excelente', 'bueno', 'satisfactorio']
    keywords_improvement = ['mejorar', 'problema', 'error', 'fallo', 'retraso', 'dificultad', 'desafío', 'revisar']
    
    # Extraer información
    decisions = []
    actions = []
    important_themes = []
    positive_feedback = []
    improvements = []
    next_steps = []
    key_points = []
    
    for sentence in sentences:
        sentence_lower = sentence.lower()
        
        # Identificar decisiones
        if any(kw in sentence_lower for kw in keywords_decision):
            if len(sentence) > 20 and sentence not in decisions:
                decisions.append(sentence.strip())
        
        # Identificar acciones
        if any(kw in sentence_lower for kw in keywords_action):
            if len(sentence) > 20 and sentence not in actions:
                actions.append(sentence.strip())
                next_steps.append(sentence.strip())
        
        # Identificar temas importantes
        if any(kw in sentence_lower for kw in keywords_important):
            if len(sentence) > 20 and sentence not in important_themes:
                important_themes.append(sentence.strip())
                if sentence not in key_points:
                    key_points.append(sentence.strip())
        
        # Identificar feedback positivo
        if any(kw in sentence_lower for kw in keywords_positive):
            if len(sentence) > 20 and sentence not in positive_feedback:
                positive_feedback.append(sentence.strip())
        
        # Identificar áreas de mejora
        if any(kw in sentence_lower for kw in keywords_improvement):
            if len(sentence) > 20 and sentence not in improvements:
                improvements.append(sentence.strip())
    
    # Extraer participantes (nombres propios - heurística simple)
    participants = []
    words = text.split()
    for i, word in enumerate(words):
        # Buscar palabras que empiecen con mayúscula (posibles nombres)
        if word and len(word) > 2 and word[0].isupper() and word.isalpha():
            # Verificar si no es la primera palabra de una frase
            if i > 0:
                # Verificar si la siguiente palabra también es mayúscula (nombre completo)
                if i + 1 < len(words) and words[i + 1] and words[i + 1][0].isupper() and words[i + 1].isalpha():
                    full_name = f"{word} {words[i + 1]}"
                    if full_name not in participants:
                        participants.append(full_name)
                elif word not in participants and len(participants) < 15:
                    participants.append(word)
    
    # Limitar cantidad de items
    decisions = decisions[:5]
    actions = actions[:7]
    important_themes = important_themes[:5]
    positive_feedback = positive_feedback[:5]
    improvements = improvements[:5]
    next_steps = next_steps[:7]
    
    # Si no hay puntos clave específicos, usar las primeras frases importantes
    if not key_points:
        key_points = sentences[:min(5, len(sentences))]
    else:
        key_points = key_points[:8]
    
    # Limitar participantes
    participants = participants[:10]
    
    # Generar resumen (primeras 3 frases o 200 caracteres)
    summary_sentences = sentences[:min(3, len(sentences))]
    summary = '. '.join(summary_sentences)
    if len(summary) > 200:
        summary = summary[:197] + '...'
    elif not summary:
        summary = "Reunión registrada."
    
    generation_time = time.time() - start_time
    
    print(f"✅ Informe generado en {generation_time:.2f} segundos:")
    print(f"   - Puntos clave: {len(key_points)}")
    print(f"   - Decisiones: {len(decisions)}")
    print(f"   - Acciones: {len(actions)}")
    print(f"   - Temas importantes: {len(important_themes)}")
    print(f"   - Participantes detectados: {len(participants)}")
    
    return {
        "feedback": {
            "resumen": summary,
            "puntosClave": key_points,
            "decisiones": decisions,
            "acciones": actions,
            "temasImportantes": important_themes,
            "feedback": {
                "positivo": positive_feedback,
                "mejoras": improvements,
                "siguientesPasos": next_steps
            },
            "participantes": participants,
        },
        "generation_time": round(generation_time, 2),
        "model": "rule_based_analysis"
    }


# ============================================================================
# ENDPOINTS
# ============================================================================

@app.get("/health")
async def health_check():
    """Endpoint de salud"""
    report_mode = "gemini" if (os.getenv("GEMINI_API_KEY") or "").strip() and HAS_GEMINI else "rule_based"
    return {
        "status": "ok",
        "message": "Servidor funcionando correctamente",
        "whisper": "loaded" if whisper_model is not None else "not-loaded",
        "report_generator": report_mode,
        "pdf_available": HAS_REPORTLAB,
    }


@app.get("/files/audio/{filename}")
async def get_audio_file(filename: str):
    """Servir archivos de audio"""
    file_path = UPLOADS_DIR / filename
    
    if not file_path.exists():
        raise HTTPException(status_code=404, detail=f"El archivo {filename} no existe en el servidor")
    
    if not filename.lower().endswith(('.mp4', '.m4a', '.mp3', '.wav', '.webm', '.ogg', '.3gp')):
        raise HTTPException(status_code=400, detail="Solo se pueden servir archivos de audio")
    
    return FileResponse(
        path=file_path,
        media_type="audio/mp4",
        filename=filename
    )


@app.get("/files/transcription/{filename}")
async def get_transcription_file(filename: str):
    """Servir archivos de transcripción"""
    file_path = UPLOADS_DIR / filename

    if not file_path.exists():
        raise HTTPException(status_code=404, detail=f"El archivo {filename} no existe en el servidor")

    if not filename.endswith('.txt'):
        raise HTTPException(status_code=400, detail="Solo se pueden servir archivos de transcripción (.txt)")

    return FileResponse(
        path=file_path,
        media_type="text/plain; charset=utf-8",
        filename=filename
    )


@app.get("/files/report/{filename}")
async def get_report_pdf(filename: str):
    """Servir informes en PDF"""
    file_path = UPLOADS_DIR / filename
    if not file_path.exists():
        raise HTTPException(status_code=404, detail=f"El archivo {filename} no existe")
    if not filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Solo se pueden servir archivos PDF")
    return FileResponse(
        path=file_path,
        media_type="application/pdf",
        filename=filename
    )


@app.get("/files/list")
async def list_files():
    """Listar todos los archivos disponibles"""
    try:
        files = list(UPLOADS_DIR.iterdir())
        audio_files = [f.name for f in files if re.match(r'^audio_.*\.mp4$', f.name, re.IGNORECASE)]
        transcription_files = [f.name for f in files if f.name.endswith('.txt')]
        
        # Emparejar archivos de audio con sus transcripciones
        paired_files = []
        for audio_file in audio_files:
            base_name = audio_file.replace('.mp4', '')
            transcription_file = next((t for t in transcription_files if t.startswith(base_name)), None)
            
            paired_files.append({
                "audio": audio_file,
                "transcription": transcription_file,
                "audioUrl": f"/files/audio/{audio_file}",
                "transcriptionUrl": f"/files/transcription/{transcription_file}" if transcription_file else None
            })
        
        return {
            "success": True,
            "files": paired_files,
            "total": len(paired_files)
        }
    except Exception as error:
        raise HTTPException(status_code=500, detail=f"Error al listar archivos: {str(error)}")


@app.post("/transcribe")
async def transcribe_audio(
    audio: UploadFile = File(...),
    language: str = Form("es")
):
    """Transcribe audio usando Whisper local. El parámetro 'language' indica el idioma (ej: 'es', 'en', 'fr')."""
    try:
        if not audio:
            raise HTTPException(status_code=400, detail="No se recibió archivo de audio")

        # Normalizar código de idioma
        lang_code = language.strip().lower() if language else "es"
        print(f"🌐 Idioma seleccionado para transcripción: {lang_code}")

        # Generar nombre de archivo descriptivo
        timestamp = int(time.time() * 1000)
        original_name = audio.filename or "audio"
        base_name = os.path.splitext(original_name)[0]
        audio_filename = f"audio_{timestamp}_{base_name}.mp4"
        audio_path = UPLOADS_DIR / audio_filename
        
        # Guardar archivo
        with open(audio_path, "wb") as f:
            content = await audio.read()
            if len(content) == 0:
                audio_path.unlink(missing_ok=True)
                raise HTTPException(status_code=400, detail="El archivo de audio está vacío")
            
            if len(content) < 1024:
                audio_path.unlink(missing_ok=True)
                raise HTTPException(status_code=400, detail="El archivo de audio es demasiado pequeño (menos de 1KB)")
            
            f.write(content)
        
        print(f"🎤 Transcribiendo archivo: {original_name}")
        print(f"   Tamaño del archivo: {len(content) / 1024:.2f} KB")
        
        # Inicializar Whisper si no está cargado
        model = initialize_whisper("small")
        
        # Transcribir con el idioma seleccionado
        print(f"🎤 Iniciando transcripción en idioma: {lang_code}...")
        segments, info = model.transcribe(
            str(audio_path),
            language=lang_code,
            beam_size=5
        )
        
        # Concatenar segmentos
        transcribed_text = " ".join([segment.text for segment in segments])
        
        if not transcribed_text or not transcribed_text.strip():
            raise HTTPException(status_code=500, detail="La transcripción está vacía")
        
        print(f"✅ Transcripción completada. Longitud: {len(transcribed_text)} caracteres")
        print(f"💾 Archivo de audio guardado: {audio_filename}")
        
        # Guardar transcripción
        base_name = audio_filename.replace('.mp4', '')
        transcription_filename = f"{base_name}.txt"
        transcription_path = UPLOADS_DIR / transcription_filename
        
        try:
            transcription_path.write_text(transcribed_text, encoding='utf-8')
            print(f"💾 Transcripción guardada en: {transcription_path}")
        except Exception as save_error:
            print(f"⚠️  No se pudo guardar la transcripción: {save_error}")
        
        return {
            "text": transcribed_text,
            "success": True,
            "audioFile": audio_filename,
            "transcriptionFile": transcription_filename
        }
    except HTTPException:
        raise
    except Exception as error:
        print(f"❌ Error en /transcribe: {error}")
        raise HTTPException(status_code=500, detail=f"Error al transcribir audio: {str(error)}")


def _save_pdf_and_get_filename(feedback: dict, base_name: str = "") -> Optional[str]:
    """Genera el PDF, lo guarda en uploads y devuelve el nombre del archivo o None."""
    buffer = build_pdf(feedback, title="Informe de reunión")
    if buffer is None:
        return None
    timestamp = int(time.time() * 1000)
    safe_base = re.sub(r"[^\w\-]", "_", base_name)[:80] if base_name else "report"
    filename = f"report_{timestamp}_{safe_base}.pdf"
    path = UPLOADS_DIR / filename
    with open(path, "wb") as f:
        f.write(buffer.read())
    print(f"💾 PDF guardado: {filename}")
    return filename


@app.post("/feedback")
async def generate_feedback(request: dict):
    """Genera informe con API Gemini (o reglas si no hay API key) y opcionalmente PDF."""
    try:
        text = request.get("text")
        generate_pdf = request.get("pdf", True)

        if not text or not isinstance(text, str) or not text.strip():
            raise HTTPException(status_code=400, detail="Campo 'text' requerido")

        print(f"📝 Generando informe para texto de {len(text)} caracteres")

        result = generate_report_from_text(text)
        feedback = result["feedback"]

        pdf_filename = None
        if generate_pdf and HAS_REPORTLAB:
            pdf_filename = _save_pdf_and_get_filename(feedback)

        out = {
            "feedback": feedback,
            "success": True,
            "model": result["model"],
            "generationTime": result["generation_time"],
            "timestamp": datetime.now().isoformat(),
        }
        if pdf_filename:
            out["pdfFile"] = pdf_filename
            out["pdfUrl"] = f"/files/report/{pdf_filename}"
        return out
    except HTTPException:
        raise
    except Exception as error:
        print(f"❌ Error en /feedback: {error}")
        raise HTTPException(status_code=500, detail=f"Error al generar feedback: {str(error)}")


@app.post("/process")
async def process_audio(
    audio: UploadFile = File(...),
    language: str = Form("es")
):
    """Endpoint combinado que transcribe y genera informe. El parámetro 'language' indica el idioma de Whisper."""
    try:
        if not audio:
            raise HTTPException(status_code=400, detail="No se recibió archivo de audio")

        lang_code = language.strip().lower() if language else "es"
        print(f"🌐 Idioma seleccionado para procesamiento: {lang_code}")

        # Generar nombre de archivo
        timestamp = int(time.time() * 1000)
        original_name = audio.filename or "audio"
        base_name = os.path.splitext(original_name)[0]
        audio_filename = f"audio_{timestamp}_{base_name}.mp4"
        audio_path = UPLOADS_DIR / audio_filename
        
        # Guardar archivo
        content = await audio.read()
        if len(content) == 0:
            raise HTTPException(status_code=400, detail="El archivo de audio está vacío")
        
        with open(audio_path, "wb") as f:
            f.write(content)
        
        file_size = len(content)
        print(f"🔄 Procesando archivo completo:")
        print(f"   Nombre: {original_name}")
        print(f"   Tamaño: {file_size / 1024:.2f} KB")
        
        # Paso 1: Transcribir
        print("📝 Iniciando transcripción con Whisper...")
        model = initialize_whisper("small")
        
        print(f"🎤 Transcribiendo audio en idioma: {lang_code}...")
        segments, info = model.transcribe(
            str(audio_path),
            language=lang_code,
            beam_size=5
        )
        
        transcribed_text = " ".join([segment.text for segment in segments])
        
        if not transcribed_text or not transcribed_text.strip():
            raise HTTPException(status_code=500, detail="La transcripción está vacía")
        
        print(f"✅ Transcripción completada. Longitud: {len(transcribed_text)} caracteres")
        print(f"💾 Archivo de audio guardado: {audio_filename}")
        
        # Guardar transcripción
        transcription_filename = f"{audio_filename.replace('.mp4', '')}.txt"
        transcription_path = UPLOADS_DIR / transcription_filename
        
        try:
            transcription_path.write_text(transcribed_text, encoding='utf-8')
            print(f"💾 Transcripción guardada en: {transcription_path}")
        except Exception as save_error:
            print(f"⚠️  No se pudo guardar la transcripción: {save_error}")
        
        # Paso 2: Generar informe (API Gemini o reglas) y PDF
        print("📊 Generando informe (API Gemini o reglas)...")
        result = generate_report_from_text(transcribed_text)
        feedback = result["feedback"]

        pdf_filename = None
        if HAS_REPORTLAB:
            pdf_filename = _save_pdf_and_get_filename(feedback, base_name=base_name)

        out = {
            "transcription": transcribed_text,
            "feedback": feedback,
            "success": True,
            "timestamp": datetime.now().isoformat(),
            "model": result["model"],
            "generationTime": result["generation_time"],
            "audioFile": audio_filename,
            "transcriptionFile": transcription_filename,
        }
        if pdf_filename:
            out["pdfFile"] = pdf_filename
            out["pdfUrl"] = f"/files/report/{pdf_filename}"
        return out
    except HTTPException:
        raise
    except Exception as error:
        print(f"❌ Error en /process: {error}")
        raise HTTPException(status_code=500, detail=f"Error al procesar audio: {str(error)}")


if __name__ == "__main__":
    # Configurar encoding UTF-8 para la consola de Windows
    import sys
    if sys.platform == "win32":
        try:
            sys.stdout.reconfigure(encoding='utf-8')
        except:
            pass
    
    port = int(os.getenv("PORT", 3000))
    
    print(f"[*] Servidor escuchando en http://localhost:{port}")
    print(f"[*] Endpoints disponibles:")
    print(f"   GET  /health - Verificar estado del servidor")
    print(f"   POST /transcribe - Transcribir audio (Whisper)")
    print(f"   POST /feedback - Generar informe (API Gemini o reglas) y PDF")
    print(f"   POST /process - Transcribir y generar informe + PDF en una llamada")
    print(f"   GET  /files/report/{{filename}} - Descargar informe PDF")
    if (os.getenv("GEMINI_API_KEY") or "").strip() and HAS_GEMINI:
        print(f"\n[*] Informes: API Gemini (gratuita) + PDF")
    else:
        print(f"\n[*] Informes: analisis por reglas (configura GEMINI_API_KEY para usar Gemini)")
    print(f"[*] Whisper: local (~500MB primera vez). PDF: generado con ReportLab")
    
    uvicorn.run(app, host="0.0.0.0", port=port)
