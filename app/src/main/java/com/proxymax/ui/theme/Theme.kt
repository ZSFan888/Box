package com.proxymax.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary            = light_primary,
    onPrimary          = light_on_primary,
    primaryContainer   = light_primary_container,
    onPrimaryContainer = light_on_primary_con,
    secondary          = light_secondary,
    onSecondary        = light_on_secondary,
    background         = light_background,
    onBackground       = light_on_background,
    surface            = light_surface,
    onSurface          = light_on_surface,
    surfaceVariant     = light_surface_variant,
    onSurfaceVariant   = light_on_surface_var,
    error              = light_error,
    onError            = light_on_error,
    errorContainer     = light_error_container,
    onErrorContainer   = light_on_error_con,
    outline            = light_outline,
    outlineVariant     = light_outline_variant,
)

private val DarkColors = darkColorScheme(
    primary            = dark_primary,
    onPrimary          = dark_on_primary,
    primaryContainer   = dark_primary_container,
    onPrimaryContainer = dark_on_primary_con,
    secondary          = dark_secondary,
    onSecondary        = dark_on_secondary,
    background         = dark_background,
    onBackground       = dark_on_background,
    surface            = dark_surface,
    onSurface          = dark_on_surface,
    surfaceVariant     = dark_surface_variant,
    onSurfaceVariant   = dark_on_surface_var,
    error              = dark_error,
    onError            = dark_on_error,
    errorContainer     = dark_error_container,
    onErrorContainer   = dark_on_error_con,
    outline            = dark_outline,
    outlineVariant     = dark_outline_variant,
)

@Composable
fun ProxyMaxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content:   @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
