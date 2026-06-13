package com.example.andbook.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.andbook.data.ReaderTheme

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

private val CoffeeLightColorScheme = lightColorScheme(
    primary = CoffeeLightPrimary,
    secondary = CoffeeLightSecondary,
    tertiary = CoffeeLightTertiary,
    background = CoffeeLightBackground,
    surface = CoffeeLightSurface,
    onPrimary = CoffeeLightOnPrimary,
    onBackground = CoffeeLightOnBackground,
    onSurface = CoffeeLightOnSurface,
    surfaceVariant = CoffeeLightSurface,
    outline = CoffeeLightBorder
)

private val CoffeeDarkColorScheme = darkColorScheme(
    primary = CoffeeDarkPrimary,
    secondary = CoffeeDarkSecondary,
    tertiary = CoffeeDarkTertiary,
    background = CoffeeDarkBackground,
    surface = CoffeeDarkSurface,
    onPrimary = CoffeeDarkOnPrimary,
    onBackground = CoffeeDarkOnBackground,
    onSurface = CoffeeDarkOnSurface,
    surfaceVariant = CoffeeDarkSurface,
    outline = CoffeeDarkBorder
)

private val AmoledColorScheme = darkColorScheme(
    primary = AmoledPrimary,
    secondary = AmoledSecondary,
    tertiary = AmoledTertiary,
    background = AmoledBackground,
    surface = AmoledSurface,
    onPrimary = AmoledOnPrimary,
    onBackground = AmoledOnBackground,
    onSurface = AmoledOnSurface,
    surfaceVariant = AmoledSurface,
    outline = AmoledBorder
)

@Composable
fun AndBookTheme(
    theme: ReaderTheme = ReaderTheme.LIGHT_COFFEE,
    content: @Composable () -> Unit
) {
    val colorScheme = when (theme) {
        ReaderTheme.LIGHT_COFFEE -> CoffeeLightColorScheme
        ReaderTheme.DARK_COFFEE -> CoffeeDarkColorScheme
        ReaderTheme.AMOLED -> AmoledColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val isLightTheme = theme == ReaderTheme.LIGHT_COFFEE
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = isLightTheme
                insetsController.isAppearanceLightNavigationBars = isLightTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

