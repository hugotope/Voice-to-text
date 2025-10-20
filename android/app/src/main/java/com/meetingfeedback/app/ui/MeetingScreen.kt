package com.meetingfeedback.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meetingfeedback.app.MeetingViewModel
import com.meetingfeedback.app.ui.theme.LocalAppColors
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Mono = FontFamily.Monospace

// ─── Responsive window helpers ────────────────────────────────────────────────

private enum class WindowSize { Compact, Medium, Expanded }

@Composable
private fun rememberWindowSize(): WindowSize {
    val w = LocalConfiguration.current.screenWidthDp
    return when {
        w >= 840 -> WindowSize.Expanded
        w >= 600 -> WindowSize.Medium
        else     -> WindowSize.Compact
    }
}

@Composable
private fun adaptiveMaxWidth(): Dp {
    return when (rememberWindowSize()) {
        WindowSize.Expanded -> 960.dp
        WindowSize.Medium   -> 680.dp
        WindowSize.Compact  -> 480.dp
    }
}

@Composable
private fun adaptiveHPadding(): Dp {
    return when (rememberWindowSize()) {
        WindowSize.Expanded -> 40.dp
        WindowSize.Medium   -> 28.dp
        WindowSize.Compact  -> 20.dp
    }
}

// ─── Root ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingScreen(
    viewModel: MeetingViewModel,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackIsSuccess by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val c = LocalAppColors.current

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackIsSuccess = uiState.snackbarIsSuccess
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearSnackbar()
        }
    }

    // Open PDF viewer when ViewModel sets openPdfUri
    LaunchedEffect(uiState.openPdfUri) {
        uiState.openPdfUri?.let { uriStr ->
            val uri = Uri.parse(uriStr)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            try {
                context.startActivity(Intent.createChooser(intent, "Ver informe con..."))
            } catch (e: Exception) {
                // No hay visor de PDF instalado
            }
            viewModel.clearPdfUri()
        }
    }

    val onToggle: () -> Unit = {
        when (uiState.recordingState) {
            is MeetingViewModel.RecordingState.Recording  -> viewModel.stopRecording()
            is MeetingViewModel.RecordingState.Idle       -> onRequestPermission()
            is MeetingViewModel.RecordingState.Processing -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(c.bg)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(
                targetState = uiState.screen,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "screen"
            ) { screen ->
                when (screen) {
                    MeetingViewModel.Screen.Home        -> HomeScreen(uiState = uiState, viewModel = viewModel)
                    MeetingViewModel.Screen.GroupDetail -> GroupDetailScreen(uiState = uiState, onToggle = onToggle, viewModel = viewModel)
                    MeetingViewModel.Screen.Trash       -> TrashScreen(uiState = uiState, viewModel = viewModel)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier  = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
        ) { data ->
            Snackbar(
                snackbarData   = data,
                containerColor = if (snackIsSuccess) c.greenBG else c.redBG,
                contentColor   = if (snackIsSuccess) c.green   else c.red,
                actionContentColor = if (snackIsSuccess) c.green else c.red
            )
        }

        // ── Modals ──
        if (uiState.showNewGroupModal) {
            NewGroupModal(
                draft          = uiState.groupDraft,
                onDismiss      = viewModel::dismissNewGroupModal,
                onCreate       = viewModel::createGroup,
                onNameChange   = viewModel::updateGroupDraftName,
                onLangChange   = viewModel::updateGroupDraftLanguage,
                onMemberChange = viewModel::updateGroupDraftMember,
                onAddMember    = viewModel::addGroupDraftMember,
                onRemoveMember = viewModel::removeGroupDraftMember
            )
        }

        if (uiState.showSettingsModal) {
            SettingsModal(
                serverUrl  = uiState.serverUrlDraft,
                onUrlChange = viewModel::updateServerUrlDraft,
                onSave     = viewModel::saveServerUrl,
                onDismiss  = viewModel::dismissSettings
            )
        }

        uiState.deleteConfirmId?.let { confirmId ->
            DeleteConfirmDialog(
                confirmId     = confirmId,
                trashedGroups = uiState.trashedGroups,
                onConfirm     = viewModel::confirmDeleteForever,
                onDismiss     = viewModel::cancelDeleteConfirm
            )
        }
    }
}

// ─── Home screen ──────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(uiState: MeetingViewModel.UiState, viewModel: MeetingViewModel) {
    val c = LocalAppColors.current
    val winSize = rememberWindowSize()
    val isLargeTablet = winSize == WindowSize.Expanded

    Column(
        modifier = Modifier
            .widthIn(max = adaptiveMaxWidth()).fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = adaptiveHPadding()).padding(top = 24.dp, bottom = 60.dp)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "VoiceLog, grabador de reuniones con IA"
            }) {
                Text("RECORDER", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 4.sp, color = c.textMuted)
                Text(
                    "VoiceLog",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = if (isLargeTablet) 40.sp else 32.sp,
                    letterSpacing = (-0.5).sp,
                    color = c.textPrimary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Theme toggle
                IconSquareButton(
                    icon  = if (uiState.isDarkTheme) "☀" else "🌙",
                    desc  = if (uiState.isDarkTheme) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                    onClick = viewModel::toggleTheme
                )

                // Settings button
                IconSquareButton(icon = "⚙", desc = "Configuración del servidor", onClick = viewModel::showSettings)

                // Trash button with badge
                Box(modifier = Modifier.size(34.dp)) {
                    IconSquareButton(icon = "🗑", desc = "Papelera", onClick = viewModel::openTrash)
                    if (uiState.trashedGroups.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(c.red),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${uiState.trashedGroups.size}",
                                color = c.bg, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = Mono
                            )
                        }
                    }
                }

                // New button
                OutlinedButton(
                    onClick = viewModel::showNewGroupModal,
                    modifier = Modifier.height(34.dp).semantics { contentDescription = "Crear nuevo grupo" },
                    border = BorderStroke(1.dp, c.greenBorder),
                    shape  = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = c.green)
                ) {
                    Text("+ NUEVO", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = c.green)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        VLSectionLabel("GRUPOS (${uiState.groups.size})")
        Spacer(Modifier.height(12.dp))

        if (uiState.groups.isEmpty()) {
            EmptyGroupsState(onNewGroup = viewModel::showNewGroupModal)
        } else if (isLargeTablet) {
            // 2-column grid for large tablets
            uiState.groups.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { group ->
                        Box(modifier = Modifier.weight(1f)) {
                            GroupCard(
                                group   = group,
                                onClick = { viewModel.openGroup(group) },
                                onTrash = { viewModel.moveToTrash(group) }
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.groups.forEach { group ->
                    GroupCard(
                        group   = group,
                        onClick = { viewModel.openGroup(group) },
                        onTrash = { viewModel.moveToTrash(group) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGroupsState(onNewGroup: () -> Unit) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth().padding(vertical = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Sin grupos aún. Toca + NUEVO para crear el primero."
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("🎙", fontSize = 48.sp)
        Text("Sin grupos aún", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = c.textSub)
        Text(
            "Crea tu primer grupo\npara empezar a grabar.",
            fontFamily = Mono, fontSize = 11.sp, color = c.textMuted, textAlign = TextAlign.Center, lineHeight = 18.sp
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp)).background(c.greenBG)
                .border(1.dp, c.greenBorder, RoundedCornerShape(10.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onNewGroup)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics { role = Role.Button; contentDescription = "Crear nuevo grupo" }
        ) {
            Text("+ Crear grupo", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.green)
        }
    }
}

@Composable
private fun GroupCard(
    group: MeetingViewModel.Group,
    onClick: () -> Unit,
    onTrash: () -> Unit
) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)).background(c.bgCard)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(14.dp)
            .semantics { role = Role.Button; contentDescription = "${group.name}, Grupo, ${group.members.size} miembros, ${group.recordings.size} grabaciones" }
    ) {
        Column(modifier = Modifier.padding(end = 56.dp)) {
            GroupBadge()
            Spacer(Modifier.height(8.dp))
            Text(text = group.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.textPrimary, letterSpacing = (-0.2).sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("👤 ${group.members.size}", fontFamily = Mono, fontSize = 11.sp, color = c.textMuted)
                Text("🎙 ${group.recordings.size}", fontFamily = Mono, fontSize = 11.sp, color = c.textMuted)
                MeetingViewModel.LANGUAGES.find { it.code == group.language }?.let { l ->
                    Text("${l.flag} ${l.code.uppercase()}", fontFamily = Mono, fontSize = 11.sp, color = c.textMuted)
                }
            }
        }
        Row(modifier = Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTrash)
                    .semantics { role = Role.Button; contentDescription = "Mover a papelera" },
                contentAlignment = Alignment.Center
            ) { Text("🗑", fontSize = 15.sp) }
            Text("→", color = c.textDark, fontSize = 16.sp)
        }
    }
}

// ─── Group detail screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupDetailScreen(
    uiState: MeetingViewModel.UiState,
    onToggle: () -> Unit,
    viewModel: MeetingViewModel
) {
    val c = LocalAppColors.current
    val winSize = rememberWindowSize()
    val isLargeTablet = winSize == WindowSize.Expanded
    val group = uiState.activeGroup ?: return
    val isRecording  = uiState.recordingState is MeetingViewModel.RecordingState.Recording
    val isProcessing = uiState.recordingState is MeetingViewModel.RecordingState.Processing
    val lang = MeetingViewModel.LANGUAGES.find { it.code == group.language }

    Column(
        modifier = Modifier
            .widthIn(max = adaptiveMaxWidth()).fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = adaptiveHPadding()).padding(top = 24.dp, bottom = 60.dp)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "← Volver", color = c.textSub, fontFamily = Mono, fontSize = 13.sp,
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = viewModel::goHome)
                    .semantics { role = Role.Button; contentDescription = "Volver a inicio" }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GroupBadge()
                Box(
                    modifier = Modifier
                        .size(32.dp).clip(RoundedCornerShape(8.dp))
                        .background(c.redBG.copy(alpha = 0.4f))
                        .border(1.dp, c.redBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { viewModel.moveToTrash(group) })
                        .semantics { role = Role.Button; contentDescription = "Mover grupo a papelera" },
                    contentAlignment = Alignment.Center
                ) { Text("🗑", fontSize = 14.sp) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = group.name,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (isLargeTablet) 34.sp else 28.sp,
            color = c.textPrimary,
            letterSpacing = (-0.5).sp,
            lineHeight = 36.sp
        )

        if (lang != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.bgElevated)
                    .border(1.dp, c.borderDark, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = lang.flag, fontSize = 11.sp)
                Text(text = lang.label, fontFamily = Mono, fontSize = 10.sp, color = c.textSub)
            }
        }

        Spacer(Modifier.height(24.dp))

        if (isLargeTablet) {
            // Two-column layout for large tablets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left column: members + record button
                Column(modifier = Modifier.weight(1f)) {
                    VLSectionLabel("INTEGRANTES")
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.members.forEach { MemberChip(name = it) }
                    }

                    Spacer(Modifier.height(24.dp))
                    VLSectionLabel("NUEVA GRABACIÓN")
                    Spacer(Modifier.height(12.dp))
                    RecordButton(isRecording = isRecording, isProcessing = isProcessing, lang = lang, onToggle = onToggle)
                }

                // Right column: recordings list + transcription
                Column(modifier = Modifier.weight(1f)) {
                    VLSectionLabel("GRABACIONES (${group.recordings.size})")
                    Spacer(Modifier.height(12.dp))
                    RecordingsList(
                        recordings = group.recordings,
                        downloadingPdfId = uiState.downloadingPdfForRecordingId,
                        playingRecordingId = uiState.playingRecordingId,
                        isAudioPlaying = uiState.isAudioPlaying,
                        viewModel = viewModel
                    )

                    if (uiState.transcription.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        VLSectionLabel("TRANSCRIPCIÓN")
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgCard)
                                .border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(16.dp)
                                .semantics { contentDescription = "Texto de la transcripción" }
                        ) {
                            SelectionContainer {
                                Text(text = uiState.transcription, fontSize = 13.sp, color = c.textSub, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Single-column layout for phones and medium tablets
            VLSectionLabel("INTEGRANTES")
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.members.forEach { MemberChip(name = it) }
            }

            Spacer(Modifier.height(24.dp))
            VLSectionLabel("NUEVA GRABACIÓN")
            Spacer(Modifier.height(12.dp))
            RecordButton(isRecording = isRecording, isProcessing = isProcessing, lang = lang, onToggle = onToggle)

            Spacer(Modifier.height(24.dp))
            VLSectionLabel("GRABACIONES (${group.recordings.size})")
            Spacer(Modifier.height(12.dp))
            RecordingsList(
                recordings = group.recordings,
                downloadingPdfId = uiState.downloadingPdfForRecordingId,
                playingRecordingId = uiState.playingRecordingId,
                isAudioPlaying = uiState.isAudioPlaying,
                viewModel = viewModel
            )

            AnimatedVisibility(visible = uiState.transcription.isNotEmpty(), enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(24.dp))
                    VLSectionLabel("TRANSCRIPCIÓN")
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgCard)
                            .border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(16.dp)
                            .semantics { contentDescription = "Texto de la transcripción" }
                    ) {
                        SelectionContainer {
                            Text(text = uiState.transcription, fontSize = 13.sp, color = c.textSub, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    lang: MeetingViewModel.Language?,
    onToggle: () -> Unit
) {
    val c = LocalAppColors.current
    val btnBG     = when { isProcessing -> c.blueBG;  isRecording -> c.redBG;    else -> c.greenBG }
    val btnBorder = when { isProcessing -> c.blue.copy(alpha = 0.4f); isRecording -> c.redBorder; else -> c.greenBorder }
    val btnColor  = when { isProcessing -> c.blue;    isRecording -> c.red;       else -> c.green }
    val btnLabel  = when { isProcessing -> "PROCESANDO..."; isRecording -> "DETENER GRABACIÓN"; else -> "INICIAR GRABACIÓN" }

    Row(
        modifier = Modifier
            .fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(btnBG)
            .border(1.dp, btnBorder, RoundedCornerShape(14.dp))
            .then(if (!isProcessing) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle) else Modifier)
            .padding(16.dp).semantics { role = Role.Button; contentDescription = btnLabel },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(10.dp), color = c.blue, strokeWidth = 2.dp)
        else Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(btnColor))
        Spacer(Modifier.width(10.dp))
        Text(text = btnLabel, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, color = btnColor)
    }

    AnimatedVisibility(visible = isRecording, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by transition.animateFloat(
                initialValue = 1f, targetValue = 0.2f,
                animationSpec = infiniteRepeatable(animation = tween(600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                label = "pulseAlpha"
            )
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(c.red.copy(alpha = pulseAlpha)))
            Text(text = if (lang != null) "Grabando · ${lang.flag} ${lang.label}" else "Grabando...", fontFamily = Mono, fontSize = 11.sp, color = c.textSub)
        }
    }
}

@Composable
private fun RecordingsList(
    recordings: List<MeetingViewModel.Recording>,
    downloadingPdfId: Long?,
    playingRecordingId: Long?,
    isAudioPlaying: Boolean,
    viewModel: MeetingViewModel
) {
    val c = LocalAppColors.current
    if (recordings.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(vertical = 24.dp)
                .semantics { contentDescription = "Sin grabaciones aún" },
            contentAlignment = Alignment.Center
        ) {
            Text("Sin grabaciones aún", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.5.sp, color = c.textDark)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recordings.forEach { rec ->
                RecordingCard(
                    rec                = rec,
                    isDownloading      = downloadingPdfId == rec.id,
                    playingRecordingId = playingRecordingId,
                    isAudioPlaying     = isAudioPlaying,
                    onDownloadPdf      = { viewModel.downloadPdf(rec) },
                    onViewPdf          = { viewModel.viewPdf(rec) },
                    onPlay             = { viewModel.playRecording(rec) }
                )
            }
        }
    }
}

// ─── Trash screen ─────────────────────────────────────────────────────────────

@Composable
private fun TrashScreen(uiState: MeetingViewModel.UiState, viewModel: MeetingViewModel) {
    val c = LocalAppColors.current
    val winSize = rememberWindowSize()
    val isLargeTablet = winSize == WindowSize.Expanded

    Column(
        modifier = Modifier
            .widthIn(max = adaptiveMaxWidth()).fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = adaptiveHPadding()).padding(top = 24.dp, bottom = 60.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "← Volver", color = c.textSub, fontFamily = Mono, fontSize = 13.sp,
                modifier = Modifier
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = viewModel::goHome)
                    .semantics { role = Role.Button; contentDescription = "Volver a inicio" }
            )
            Text("PAPELERA", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 4.sp, color = c.textMuted)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "🗑 Papelera",
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (isLargeTablet) 34.sp else 28.sp,
            color = c.red,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(24.dp))

        if (uiState.trashedGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("La papelera está vacía", fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.5.sp, color = c.textDark)
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .border(1.dp, c.redBorder, RoundedCornerShape(10.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = viewModel::requestEmptyTrash)
                    .padding(10.dp).semantics { role = Role.Button; contentDescription = "Vaciar papelera" },
                contentAlignment = Alignment.Center
            ) {
                Text("Vaciar papelera (${uiState.trashedGroups.size})", fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp, color = c.red)
            }

            Spacer(Modifier.height(14.dp))

            if (isLargeTablet) {
                uiState.trashedGroups.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { group ->
                            Box(modifier = Modifier.weight(1f)) {
                                TrashedGroupCard(
                                    group           = group,
                                    onRestore       = { viewModel.restoreFromTrash(group) },
                                    onDeleteForever = { viewModel.requestDeleteForever(group.id) }
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.trashedGroups.forEach { group ->
                        TrashedGroupCard(
                            group           = group,
                            onRestore       = { viewModel.restoreFromTrash(group) },
                            onDeleteForever = { viewModel.requestDeleteForever(group.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashedGroupCard(
    group: MeetingViewModel.Group,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val c = LocalAppColors.current
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bgCard)
            .border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(14.dp)
    ) {
        Row(
            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                .background(c.green.copy(alpha = 0.06f))
                .border(1.dp, c.green.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("👥", fontSize = 10.sp)
            Text("GRUPO", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, color = c.green.copy(alpha = 0.4f))
        }
        Spacer(Modifier.height(8.dp))
        Text(text = group.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.textPrimary.copy(alpha = 0.45f), letterSpacing = (-0.2).sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("👤 ${group.members.size}", fontFamily = Mono, fontSize = 11.sp, color = c.textDark)
            Text("🎙 ${group.recordings.size}", fontFamily = Mono, fontSize = 11.sp, color = c.textDark)
        }
        group.trashedAt?.let { ts ->
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))
            Spacer(Modifier.height(4.dp))
            Text("Eliminado: $dateStr", fontFamily = Mono, fontSize = 10.sp, color = c.textDark)
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(c.greenBG)
                    .border(1.dp, c.greenBorder, RoundedCornerShape(8.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRestore)
                    .padding(8.dp).semantics { role = Role.Button; contentDescription = "Restaurar grupo" },
                contentAlignment = Alignment.Center
            ) { Text("↩ Restaurar", fontFamily = Mono, fontSize = 11.sp, color = c.green) }

            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(c.redBG)
                    .border(1.dp, c.redBorder, RoundedCornerShape(8.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDeleteForever)
                    .padding(8.dp).semantics { role = Role.Button; contentDescription = "Borrar definitivamente" },
                contentAlignment = Alignment.Center
            ) { Text("🗑 Borrar def.", fontFamily = Mono, fontSize = 11.sp, color = c.red) }
        }
    }
}

// ─── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun GroupBadge() {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(c.green.copy(alpha = 0.13f))
            .border(1.dp, c.green.copy(alpha = 0.26f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("👥", fontSize = 10.sp)
        Text("GRUPO", fontFamily = Mono, fontSize = 9.sp, letterSpacing = 1.sp, color = c.green)
    }
}

@Composable
private fun MemberChip(name: String) {
    val c = LocalAppColors.current
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.bgElevated)
            .border(1.dp, c.border, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics { contentDescription = name },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(c.greenDark).border(1.dp, c.greenBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?", fontFamily = Mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.green)
        }
        Text(text = name, fontSize = 11.sp, color = c.textSub)
    }
}

@Composable
private fun RecordingCard(
    rec: MeetingViewModel.Recording,
    isDownloading: Boolean,
    playingRecordingId: Long?,
    isAudioPlaying: Boolean,
    onDownloadPdf: () -> Unit,
    onViewPdf: () -> Unit,
    onPlay: () -> Unit
) {
    val c = LocalAppColors.current
    val lang          = MeetingViewModel.LANGUAGES.find { it.code == rec.languageCode }
    val isThisPlaying = playingRecordingId == rec.id
    val canPlay       = rec.localAudioPath != null
    val hasPdfLocal   = rec.localPdfPath != null
    val playIcon      = if (isThisPlaying && isAudioPlaying) "⏸" else "▶"
    val playColor     = when { isThisPlaying -> c.green; canPlay -> c.textSub; else -> c.textDark }
    val playBG        = if (isThisPlaying) c.greenBG else c.bgBtn
    val playBorder    = if (isThisPlaying) c.greenBorder else c.borderMuted

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.bgCard)
            .border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(12.dp)
            .semantics(mergeDescendants = false) { contentDescription = "${rec.title}, ${rec.date}, duración ${rec.duration}" }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.bgBtn), contentAlignment = Alignment.Center) {
                Text("🎙", fontSize = 16.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = rec.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(rec.date, fontFamily = Mono, fontSize = 10.sp, color = c.textDimmed)
                    Text("⏱ ${rec.duration}", fontFamily = Mono, fontSize = 10.sp, color = c.textDimmed)
                    if (lang != null) Text("${lang.flag} ${rec.languageCode.uppercase()}", fontFamily = Mono, fontSize = 10.sp, color = c.textDimmed)
                }
            }

            Box(
                modifier = Modifier
                    .size(34.dp).clip(CircleShape).background(playBG).border(1.dp, playBorder, CircleShape)
                    .then(if (canPlay) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPlay) else Modifier)
                    .semantics {
                        role = Role.Button
                        contentDescription = when { !canPlay -> "Sin audio guardado"; isThisPlaying && isAudioPlaying -> "Pausar"; else -> "Reproducir" }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = playIcon, color = playColor, fontSize = 11.sp)
            }
        }

        if (rec.pdfUrl != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasPdfLocal) {
                    Box(
                        modifier = Modifier
                            .weight(1f).clip(RoundedCornerShape(8.dp))
                            .background(c.blueBG)
                            .border(1.dp, c.blue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onViewPdf)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .semantics { role = Role.Button; contentDescription = "Ver informe PDF" },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text("📖", fontSize = 11.sp)
                            Spacer(Modifier.width(5.dp))
                            Text("Ver informe", fontFamily = Mono, fontSize = 10.sp, letterSpacing = 0.5.sp, color = c.blue)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(if (hasPdfLocal) 1f else 2f)
                        .clip(RoundedCornerShape(8.dp)).background(c.greenBG)
                        .border(1.dp, if (isDownloading) c.borderDark else c.greenBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .then(if (!isDownloading) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDownloadPdf) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .semantics { role = Role.Button; contentDescription = if (isDownloading) "Descargando PDF" else if (hasPdfLocal) "Re-descargar PDF" else "Descargar PDF" },
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(10.dp), color = c.green.copy(alpha = 0.7f), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Descargando...", fontFamily = Mono, fontSize = 10.sp, color = c.textMuted)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (hasPdfLocal) "⬇" else "📄", fontSize = 11.sp)
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (hasPdfLocal) "Re-descargar" else "Descargar PDF",
                                fontFamily = Mono, fontSize = 10.sp, letterSpacing = 0.5.sp, color = c.green.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VLSectionLabel(text: String) {
    val c = LocalAppColors.current
    Text(text = text, fontFamily = Mono, fontSize = 9.sp, letterSpacing = 3.sp, color = c.textDimmed)
}

@Composable
private fun IconSquareButton(icon: String, desc: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.bgBtn)
            .border(1.dp, c.borderMuted, RoundedCornerShape(10.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = desc },
        contentAlignment = Alignment.Center
    ) { Text(icon, fontSize = 15.sp) }
}

// ─── New group modal ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewGroupModal(
    draft: MeetingViewModel.GroupDraft,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
    onNameChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onMemberChange: (Int, String) -> Unit,
    onAddMember: () -> Unit,
    onRemoveMember: (Int) -> Unit
) {
    val c = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = c.bgCard,
        dragHandle       = { ModalDragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp).padding(bottom = 40.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("NUEVO GRUPO", fontFamily = Mono, fontSize = 10.sp, letterSpacing = 4.sp, color = c.textDimmed)
            Spacer(Modifier.height(20.dp))

            VLFormLabel("NOMBRE DEL GRUPO")
            Spacer(Modifier.height(8.dp))
            VLTextField(value = draft.name, onValueChange = onNameChange, placeholder = "ej. Equipo de Diseño...", contentDesc = "Nombre del grupo")

            Spacer(Modifier.height(18.dp))

            VLFormLabel("IDIOMA DE TRANSCRIPCIÓN")
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MeetingViewModel.LANGUAGES.forEach { l ->
                    val active = l.code == draft.language
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (active) c.greenBG else c.bgInput)
                            .border(1.dp, if (active) c.greenBorder else c.borderDark, RoundedCornerShape(8.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onLangChange(l.code) })
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .semantics { role = Role.Button; contentDescription = "${l.label}${if (active) ", seleccionado" else ""}" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = l.flag, fontSize = 14.sp)
                        Text(text = l.code.uppercase(), fontFamily = Mono, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) c.green else c.textSub)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            VLFormLabel("INTEGRANTES")
            Spacer(Modifier.height(8.dp))
            draft.members.forEachIndexed { i, m ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        VLTextField(value = m, onValueChange = { onMemberChange(i, it) }, placeholder = "Integrante ${i + 1}", contentDesc = "Nombre de integrante ${i + 1}")
                    }
                    Box(
                        modifier = Modifier.size(36.dp)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { onRemoveMember(i) })
                            .semantics { role = Role.Button; contentDescription = "Eliminar integrante ${i + 1}" },
                        contentAlignment = Alignment.Center
                    ) { Text("✕", color = c.textDimmed, fontSize = 12.sp) }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .border(1.dp, c.borderDark, RoundedCornerShape(10.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onAddMember)
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .semantics { role = Role.Button; contentDescription = "Añadir integrante" },
                contentAlignment = Alignment.Center
            ) { Text("+ Añadir integrante", fontFamily = Mono, fontSize = 11.sp, color = c.textDimmed) }

            Spacer(Modifier.height(20.dp))
            ModalActions(onDismiss = onDismiss, onCreate = onCreate, createLabel = "Crear →")
        }
    }
}

// ─── Settings modal ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsModal(
    serverUrl: String,
    onUrlChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.bgCard, dragHandle = { ModalDragHandle() }) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
            Text("CONFIGURACIÓN", fontFamily = Mono, fontSize = 10.sp, letterSpacing = 4.sp, color = c.textDimmed)
            Spacer(Modifier.height(20.dp))

            VLFormLabel("URL DEL SERVIDOR")
            Spacer(Modifier.height(8.dp))
            VLTextField(value = serverUrl, onValueChange = onUrlChange, placeholder = "http://192.168.1.X:3000", contentDesc = "URL del servidor backend")
            Spacer(Modifier.height(6.dp))
            Text(
                text       = "Si usas la app en el móvil, pon la IP local del PC donde corre el servidor Python.",
                fontFamily = Mono, fontSize = 10.sp, color = c.textDark, lineHeight = 16.sp
            )

            Spacer(Modifier.height(20.dp))
            ModalActions(onDismiss = onDismiss, onCreate = onSave, createLabel = "Guardar →")
        }
    }
}

// ─── Delete confirm dialog ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmDialog(
    confirmId: Long,
    trashedGroups: List<MeetingViewModel.Group>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalAppColors.current
    val isEmptyAll  = confirmId == -1L
    val groupName   = if (!isEmptyAll) trashedGroups.find { it.id == confirmId }?.name ?: "este grupo" else null
    val message     = if (isEmptyAll)
        "¿Borrar definitivamente todos los elementos de la papelera? Esta acción no se puede deshacer."
    else
        "¿Borrar definitivamente \"$groupName\"? Esta acción no se puede deshacer."

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.bgCard, dragHandle = { ModalDragHandle() }) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
            Text("⚠ CONFIRMAR BORRADO", fontFamily = Mono, fontSize = 10.sp, letterSpacing = 3.sp, color = c.red.copy(alpha = 0.7f))
            Spacer(Modifier.height(12.dp))
            Text(text = message, fontSize = 13.sp, color = c.textSub, lineHeight = 20.sp)
            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .border(1.dp, c.borderDark, RoundedCornerShape(10.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                        .padding(12.dp).semantics { role = Role.Button; contentDescription = "Cancelar" },
                    contentAlignment = Alignment.Center
                ) { Text("Cancelar", fontFamily = Mono, fontSize = 12.sp, color = c.textMuted) }

                Box(
                    modifier = Modifier.weight(2f).clip(RoundedCornerShape(10.dp)).background(c.redBG)
                        .border(1.dp, c.redBorder, RoundedCornerShape(10.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onConfirm)
                        .padding(12.dp).semantics { role = Role.Button; contentDescription = "Borrar definitivamente" },
                    contentAlignment = Alignment.Center
                ) { Text("Borrar definitivamente", fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.red) }
            }
        }
    }
}

// ─── Small helpers ────────────────────────────────────────────────────────────

@Composable
private fun ModalDragHandle() {
    val c = LocalAppColors.current
    Box(
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).width(36.dp).height(4.dp)
            .clip(CircleShape).background(c.borderMuted)
    )
}

@Composable
private fun ModalActions(onDismiss: () -> Unit, onCreate: () -> Unit, createLabel: String) {
    val c = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                .border(1.dp, c.borderDark, RoundedCornerShape(10.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .padding(12.dp).semantics { role = Role.Button; contentDescription = "Cancelar" },
            contentAlignment = Alignment.Center
        ) { Text("Cancelar", fontFamily = Mono, fontSize = 12.sp, color = c.textMuted) }

        Box(
            modifier = Modifier.weight(2f).clip(RoundedCornerShape(10.dp)).background(c.greenBG)
                .border(1.dp, c.greenBorder, RoundedCornerShape(10.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCreate)
                .padding(12.dp).semantics { role = Role.Button; contentDescription = createLabel },
            contentAlignment = Alignment.Center
        ) { Text(createLabel, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = c.green) }
    }
}

@Composable
private fun VLFormLabel(text: String) {
    val c = LocalAppColors.current
    Text(text = text, fontFamily = Mono, fontSize = 9.sp, letterSpacing = 3.sp, color = c.textDimmed)
}

@Composable
private fun VLTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    contentDesc: String = placeholder
) {
    val c = LocalAppColors.current
    BasicTextField(
        value         = value,
        onValueChange = onValueChange,
        textStyle     = TextStyle(color = c.textPrimary, fontFamily = Mono, fontSize = 13.sp),
        cursorBrush   = SolidColor(c.green),
        modifier      = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)).background(c.bgInput)
            .border(1.dp, c.borderDark, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { this.contentDescription = contentDesc },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(text = placeholder, color = c.textMuted, fontFamily = Mono, fontSize = 13.sp)
                inner()
            }
        }
    )
}
