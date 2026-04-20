package io.github.xhugoliu.touckey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val TouckeyLightColors =
    lightColorScheme(
        primary = Graphite900,
        secondary = Graphite500,
        background = Paper050,
        surface = Paper000,
        surfaceVariant = Graphite100,
        onPrimary = Paper050,
        onSecondary = Paper000,
        onBackground = Graphite900,
        onSurface = Graphite900,
        onSurfaceVariant = Graphite500,
        outline = Graphite300,
    )

private val TouckeyDarkColors =
    darkColorScheme(
        primary = Paper000,
        secondary = Graphite300,
        background = Graphite950,
        surface = Graphite900,
        surfaceVariant = Graphite800,
        onPrimary = Graphite900,
        onSecondary = Graphite900,
        onBackground = Paper050,
        onSurface = Paper050,
        onSurfaceVariant = Graphite300,
        outline = Graphite700,
    )

@Composable
fun TouckeyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TouckeyDarkColors else TouckeyLightColors,
        typography = TouckeyTypography,
        content = content,
    )
}
