package com.meetingfeedback.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.meetingfeedback.app.ui.MeetingScreen
import com.meetingfeedback.app.ui.theme.MeetingFeedbackTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MeetingViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(
                this,
                getString(R.string.permission_audio_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MeetingFeedbackTheme(darkTheme = uiState.isDarkTheme) {
                MeetingScreen(
                    viewModel = viewModel,
                    onRequestPermission = ::checkPermissionAndRecord
                )
            }
        }
    }

    private fun checkPermissionAndRecord() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> viewModel.startRecording()

            else -> requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onPause() {
        super.onPause()
        // Stops recording automatically when the app goes to background
        viewModel.stopRecordingIfActive()
    }
}
