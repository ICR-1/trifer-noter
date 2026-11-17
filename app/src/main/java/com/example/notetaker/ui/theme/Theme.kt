package com.example.notetaker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Warna tambahan di luar skema Material (status rekam, sukses, proses). */
@Immutable
data class ExtraColors(
    val recording: Color,
    val onRecording: Color,
    val recordingContainer: Color,
    val onRecordingContainer: Color,
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val processing: Color,
    val processingContainer: Color,
    val onProcessingContainer: Color,
)

private val LightExtra = ExtraColors(
    recording = TriferRecordingLight,
    onRecording = TriferOnRecordingLight,
    recordingContainer = TriferRecordingContainerLight,
    onRecordingContainer = TriferOnRecordingContainerLight,
    success = TriferSuccessLight,
    successContainer = TriferSuccessContainerLight,
    onSuccessContainer = TriferOnSuccessContainerLight,
    processing = TriferProcessingLight,
    processingContainer = TriferProcessingContainerLight,
    onProcessingContainer = TriferOnProcessingContainerLight,
)

private val DarkExtra = ExtraColors(
    recording = TriferRecordingDark,
    onRecording = TriferOnRecordingDark,
    recordingContainer = TriferRecordingContainerDark,
    onRecordingContainer = TriferOnRecordingContainerDark,
    success = TriferSuccessDark,
    successContainer = TriferSuccessContainerDark,
    onSuccessContainer = TriferOnSuccessContainerDark,
    processing = TriferProcessingDark,
    processingContainer = TriferProcessingContainerDark,
    onProcessingContainer = TriferOnProcessingContainerDark,
)

private val LightColorScheme = lightColorScheme(
    primary = TriferPrimaryLight,
    onPrimary = TriferOnPrimaryLight,
    primaryContainer = TriferPrimaryContainerLight,
    onPrimaryContainer = TriferOnPrimaryContainerLight,
    secondary = TriferSecondaryLight,
    onSecondary = TriferOnSecondaryLight,
    secondaryContainer = TriferSecondaryContainerLight,
    onSecondaryContainer = TriferOnSecondaryContainerLight,
    background = TriferBackgroundLight,
    onBackground = TriferOnBackgroundLight,
    surface = TriferSurfaceLight,
    onSurface = TriferOnSurfaceLight,
    surfaceVariant = TriferSurfaceVariantLight,
    onSurfaceVariant = TriferOnSurfaceVariantLight,
    outline = TriferOutlineLight,
    error = TriferErrorLight,
    onError = TriferOnErrorLight,
    errorContainer = TriferErrorContainerLight,
    onErrorContainer = TriferOnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = TriferPrimaryDark,
    onPrimary = TriferOnPrimaryDark,
    primaryContainer = TriferPrimaryContainerDark,
    onPrimaryContainer = TriferOnPrimaryContainerDark,
    secondary = TriferSecondaryDark,
    onSecondary = TriferOnSecondaryDark,
    secondaryContainer = TriferSecondaryContainerDark,
    onSecondaryContainer = TriferOnSecondaryContainerDark,
    background = TriferBackgroundDark,
    onBackground = TriferOnBackgroundDark,
    surface = TriferSurfaceDark,
    onSurface = TriferOnSurfaceDark,
    surfaceVariant = TriferSurfaceVariantDark,
    onSurfaceVariant = TriferOnSurfaceVariantDark,
    outline = TriferOutlineDark,
    error = TriferErrorDark,
    onError = TriferOnErrorDark,
    errorContainer = TriferErrorContainerDark,
    onErrorContainer = TriferOnErrorContainerDark,
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object TriferTheme {
    val extra: ExtraColors
        @Composable get() = LocalExtraColors.current
}

@Composable
fun TriferNoterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
