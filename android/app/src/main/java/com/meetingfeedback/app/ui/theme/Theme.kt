package com.meetingfeedback.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val VoiceLogDarkScheme = darkColorScheme(
    primary              = VLGreen,
    onPrimary            = VLBGRoot,
    primaryContainer     = VLGreenBG,
    onPrimaryContainer   = VLGreen,

    secondary            = VLBlue,
    onSecondary          = VLBGRoot,
    secondaryContainer   = Color(0xFF0D1525),
    onSecondaryContainer = VLBlue,

    tertiary             = VLPink,
    onTertiary           = VLBGRoot,
    tertiaryContainer    = Color(0xFF1F0D18),
    onTertiaryContainer  = VLPink,

    error                = VLRed,
    onError              = VLBGRoot,
    errorContainer       = VLRedBG,
    onErrorContainer     = VLRed,

    background           = VLBGRoot,
    onBackground         = VLTextPrimary,

    surface              = VLBGSurface,
    onSurface            = VLTextPrimary,

    surfaceVariant       = VLBGButton,
    onSurfaceVariant     = VLTextSecondary,

    outline              = VLBorderMuted,
    outlineVariant       = VLBorder,

    scrim                = Color(0xCC000000),
)

private val VoiceLogLightScheme = lightColorScheme(
    primary              = VLLightGreen,
    onPrimary            = VLLightSurface,
    primaryContainer     = Color(0xFFF0FDF4),
    onPrimaryContainer   = Color(0xFF14532D),

    secondary            = Color(0xFF2563EB),
    onSecondary          = VLLightSurface,
    secondaryContainer   = Color(0xFFEFF6FF),
    onSecondaryContainer = Color(0xFF1E40AF),

    tertiary             = Color(0xFFDB2777),
    onTertiary           = VLLightSurface,
    tertiaryContainer    = Color(0xFFFDF2F8),
    onTertiaryContainer  = Color(0xFF9D174D),

    error                = Color(0xFFDC2626),
    onError              = VLLightSurface,
    errorContainer       = Color(0xFFFEF2F2),
    onErrorContainer     = Color(0xFF991B1B),

    background           = VLLightBG,
    onBackground         = VLLightText,

    surface              = VLLightSurface,
    onSurface            = VLLightText,

    surfaceVariant       = Color(0xFFEBEBED),
    onSurfaceVariant     = VLLightSubText,

    outline              = Color(0xFFCECED1),
    outlineVariant       = Color(0xFFE0E0E3),

    scrim                = Color(0x80000000),
)

@Composable
fun MeetingFeedbackTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VoiceLogDarkScheme else VoiceLogLightScheme
    val appColors   = if (darkTheme) DarkAppColors     else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = AppTypography,
            content     = content
        )
    }
}
