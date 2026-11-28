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

// Extended semantic colors stay within the same graphite, gray, and muted-teal palette.
internal val ShodLightExtendedColors = ShodExtendedColors(
    success = Color(0xFF557F7D),
    onSuccess = Color.White,
    successContainer = Color(0xFFD8E5E3),
    onSuccessContainer = Color(0xFF203332),
    warning = Color(0xFF686D6E),
    onWarning = Color.White,
    warningContainer = Color(0xFFE2E4E3),
    onWarningContainer = Color(0xFF292C2D),
    info = Color(0xFF686D6E),
    onInfo = Color.White,
    infoContainer = Color(0xFFE2E4E3),
    onInfoContainer = Color(0xFF292C2D),
    terminalBackground = Color(0xFF252728),
    terminalText = Color(0xFFE7E8E8),
    terminalMuted = Color(0xFFA9ADAE),
    terminalError = Color(0xFFB8AAAA),
    terminalSuccess = Color(0xFF557F7D),
)

internal val ShodDarkExtendedColors = ShodExtendedColors(
    success = Color(0xFF7FB6B3),
    onSuccess = Color(0xFF1E2021),
    successContainer = Color(0xFF35494A),
    onSuccessContainer = Color(0xFFE0EEEC),
    warning = Color(0xFFA9ADAE),
    onWarning = Color(0xFF1E2021),
    warningContainer = Color(0xFF343738),
    onWarningContainer = Color(0xFFE2E4E4),
    info = Color(0xFFA9ADAE),
    onInfo = Color(0xFF1E2021),
    infoContainer = Color(0xFF343738),
    onInfoContainer = Color(0xFFE2E4E4),
    terminalBackground = Color(0xFF191B1C),
    terminalText = Color(0xFFE7E8E8),
    terminalMuted = Color(0xFFA9ADAE),
    terminalError = Color(0xFFC5B9B9),
    terminalSuccess = Color(0xFF7FB6B3),
)

internal val LocalShodExtendedColors = staticCompositionLocalOf { ShodDarkExtendedColors }

object ShodTheme {
    val extendedColors: ShodExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalShodExtendedColors.current
}
