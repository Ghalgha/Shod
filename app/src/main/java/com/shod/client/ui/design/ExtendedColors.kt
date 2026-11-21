package com.shod.client.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ShodExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val terminalBackground: Color,
    val terminalText: Color,
    val terminalMuted: Color,
    val terminalError: Color,
    val terminalSuccess: Color,
)

// Success and warning remain semantic colors; informational UI uses the brand blue.
internal val ShodLightExtendedColors = ShodExtendedColors(
    success = Color(0xFF247A50),
    onSuccess = Color.White,
    successContainer = Color(0xFFDDF2E6),
    onSuccessContainer = Color(0xFF123D28),
    warning = Color(0xFF94600C),
    onWarning = Color.White,
    warningContainer = Color(0xFFFFEDC9),
    onWarningContainer = Color(0xFF4A3108),
    info = Color(0xFF315F96),
    onInfo = Color.White,
    infoContainer = Color(0xFFDCE7F3),
    onInfoContainer = Color(0xFF172D47),
    terminalBackground = Color(0xFF101722),
    terminalText = Color(0xFFEAF0F8),
    terminalMuted = Color(0xFFAFBDCE),
    terminalError = Color(0xFFFFB8BE),
    terminalSuccess = Color(0xFF72D5A2),
)

internal val ShodDarkExtendedColors = ShodExtendedColors(
    success = Color(0xFF70D6A2),
    onSuccess = Color(0xFF092819),
    successContainer = Color(0xFF17452F),
    onSuccessContainer = Color(0xFFDDF7E8),
    warning = Color(0xFFF0BE68),
    onWarning = Color(0xFF30200A),
    warningContainer = Color(0xFF503817),
    onWarningContainer = Color(0xFFFFEDC9),
    info = Color(0xFF8FB4E2),
    onInfo = Color(0xFF111923),
    infoContainer = Color(0xFF2B4058),
    onInfoContainer = Color(0xFFE4EDF8),
    terminalBackground = Color(0xFF080D14),
    terminalText = Color(0xFFEAF0F8),
    terminalMuted = Color(0xFFAFBDCE),
    terminalError = Color(0xFFFFB8BE),
    terminalSuccess = Color(0xFF72D5A2),
)

internal val LocalShodExtendedColors = staticCompositionLocalOf { ShodDarkExtendedColors }

object ShodTheme {
    val extendedColors: ShodExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalShodExtendedColors.current
}
