package io.github.xhugoliu.touckey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TouckeyLightColors =
    lightColorScheme(
        primary = Signal500,
        secondary = Moss500,
        background = Sand050,
        surface = Color.White,
        onPrimary = Sand050,
        onSecondary = Sand050,
        onBackground = Ink900,
        onSurface = Ink900,
        outline = Sand200,
    )

private val TouckeyDarkColors =
    darkColorScheme(
        primary = Signal500,
        secondary = Moss500,
        background = Ink900,
        surface = Ink700,
        onPrimary = Sand050,
        onSecondary = Sand050,
        onBackground = Sand050,
        onSurface = Sand050,
        outline = Fog100,
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
