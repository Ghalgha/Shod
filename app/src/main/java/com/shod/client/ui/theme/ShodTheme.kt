package com.shod.client.ui.theme

import com.shod.client.R

import android.os.Build
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.shod.client.ShodConstants
import com.shod.client.ui.design.ShodDarkExtendedColors
import com.shod.client.ui.design.ShodLightExtendedColors
import com.shod.client.ui.design.ShodMaterialShapes
import com.shod.client.ui.design.LocalShodExtendedColors

private fun variableFont(resourceId: Int, weight: FontWeight, axisWeight: Int): Font =
    Font(
        resId = resourceId,
        weight = weight,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(axisWeight),
        ),
    )

private val ManropeFontFamily = FontFamily(
    variableFont(R.font.manrope_variable, FontWeight.Normal, 400),
    variableFont(R.font.manrope_variable, FontWeight.Medium, 500),
    variableFont(R.font.manrope_variable, FontWeight.SemiBold, 600),
    variableFont(R.font.manrope_variable, FontWeight.Bold, 700),
    variableFont(R.font.manrope_variable, FontWeight.ExtraBold, 800),
)

val ShodTypography = Typography(
    displayLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = ManropeFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)

// Neutral slate surfaces with one restrained blue brand accent.
internal val BrandLightColorScheme = lightColorScheme(
    primary = Color(0xFF315F96),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE7F3),
    onPrimaryContainer = Color(0xFF172D47),
    secondary = Color(0xFF586779),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EDF3),
    onSecondaryContainer = Color(0xFF263444),
    tertiary = Color(0xFF586779),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8EDF3),
    onTertiaryContainer = Color(0xFF263444),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF17212D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17212D),
    surfaceVariant = Color(0xFFE8EDF3),
    onSurfaceVariant = Color(0xFF586779),
    outline = Color(0xFF7C8999),
    outlineVariant = Color(0xFFD7DEE7),
    error = Color(0xFFB3263A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DDE1),
    onErrorContainer = Color(0xFF42151C),
    surfaceTint = Color.Transparent,
)

internal val BrandDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FB4E2),
    onPrimary = Color(0xFF111923),
    primaryContainer = Color(0xFF2B4058),
    onPrimaryContainer = Color(0xFFE4EDF8),
    secondary = Color(0xFFA9BBD1),
    onSecondary = Color(0xFF172333),
    secondaryContainer = Color(0xFF2A3544),
    onSecondaryContainer = Color(0xFFE5EBF4),
    tertiary = Color(0xFFA9BBD1),
    onTertiary = Color(0xFF172333),
    tertiaryContainer = Color(0xFF2A3544),
    onTertiaryContainer = Color(0xFFE5EBF4),
    background = Color(0xFF111923),
    onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF202B38),
    onSurface = Color(0xFFE8EDF3),
    surfaceVariant = Color(0xFF263444),
    onSurfaceVariant = Color(0xFFA9BBD1),
    outline = Color(0xFF8494A8),
    outlineVariant = Color(0xFF394657),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF321116),
    errorContainer = Color(0xFF592630),
    onErrorContainer = Color(0xFFFFE1E4),
    surfaceTint = Color.Transparent,
)

private fun getAppColorScheme(isDark: Boolean): androidx.compose.material3.ColorScheme =
    if (isDark) BrandDarkColorScheme else BrandLightColorScheme

@Composable
fun ShodTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ShodConstants.Theme.MODE_DARK -> true
        ShodConstants.Theme.MODE_LIGHT -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = remember(darkTheme) {
        getAppColorScheme(darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        SideEffect {
            val window = (view.context as Activity).window
            val navigationBarColor = if (darkTheme) {
                Color.Transparent
            } else {
                lerp(colorScheme.background, colorScheme.surface, 0.55f)
            }
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = navigationBarColor.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalShodExtendedColors provides if (darkTheme) ShodDarkExtendedColors else ShodLightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ShodTypography,
            shapes = ShodMaterialShapes,
            content = content,
        )
    }
}
