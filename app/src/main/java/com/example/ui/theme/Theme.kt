package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.quizapp.data.model.AppColorTheme

// Strictly Dark Scheme with Natural Emerald / Forest Green Accents
private val DarkGreenScheme = darkColorScheme(
    primary = AppPrimaryGreen,
    onPrimary = AppOnPrimaryGreen,
    primaryContainer = AppPrimaryContainerGreen,
    onPrimaryContainer = AppOnPrimaryContainerGreen,
    secondary = AppSecondaryGreen,
    onSecondary = AppOnPrimaryGreen,
    secondaryContainer = AppSecondaryContainerGreen,
    onSecondaryContainer = AppOnSecondaryContainerGreen,
    tertiary = Color(0xFF86EFAC),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFDCFCE7),
    background = AppBackgroundDark,
    onBackground = AppTextPrimary,
    surface = AppSurfaceDark,
    onSurface = AppTextPrimary,
    surfaceVariant = AppSurfaceContainerHighDark,
    onSurfaceVariant = AppTextSecondary,
    surfaceContainerLowest = AppBackgroundDark,
    surfaceContainerLow = AppSurfaceContainerLowDark,
    surfaceContainer = AppSurfaceContainerDark,
    surfaceContainerHigh = AppSurfaceContainerHighDark,
    surfaceContainerHighest = AppSurfaceContainerHighestDark,
    outline = AppOutlineDark,
    outlineVariant = AppOutlineVariantDark
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    colorTheme: AppColorTheme = AppColorTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkGreenScheme,
        typography = Typography,
        content = content
    )
}
