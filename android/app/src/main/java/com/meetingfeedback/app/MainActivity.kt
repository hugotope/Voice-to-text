package com.meetingfeedback.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtTranscription: TextView
    private lateinit var txtFeedback: TextView
    private lateinit var progressBar: ProgressBar

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var audioFile: File? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(100, java.util.concurrent.TimeUnit.MINUTES) // Aumentado para archivos de 1 hora
        .writeTimeout(10, java.util.concurrent.TimeUnit.MINUTES) // Aumentado para subir archivos grandes
        .build()

    // Cambia esta URL según tu configuración:
    // - Emulador Android: http://10.0.2.2:3000
    // - Dispositivo físico (mismo WiFi): http://TU_IP_LOCAL:3000
    // - Servidor en producción: https://tu-dominio.com
    private val backendBaseUrl = "http://192.168.1.50:3000"

    // Launcher para solicitar permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            txtStatus.text = getString(R.string.permission_audio_required)
            Toast.makeText(
                this,
                getString(R.string.permission_audio_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            initViews()
            setupButton()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al inicializar la app: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        btnRecord = findViewById(R.id.btnRecord)
        txtStatus = findViewById(R.id.txtStatus)
        txtTranscription = findViewById(R.id.txtTranscription)
        txtFeedback = findViewById(R.id.txtFeedback)
        progressBar = findViewById(R.id.progressBar)
        
        // Verificar que todas las vistas se inicializaron correctamente
        if (!::btnRecord.isInitialized || !::txtStatus.isInitialized || 
            !::txtTranscription.isInitialized || !::txtFeedback.isInitialized || 
            !::progressBar.isInitialized) {
            throw IllegalStateException("Error al inicializar vistas")
        }
    }

    private fun setupButton() {
        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkPermissionAndRecord()
            }
        }
    }

    private fun checkPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startRecording()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecording() {
        try {
            // Crear archivo temporal para el audio
            audioFile = File.createTempFile(
                "meeting_audio_",
                ".m4a",
                cacheDir
            )

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)

                try {
                    prepare()
                    start()
                    isRecording = true
                    btnRecord.text = "Detener Grabación"
                    txtStatus.text = getString(R.string.status_recording)
                    txtTranscription.text = getString(R.string.no_transcription)
                    txtFeedback.text = getString(R.string.no_feedback)
                } catch (e: IOException) {
                    e.printStackTrace()
                    txtStatus.text = getString(R.string.error_recording)
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            txtStatus.text = getString(R.string.error_recording)
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            btnRecord.text = getString(R.string.btn_record)

            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    // Validar tamaño mínimo del archivo (al menos 1KB)
                    if (file.length() < 1024) {
                        txtStatus.text = getString(R.string.error_recording)
                        Toast.makeText(this, getString(R.string.error_short_recording), Toast.LENGTH_SHORT).show()
                        file.delete()
                    } else {
                        processAudio(file)
                    }
                } else {
                    txtStatus.text = getString(R.string.error_recording)
                    Toast.makeText(this, "Archivo de audio vacío", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                txtStatus.text = getString(R.string.error_recording)
                Toast.makeText(this, "No se pudo crear el archivo de audio", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            txtStatus.text = getString(R.string.error_recording)
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun processAudio(file: File) {
        // Verificar conectividad antes de procesar
        if (!isNetworkAvailable()) {
            txtStatus.text = getString(R.string.error_network)
            Toast.makeText(
                this,
                getString(R.string.error_network),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        lifecycleScope.launch {
            progressBar.visibility = android.view.View.VISIBLE
            txtStatus.text = "Subiendo archivo y transcribiendo..."

            try {
                // Solo transcribir (sin feedback por ahora)
                val transcriptionResult = transcribeAudioFile(file)

                if (transcriptionResult != null) {
                    txtTranscription.text = transcriptionResult.text
                    txtStatus.text = "✅ Transcripción completada"
                    txtFeedback.text = "" // Limpiar feedback por ahora
                    
                    // Mostrar información de archivos guardados
                    val message = if (transcriptionResult.audioFile != null && transcriptionResult.transcriptionFile != null) {
                        "Archivo guardado: ${transcriptionResult.audioFile}\nTranscripción: ${transcriptionResult.transcriptionFile}"
                    } else {
                        "Transcripción completada"
                    }
                    Toast.makeText(
                        this@MainActivity,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    txtStatus.text = "Error: No se pudo transcribir"
                }
            } catch (e: IOException) {
                e.printStackTrace()
                val errorMessage = when {
                    e.message?.contains("Unable to resolve host") == true -> 
                        getString(R.string.error_network)
                    e.message?.contains("timeout") == true -> 
                        getString(R.string.error_timeout)
                    e.message?.contains("404") == true -> 
                        getString(R.string.error_server)
                    else -> e.message ?: "Error desconocido"
                }
                txtStatus.text = "Error: $errorMessage"
                Toast.makeText(
                    this@MainActivity,
                    errorMessage,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                e.printStackTrace()
                txtStatus.text = "Error: ${e.message ?: "Error desconocido"}"
                Toast.makeText(
                    this@MainActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressBar.visibility = android.view.View.GONE
                // Limpiar archivo temporal local (el servidor ya lo tiene guardado)
                try {
                    file.delete()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Clase para resultado de transcripción
    data class TranscriptionResult(
        val text: String,
        val audioFile: String? = null,
        val transcriptionFile: String? = null
    )

    private suspend fun transcribeAudioFile(file: File): TranscriptionResult? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    file.name,
                    file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$backendBaseUrl/transcribe")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Error desconocido"
                    
                    // Intentar parsear el JSON de error del servidor
                    var errorMessage = when (response.code) {
                        404 -> "Servidor no encontrado. Verifica la URL del backend."
                        500 -> {
                            try {
                                val errorJson = JSONObject(errorBody)
                                errorJson.optString("message", "Error del servidor. Verifica los logs del backend.")
                            } catch (e: Exception) {
                                "Error del servidor (500). Verifica los logs del backend: $errorBody"
                            }
                        }
                        503 -> "Servicio temporalmente no disponible. Intenta de nuevo en unos segundos."
                        else -> "Error del servidor: ${response.code} - $errorBody"
                    }
                    
                    android.util.Log.e("MainActivity", "Error del servidor: ${response.code} - $errorBody")
                    throw IOException(errorMessage)
                }

                val bodyStr = response.body?.string() ?: throw IOException("Respuesta vacía")
                val json = JSONObject(bodyStr)

                if (json.optBoolean("success", false)) {
                    val text = json.getString("text")
                    val audioFile = json.optString("audioFile", null)
                    val transcriptionFile = json.optString("transcriptionFile", null)
                    return@withContext TranscriptionResult(text, audioFile, transcriptionFile)
                } else {
                    val error = json.optString("error", "Error desconocido")
                    throw IOException(error)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private suspend fun transcribeAudio(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    file.name,
                    file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url("$backendBaseUrl/transcribe")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Error al transcribir: ${response.code}")
                }

                val bodyStr = response.body?.string() ?: throw IOException("Respuesta vacía")
                val json = JSONObject(bodyStr)

                if (json.optBoolean("success", false)) {
                    return@withContext json.getString("text")
                } else {
                    val error = json.optString("error", "Error desconocido")
                    throw IOException(error)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private suspend fun generateFeedback(text: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("text", text)

            val requestBody = RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                json.toString()
            )

            val request = Request.Builder()
                .url("$backendBaseUrl/feedback")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Error al generar feedback: ${response.code}")
                }

                val bodyStr = response.body?.string() ?: throw IOException("Respuesta vacía")
                val jsonResponse = JSONObject(bodyStr)

                if (jsonResponse.optBoolean("success", false)) {
                    return@withContext jsonResponse.getJSONObject("feedback")
                } else {
                    val error = jsonResponse.optString("error", "Error desconocido")
                    throw IOException(error)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun displayFeedback(feedbackJson: JSONObject) {
        try {
            val sb = StringBuilder()

            // Resumen
            val resumen = feedbackJson.optString("resumen", "")
            if (resumen.isNotEmpty()) {
                sb.append("📋 RESUMEN\n")
                sb.append("$resumen\n\n")
            }

            // Puntos clave
            val puntosClave = feedbackJson.optJSONArray("puntosClave")
            if (puntosClave != null && puntosClave.length() > 0) {
                sb.append("🔑 PUNTOS CLAVE\n")
                for (i in 0 until puntosClave.length()) {
                    sb.append("• ${puntosClave.getString(i)}\n")
                }
                sb.append("\n")
            }

            // Decisiones
            val decisiones = feedbackJson.optJSONArray("decisiones")
            if (decisiones != null && decisiones.length() > 0) {
                sb.append("✅ DECISIONES\n")
                for (i in 0 until decisiones.length()) {
                    sb.append("• ${decisiones.getString(i)}\n")
                }
                sb.append("\n")
            }

            // Acciones
            val acciones = feedbackJson.optJSONArray("acciones")
            if (acciones != null && acciones.length() > 0) {
                sb.append("📝 ACCIONES\n")
                for (i in 0 until acciones.length()) {
                    val accion = acciones.getJSONObject(i)
                    sb.append("• ${accion.optString("accion", "")}")
                    val responsable = accion.optString("responsable", "")
                    val fecha = accion.optString("fecha", "")
                    if (responsable.isNotEmpty() || fecha.isNotEmpty()) {
                        sb.append(" (")
                        if (responsable.isNotEmpty()) sb.append("Responsable: $responsable")
                        if (responsable.isNotEmpty() && fecha.isNotEmpty()) sb.append(", ")
                        if (fecha.isNotEmpty()) sb.append("Fecha: $fecha")
                        sb.append(")")
                    }
                    sb.append("\n")
                }
                sb.append("\n")
            }

            // Temas importantes
            val temasImportantes = feedbackJson.optJSONArray("temasImportantes")
            if (temasImportantes != null && temasImportantes.length() > 0) {
                sb.append("💡 TEMAS IMPORTANTES\n")
                for (i in 0 until temasImportantes.length()) {
                    sb.append("• ${temasImportantes.getString(i)}\n")
                }
                sb.append("\n")
            }

            // Feedback estructurado
            val feedback = feedbackJson.optJSONObject("feedback")
            if (feedback != null) {
                val positivo = feedback.optJSONArray("positivo")
                if (positivo != null && positivo.length() > 0) {
                    sb.append("👍 ASPECTOS POSITIVOS\n")
                    for (i in 0 until positivo.length()) {
                        sb.append("• ${positivo.getString(i)}\n")
                    }
                    sb.append("\n")
                }

                val mejoras = feedback.optJSONArray("mejoras")
                if (mejoras != null && mejoras.length() > 0) {
                    sb.append("🔧 ÁREAS DE MEJORA\n")
                    for (i in 0 until mejoras.length()) {
                        sb.append("• ${mejoras.getString(i)}\n")
                    }
                    sb.append("\n")
                }

                val siguientesPasos = feedback.optJSONArray("siguientesPasos")
                if (siguientesPasos != null && siguientesPasos.length() > 0) {
                    sb.append("➡️ SIGUIENTES PASOS\n")
                    for (i in 0 until siguientesPasos.length()) {
                        sb.append("• ${siguientesPasos.getString(i)}\n")
                    }
                    sb.append("\n")
                }
            }

            // Participantes
            val participantes = feedbackJson.optJSONArray("participantes")
            if (participantes != null && participantes.length() > 0) {
                sb.append("👥 PARTICIPANTES\n")
                for (i in 0 until participantes.length()) {
                    sb.append("• ${participantes.getString(i)}\n")
                }
            }

            txtFeedback.text = if (sb.isNotEmpty()) sb.toString() else feedbackJson.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            txtFeedback.text = feedbackJson.toString()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopRecording()
        }
    }

    /**
     * Verifica si hay conexión a internet disponible
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }

    data class ProcessResult(
        val transcription: String,
        val feedback: JSONObject
    )
}
