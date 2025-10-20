package com.meetingfeedback.app

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MeetingViewModel(application: Application) : AndroidViewModel(application) {

    // ─── Domain models ────────────────────────────────────────────────────────

    data class Language(val code: String, val label: String, val flag: String)

    enum class GroupType { MEETING, INTERVIEW, GROUP }

    data class Recording(
        val id: Long,
        val title: String,
        val date: String,
        val duration: String,
        val languageCode: String,
        val pdfUrl: String? = null,
        val localAudioPath: String? = null,
        val localPdfPath: String? = null   // content URI or file path after download
    )

    data class Group(
        val id: Long,
        val name: String,
        val type: GroupType,
        val members: List<String>,
        val recordings: List<Recording>,
        val language: String = "es",
        val trashedAt: Long? = null
    )

    sealed class Screen {
        object Home        : Screen()
        object GroupDetail : Screen()
        object Trash       : Screen()
    }

    data class GroupDraft(
        val name: String = "",
        val type: GroupType = GroupType.GROUP,
        val members: List<String> = listOf(""),
        val language: String = "es"
    )

    sealed class RecordingState {
        object Idle       : RecordingState()
        object Recording  : RecordingState()
        object Processing : RecordingState()
    }

    data class UiState(
        val screen: Screen = Screen.Home,
        val groups: List<Group> = emptyList(),
        val trashedGroups: List<Group> = emptyList(),
        val activeGroup: Group? = null,
        val showNewGroupModal: Boolean = false,
        val showSettingsModal: Boolean = false,
        // null = no confirm; -1L = vaciar todo; other = id del grupo a borrar def.
        val deleteConfirmId: Long? = null,
        val groupDraft: GroupDraft = GroupDraft(),
        val serverUrl: String = DEFAULT_SERVER_URL,
        val serverUrlDraft: String = DEFAULT_SERVER_URL,
        val recordingState: RecordingState = RecordingState.Idle,
        val statusMessage: String = "Listo para grabar",
        val transcription: String = "",
        val snackbarMessage: String? = null,
        val snackbarIsSuccess: Boolean = false,
        val downloadingPdfForRecordingId: Long? = null,
        val playingRecordingId: Long? = null,
        val isAudioPlaying: Boolean = false,
        val openPdfUri: String? = null,    // set to trigger PDF viewer Intent
        val isDarkTheme: Boolean = true
    )

    companion object {
        val LANGUAGES = listOf(
            Language("es", "Español",   "🇪🇸"),
            Language("en", "English",   "🇬🇧"),
            Language("fr", "Français",  "🇫🇷"),
            Language("de", "Deutsch",   "🇩🇪"),
            Language("pt", "Português", "🇧🇷"),
            Language("it", "Italiano",  "🇮🇹"),
            Language("zh", "中文",       "🇨🇳"),
            Language("ja", "日本語",     "🇯🇵")
        )
        private const val DEFAULT_SERVER_URL = "http://192.168.193.232:3000"
        private const val PREFS_NAME      = "voicelog_prefs"
        private const val KEY_GROUPS      = "groups"
        private const val KEY_TRASH       = "trash"
        private const val KEY_SERVER      = "server_url"
        private const val KEY_DARK_THEME  = "dark_theme"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var recordingStartTime: Long = 0L
    private var mediaPlayer: MediaPlayer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    init { loadFromPrefs() }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private fun loadFromPrefs() {
        val url      = prefs.getString(KEY_SERVER, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        val groups   = loadGroupListFromPrefs(KEY_GROUPS)
        val trash    = loadGroupListFromPrefs(KEY_TRASH)
        val isDark   = prefs.getBoolean(KEY_DARK_THEME, true)
        _uiState.value = _uiState.value.copy(
            groups = groups, trashedGroups = trash,
            serverUrl = url, serverUrlDraft = url,
            isDarkTheme = isDark
        )
    }

    fun toggleTheme() {
        val newDark = !_uiState.value.isDarkTheme
        _uiState.value = _uiState.value.copy(isDarkTheme = newDark)
        prefs.edit().putBoolean(KEY_DARK_THEME, newDark).apply()
    }

    private fun loadGroupListFromPrefs(key: String): List<Group> {
        return try {
            val json = prefs.getString(key, null) ?: return emptyList()
            val arr  = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                try { arr.getJSONObject(i).toGroup() } catch (e: Exception) { null }
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun persist() {
        val st = _uiState.value
        prefs.edit()
            .putString(KEY_GROUPS, JSONArray(st.groups.map { it.toJson() }).toString())
            .putString(KEY_TRASH,  JSONArray(st.trashedGroups.map { it.toJson() }).toString())
            .apply()
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun Group.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type.name); put("language", language)
        if (trashedAt != null) put("trashedAt", trashedAt) else put("trashedAt", JSONObject.NULL)
        put("members", JSONArray(members))
        put("recordings", JSONArray(recordings.map { it.toJson() }))
    }

    private fun JSONObject.toGroup(): Group = Group(
        id        = getLong("id"),
        name      = getString("name"),
        type      = try { GroupType.valueOf(optString("type", "GROUP")) } catch (e: Exception) { GroupType.GROUP },
        language  = optString("language", "es"),
        trashedAt = if (isNull("trashedAt")) null else optLong("trashedAt"),
        members   = (optJSONArray("members") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) } },
        recordings = (optJSONArray("recordings") ?: JSONArray()).let { a ->
            (0 until a.length()).mapNotNull { i -> try { a.getJSONObject(i).toRecording() } catch (e: Exception) { null } }
        }
    )

    private fun Recording.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("date", date)
        put("duration", duration); put("languageCode", languageCode)
        if (pdfUrl != null) put("pdfUrl", pdfUrl) else put("pdfUrl", JSONObject.NULL)
        if (localAudioPath != null) put("localAudioPath", localAudioPath) else put("localAudioPath", JSONObject.NULL)
        if (localPdfPath  != null) put("localPdfPath",  localPdfPath)  else put("localPdfPath",  JSONObject.NULL)
    }

    private fun JSONObject.toRecording(): Recording = Recording(
        id             = getLong("id"),
        title          = getString("title"),
        date           = getString("date"),
        duration       = getString("duration"),
        languageCode   = optString("languageCode", "es"),
        pdfUrl         = if (isNull("pdfUrl"))        null else optString("pdfUrl").takeIf        { it.isNotEmpty() },
        localAudioPath = if (isNull("localAudioPath")) null else optString("localAudioPath").takeIf { it.isNotEmpty() },
        localPdfPath   = if (isNull("localPdfPath"))  null else optString("localPdfPath").takeIf  { it.isNotEmpty() }
    )

    // ─── Navigation ───────────────────────────────────────────────────────────

    fun openGroup(group: Group) {
        stopPlayback()
        _uiState.value = _uiState.value.copy(activeGroup = group, screen = Screen.GroupDetail, transcription = "")
    }

    fun goHome() {
        stopPlayback()
        _uiState.value = _uiState.value.copy(screen = Screen.Home, activeGroup = null, transcription = "")
    }

    fun openTrash() {
        stopPlayback()
        _uiState.value = _uiState.value.copy(screen = Screen.Trash)
    }

    // ─── Group management ─────────────────────────────────────────────────────

    fun showNewGroupModal()    { _uiState.value = _uiState.value.copy(showNewGroupModal = true) }
    fun dismissNewGroupModal() { _uiState.value = _uiState.value.copy(showNewGroupModal = false, groupDraft = GroupDraft()) }

    fun updateGroupDraftName(name: String) {
        _uiState.value = _uiState.value.copy(groupDraft = _uiState.value.groupDraft.copy(name = name))
    }

    fun updateGroupDraftLanguage(code: String) {
        _uiState.value = _uiState.value.copy(groupDraft = _uiState.value.groupDraft.copy(language = code))
    }

    fun updateGroupDraftMember(index: Int, value: String) {
        val updated = _uiState.value.groupDraft.members.toMutableList()
        if (index < updated.size) updated[index] = value
        _uiState.value = _uiState.value.copy(groupDraft = _uiState.value.groupDraft.copy(members = updated))
    }

    fun addGroupDraftMember() {
        _uiState.value = _uiState.value.copy(
            groupDraft = _uiState.value.groupDraft.copy(members = _uiState.value.groupDraft.members + "")
        )
    }

    fun removeGroupDraftMember(index: Int) {
        val updated = _uiState.value.groupDraft.members.filterIndexed { i, _ -> i != index }
        _uiState.value = _uiState.value.copy(
            groupDraft = _uiState.value.groupDraft.copy(members = if (updated.isEmpty()) listOf("") else updated)
        )
    }

    fun createGroup() {
        val draft   = _uiState.value.groupDraft
        val members = draft.members.filter { it.isNotBlank() }
        if (draft.name.isBlank() || members.isEmpty()) return

        val group = Group(
            id = System.currentTimeMillis(), name = draft.name.trim(),
            type = GroupType.GROUP,  // Always Grupo
            members = members, recordings = emptyList(), language = draft.language
        )
        _uiState.value = _uiState.value.copy(
            groups = listOf(group) + _uiState.value.groups,
            showNewGroupModal = false, groupDraft = GroupDraft(),
            activeGroup = group, screen = Screen.GroupDetail
        )
        persist()
    }

    // ─── Trash ────────────────────────────────────────────────────────────────

    fun moveToTrash(group: Group) {
        stopPlayback()
        val trashed = group.copy(trashedAt = System.currentTimeMillis())
        val newGroups = _uiState.value.groups.filter { it.id != group.id }
        val newTrash  = listOf(trashed) + _uiState.value.trashedGroups
        val base = _uiState.value.copy(groups = newGroups, trashedGroups = newTrash)
        _uiState.value = if (_uiState.value.activeGroup?.id == group.id)
            base.copy(screen = Screen.Home, activeGroup = null)
        else base
        persist()
    }

    fun restoreFromTrash(group: Group) {
        val restored  = group.copy(trashedAt = null)
        _uiState.value = _uiState.value.copy(
            groups        = listOf(restored) + _uiState.value.groups,
            trashedGroups = _uiState.value.trashedGroups.filter { it.id != group.id }
        )
        persist()
    }

    fun requestDeleteForever(groupId: Long) {
        _uiState.value = _uiState.value.copy(deleteConfirmId = groupId)
    }

    fun requestEmptyTrash() {
        _uiState.value = _uiState.value.copy(deleteConfirmId = -1L)
    }

    fun cancelDeleteConfirm() {
        _uiState.value = _uiState.value.copy(deleteConfirmId = null)
    }

    fun confirmDeleteForever() {
        val id = _uiState.value.deleteConfirmId ?: return
        val newTrash = if (id == -1L) emptyList()
                       else _uiState.value.trashedGroups.filter { it.id != id }
        _uiState.value = _uiState.value.copy(trashedGroups = newTrash, deleteConfirmId = null)
        persist()
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    fun showSettings() {
        _uiState.value = _uiState.value.copy(showSettingsModal = true, serverUrlDraft = _uiState.value.serverUrl)
    }

    fun dismissSettings() {
        _uiState.value = _uiState.value.copy(showSettingsModal = false)
    }

    fun updateServerUrlDraft(url: String) {
        _uiState.value = _uiState.value.copy(serverUrlDraft = url)
    }

    fun saveServerUrl() {
        val url = _uiState.value.serverUrlDraft.trim().trimEnd('/')
        prefs.edit().putString(KEY_SERVER, url).apply()
        stopPlayback()
        _uiState.value = _uiState.value.copy(serverUrl = url, showSettingsModal = false)
    }

    // ─── Audio playback ───────────────────────────────────────────────────────

    fun playRecording(recording: Recording) {
        val path = recording.localAudioPath ?: run {
            showError("Sin audio local. El archivo no está guardado en este dispositivo.")
            return
        }

        if (_uiState.value.playingRecordingId == recording.id) {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) { mp.pause(); _uiState.value = _uiState.value.copy(isAudioPlaying = false) }
                else              { mp.start();  _uiState.value = _uiState.value.copy(isAudioPlaying = true)  }
            }
            return
        }

        stopPlayback()

        try {
            val context = getApplication<Application>()
            mediaPlayer = MediaPlayer().apply {
                if (path.startsWith("content://")) setDataSource(context, Uri.parse(path))
                else                               setDataSource(path)
                setOnCompletionListener {
                    _uiState.value = _uiState.value.copy(playingRecordingId = null, isAudioPlaying = false)
                }
                setOnErrorListener { _, _, _ ->
                    _uiState.value = _uiState.value.copy(playingRecordingId = null, isAudioPlaying = false)
                    showError("Error al reproducir el audio")
                    false
                }
                prepare()
                start()
            }
            _uiState.value = _uiState.value.copy(playingRecordingId = recording.id, isAudioPlaying = true)
        } catch (e: Exception) {
            Log.e("MeetingViewModel", "Error reproduciendo: ${e.message}")
            showError("No se puede reproducir: ${e.message}")
            try { mediaPlayer?.release() } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    fun stopPlayback() {
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        _uiState.value = _uiState.value.copy(playingRecordingId = null, isAudioPlaying = false)
    }

    // ─── Snackbar ─────────────────────────────────────────────────────────────

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null, snackbarIsSuccess = false)
    }

    private fun showError(msg: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = msg, snackbarIsSuccess = false)
    }

    private fun showSuccess(msg: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = msg, snackbarIsSuccess = true)
    }

    // ─── Recording controls ───────────────────────────────────────────────────

    fun startRecording() {
        try {
            val context = getApplication<Application>()
            audioFile = File.createTempFile("meeting_audio_", ".m4a", context.cacheDir)
            recordingStartTime = System.currentTimeMillis()

            mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
            ).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile!!.absolutePath)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                prepare(); start()
            }
            _uiState.value = _uiState.value.copy(
                recordingState = RecordingState.Recording,
                statusMessage = "Grabando...", transcription = "", snackbarMessage = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Error al iniciar grabación: ${e.message}")
            _uiState.value = _uiState.value.copy(recordingState = RecordingState.Idle, statusMessage = "Error al grabar")
        }
    }

    fun stopRecording() {
        try {
            val elapsedMs = System.currentTimeMillis() - recordingStartTime
            mediaRecorder?.apply { stop(); release() }
            mediaRecorder = null
            val file = audioFile
            if (file != null && file.exists() && file.length() > 1024) {
                processAudio(file, elapsedMs)
            } else {
                file?.delete()
                showError("La grabación es demasiado corta. Intenta grabar más tiempo.")
                _uiState.value = _uiState.value.copy(recordingState = RecordingState.Idle, statusMessage = "Grabación demasiado corta")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showError("Error al detener grabación: ${e.message}")
            _uiState.value = _uiState.value.copy(recordingState = RecordingState.Idle, statusMessage = "Error al detener")
        }
    }

    fun stopRecordingIfActive() {
        if (_uiState.value.recordingState is RecordingState.Recording) stopRecording()
    }

    // ─── Audio processing ─────────────────────────────────────────────────────

    private fun processAudio(file: File, durationMs: Long) {
        val context = getApplication<Application>()
        if (!isNetworkAvailable(context)) {
            showError("Error de conexión. Verifica tu conexión a internet.")
            _uiState.value = _uiState.value.copy(recordingState = RecordingState.Idle, statusMessage = "Sin conexión")
            return
        }

        _uiState.value = _uiState.value.copy(
            recordingState = RecordingState.Processing, statusMessage = "Procesando grabación..."
        )

        val languageCode = _uiState.value.activeGroup?.language ?: "es"
        val groupName    = _uiState.value.activeGroup?.name ?: "reunion"
        val currentUrl   = _uiState.value.serverUrl

        viewModelScope.launch {
            val localAudioPath = withContext(Dispatchers.IO) { saveAudioToDevice(file, groupName) }
            if (localAudioPath != null) Log.d("MeetingViewModel", "Audio guardado en: $localAudioPath")

            try {
                val result = processAudioFile(file, languageCode, currentUrl)
                if (result != null) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val newRecording = Recording(
                        id = System.currentTimeMillis(),
                        title = "Grabación $dateStr",
                        date = dateStr,
                        duration = formatDuration(durationMs),
                        languageCode = languageCode,
                        pdfUrl = result.pdfUrl,
                        localAudioPath = localAudioPath
                    )
                    val current      = _uiState.value
                    val updatedGroup = current.activeGroup?.copy(
                        recordings = listOf(newRecording) + current.activeGroup.recordings
                    )
                    val updatedGroups = if (updatedGroup != null)
                        current.groups.map { if (it.id == updatedGroup.id) updatedGroup else it }
                    else current.groups

                    _uiState.value = current.copy(
                        recordingState = RecordingState.Idle,
                        statusMessage  = "Transcripción completada",
                        transcription  = result.transcription,
                        activeGroup    = updatedGroup ?: current.activeGroup,
                        groups         = updatedGroups,
                        snackbarMessage  = if (result.pdfUrl != null) "Transcripción lista. PDF disponible." else "Transcripción completada.",
                        snackbarIsSuccess = true
                    )
                    persist()
                }
            } catch (e: IOException) {
                val msg = when {
                    e.message?.contains("Unable to resolve host") == true -> "Error de conexión. Verifica tu conexión."
                    e.message?.contains("timeout") == true                -> "Tiempo de espera agotado."
                    e.message?.contains("404") == true                    -> "Servidor no encontrado. Verifica el backend."
                    else -> e.message ?: "Error desconocido"
                }
                showError(msg)
                _uiState.value = _uiState.value.copy(recordingState = RecordingState.Idle, statusMessage = "Error en la transcripción")
            } catch (e: Exception) {
                showError(e.message ?: "Error inesperado")
                _uiState.value = _uiState.value.copy(recordingState = RecordingState.Idle, statusMessage = "Error inesperado")
            } finally {
                try { file.delete() } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // ─── PDF download & view ──────────────────────────────────────────────────

    fun downloadPdf(recording: Recording) {
        val pdfUrl     = recording.pdfUrl ?: return
        val currentUrl = _uiState.value.serverUrl
        _uiState.value = _uiState.value.copy(downloadingPdfForRecordingId = recording.id)

        viewModelScope.launch {
            try {
                val savedUri = withContext(Dispatchers.IO) { downloadFileToDevice(pdfUrl, recording, currentUrl) }
                _uiState.value = _uiState.value.copy(downloadingPdfForRecordingId = null)
                if (savedUri != null) {
                    updateRecordingPdfPath(recording.id, savedUri)
                    showSuccess("PDF guardado en Descargas/VoiceLog")
                } else {
                    showError("No se pudo descargar el PDF. ¿Está el servidor activo?")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(downloadingPdfForRecordingId = null)
                showError("Error al descargar: ${e.message}")
            }
        }
    }

    /** Opens the already-downloaded PDF (triggers Intent in the UI layer). */
    fun viewPdf(recording: Recording) {
        val path = recording.localPdfPath ?: run {
            showError("Descarga primero el PDF para poder verlo.")
            return
        }
        _uiState.value = _uiState.value.copy(openPdfUri = path)
    }

    fun clearPdfUri() {
        _uiState.value = _uiState.value.copy(openPdfUri = null)
    }

    private fun updateRecordingPdfPath(recordingId: Long, pdfPath: String) {
        fun List<Recording>.withUpdatedPdf() = map { r ->
            if (r.id == recordingId) r.copy(localPdfPath = pdfPath) else r
        }
        val current = _uiState.value
        val updatedGroups = current.groups.map { g ->
            val updated = g.recordings.withUpdatedPdf()
            if (updated != g.recordings) g.copy(recordings = updated) else g
        }
        val updatedActive = current.activeGroup?.let { ag ->
            val updated = ag.recordings.withUpdatedPdf()
            if (updated != ag.recordings) ag.copy(recordings = updated) else ag
        }
        _uiState.value = current.copy(groups = updatedGroups, activeGroup = updatedActive)
        persist()
    }

    // ─── Network helpers ──────────────────────────────────────────────────────

    data class ProcessResult(
        val transcription: String,
        val pdfUrl: String? = null,
        val audioFile: String? = null,
        val transcriptionFile: String? = null
    )

    private suspend fun processAudioFile(file: File, language: String, baseUrl: String): ProcessResult? =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("audio", file.name, file.asRequestBody("audio/mp4".toMediaTypeOrNull()))
                .addFormDataPart("language", language)
                .build()

            val request = Request.Builder().url("$baseUrl/process").post(body).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: "Error desconocido"
                    val msg = when (response.code) {
                        404  -> "Servidor no encontrado. Verifica la URL del backend."
                        500  -> try { JSONObject(err).optString("message", "Error del servidor.") } catch (e: Exception) { "Error 500: $err" }
                        503  -> "Servicio no disponible. Intenta de nuevo."
                        else -> "Error ${response.code}: $err"
                    }
                    throw IOException(msg)
                }
                val json = JSONObject(response.body?.string() ?: throw IOException("Respuesta vacía"))
                if (json.optBoolean("success", false)) {
                    val text = json.optString("transcription").takeIf { it.isNotEmpty() }
                        ?: json.optString("text").takeIf { it.isNotEmpty() }
                        ?: throw IOException("Transcripción vacía en la respuesta")
                    ProcessResult(
                        transcription     = text,
                        pdfUrl            = json.optString("pdfUrl").takeIf { it.isNotEmpty() },
                        audioFile         = json.optString("audioFile").takeIf { it.isNotEmpty() },
                        transcriptionFile = json.optString("transcriptionFile").takeIf { it.isNotEmpty() }
                    )
                } else {
                    throw IOException(json.optString("error", "Error desconocido"))
                }
            }
        }

    // ─── File helpers ─────────────────────────────────────────────────────────

    private fun saveAudioToDevice(sourceFile: File, groupName: String): String? {
        return try {
            val context  = getApplication<Application>()
            val safeName = groupName.replace(Regex("[^a-zA-Z0-9_]"), "_").take(40)
            val timeStr  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "VoiceLog_${safeName}_$timeStr.m4a"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VoiceLog")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { u ->
                    context.contentResolver.openOutputStream(u)?.use { out ->
                        sourceFile.inputStream().use { it.copyTo(out) }
                    }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(u, values, null, null)
                    u.toString()
                }
            } else {
                val dir  = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                dir.mkdirs()
                val dest = File(dir, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
                dest.absolutePath
            }
        } catch (e: Exception) {
            Log.e("MeetingViewModel", "Error guardando audio: ${e.message}"); null
        }
    }

    /** Downloads PDF from server, saves to device, returns a URI string for viewing or null on failure. */
    private fun downloadFileToDevice(pdfUrl: String, recording: Recording, baseUrl: String): String? {
        return try {
            val context  = getApplication<Application>()
            val fileName = pdfUrl.substringAfterLast("/").ifEmpty { "informe_${recording.date}.pdf" }
            val request  = Request.Builder().url("$baseUrl$pdfUrl").build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29+: save via MediaStore, return content URI
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VoiceLog")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return null
                    context.contentResolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    uri.toString()
                } else {
                    // API < 29: save to app-specific external dir, wrap with FileProvider
                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
                    dir.mkdirs()
                    val dest = File(dir, fileName)
                    dest.writeBytes(bytes)
                    val fpUri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", dest
                    )
                    fpUri.toString()
                }
            }
        } catch (e: Exception) {
            Log.e("MeetingViewModel", "Error descargando PDF: ${e.message}"); null
        }
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(net) ?: return false
            cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { mediaRecorder?.release() } catch (e: Exception) { e.printStackTrace() }
        try { mediaPlayer?.release()   } catch (e: Exception) { e.printStackTrace() }
        mediaRecorder = null; mediaPlayer = null
    }
}
