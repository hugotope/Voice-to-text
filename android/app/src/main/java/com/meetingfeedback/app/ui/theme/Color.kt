package com.meetingfeedback.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── AppColors — holds all semantic UI color tokens ───────────────────────────

data class AppColors(
    val bg: Color,
    val bgCard: Color,
    val bgElevated: Color,
    val bgBtn: Color,
    val bgInput: Color,
    val border: Color,
    val borderMuted: Color,
    val borderDark: Color,
    val green: Color,
    val greenBG: Color,
    val greenBorder: Color,
    val greenDark: Color,
    val red: Color,
    val redBG: Color,
    val redBorder: Color,
    val blue: Color,
    val blueBG: Color,
    val textPrimary: Color,
    val textSub: Color,
    val textMuted: Color,
    val textDimmed: Color,
    val textDark: Color,
    val isDark: Boolean
)

val DarkAppColors = AppColors(
    bg          = Color(0xFF0A0A0B),
    bgCard      = Color(0xFF111113),
    bgElevated  = Color(0xFF141416),
    bgBtn       = Color(0xFF1A1A1C),
    bgInput     = Color(0xFF0E0E10),
    border      = Color(0xFF1E1E21),
    borderMuted = Color(0xFF2A2A2C),
    borderDark  = Color(0xFF222222),
    green       = Color(0xFF4ADE80),
    greenBG     = Color(0xFF0D1F14),
    greenBorder = Color(0xFF2E7D52),
    greenDark   = Color(0xFF1F3A2A),
    red         = Color(0xFFF87171),
    redBG       = Color(0xFF1F0D0D),
    redBorder   = Color(0xFF3D1A1A),
    blue        = Color(0xFF60A5FA),
    blueBG      = Color(0xFF0D1420),
    textPrimary = Color(0xFFF0EDE8),
    textSub     = Color(0xFFAAAAAA),
    textMuted   = Color(0xFF555555),
    textDimmed  = Color(0xFF444444),
    textDark    = Color(0xFF333333),
    isDark      = true
)

val LightAppColors = AppColors(
    bg          = Color(0xFFF5F5F7),
    bgCard      = Color(0xFFFFFFFF),
    bgElevated  = Color(0xFFEBEBED),
    bgBtn       = Color(0xFFE4E4E6),
    bgInput     = Color(0xFFF0F0F2),
    border      = Color(0xFFE0E0E3),
    borderMuted = Color(0xFFCECED1),
    borderDark  = Color(0xFFC8C8CB),
    green       = Color(0xFF16A34A),
    greenBG     = Color(0xFFF0FDF4),
    greenBorder = Color(0xFF86EFAC),
    greenDark   = Color(0xFFDCFCE7),
    red         = Color(0xFFDC2626),
    redBG       = Color(0xFFFEF2F2),
    redBorder   = Color(0xFFFECACA),
    blue        = Color(0xFF2563EB),
    blueBG      = Color(0xFFEFF6FF),
    textPrimary = Color(0xFF111111),
    textSub     = Color(0xFF555555),
    textMuted   = Color(0xFF888888),
    textDimmed  = Color(0xFFAAAAAA),
    textDark    = Color(0xFFCCCCCC),
    isDark      = false
)

val LocalAppColors = compositionLocalOf { DarkAppColors }

// ─── VoiceLog Material3 design tokens (used in Theme.kt schemes) ──────────────

val VLBGRoot          = Color(0xFF0A0A0B)
val VLBGSurface       = Color(0xFF111113)
val VLBGElevated      = Color(0xFF141416)
val VLBGButton        = Color(0xFF1A1A1C)

val VLBorder          = Color(0xFF1E1E21)
val VLBorderMuted     = Color(0xFF2A2A2C)
val VLBorderDark      = Color(0xFF222222)

val VLGreen           = Color(0xFF4ADE80)
val VLGreenBG         = Color(0xFF0D1F14)
val VLGreenBorder     = Color(0xFF2E7D52)
val VLGreenDark       = Color(0xFF1F3A2A)

val VLRed             = Color(0xFFF87171)
val VLRedBG           = Color(0xFF1F0D0D)
val VLRedBorder       = Color(0xFF3D1A1A)

val VLBlue            = Color(0xFF60A5FA)
val VLPink            = Color(0xFFF472B6)

val VLTextPrimary     = Color(0xFFF0EDE8)
val VLTextSecondary   = Color(0xFFAAAAAA)
val VLTextMuted       = Color(0xFF555555)
val VLTextDimmed      = Color(0xFF444444)
val VLTextDark        = Color(0xFF333333)

// Light Material3 tokens
val VLLightGreen      = Color(0xFF16A34A)
val VLLightBG         = Color(0xFFF5F5F7)
val VLLightSurface    = Color(0xFFFFFFFF)
val VLLightText       = Color(0xFF111111)
val VLLightSubText    = Color(0xFF555555)
