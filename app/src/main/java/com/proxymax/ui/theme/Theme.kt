package com.proxymax.ui.theme

import androidx.compose.foundation.background
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary       = md_theme_dark_primary,
    secondary     = md_theme_dark_secondary,
    tertiary      = md_theme_dark_tertiary,
    background    = md_theme_dark_background,
    surface       = md_theme_dark_surface,
    onPrimary     = md_theme_dark_onPrimary,
    onBackground  = md_theme_dark_onBackground,
    onSurface     = md_theme_dark_onSurface,
)
private val LightColorScheme = lightColorScheme(
    primary       = md_theme_light_primary,
    secondary     = md_theme_light_secondary,
    tertiary      = md_theme_light_tertiary,
    background    = md_theme_light_background,
    surface       = md_theme_light_surface,
    onPrimary     = md_theme_light_onPrimary,
    onBackground  = md_theme_light_onBackground,
    onSurface     = md_theme_light_onSurface,
)

@Composable
fun ProxyMaxTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
