package io.github.xhugoliu.touckey.feature.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    modifierMode: ModifierMode,
    onModifierModeSelected: (ModifierMode) -> Unit,
    onBackTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier =
            modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ControlActionChip(
                label = "Back",
                selected = false,
                onTap = onBackTap,
            )
            Text(
                text = "Settings",
                color = colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Box(modifier = Modifier.padding(horizontal = 22.dp))
        }

        Surface(
            color = colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Keyboard touch mode",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Choose between one-shot preset shortcuts and real press-and-release keyboard touches.",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                ModifierMode.entries.forEach { mode ->
                    ModifierModeCard(
                        mode = mode,
                        selected = modifierMode == mode,
                        onTap = { onModifierModeSelected(mode) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModifierModeCard(
    mode: ModifierMode,
    selected: Boolean,
    onTap: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (selected) {
                        colorScheme.primary
                    } else {
                        colorScheme.surfaceVariant
                    },
                )
                .border(
                    width = 1.dp,
                    color =
                        if (selected) {
                            colorScheme.primary
                        } else {
                            colorScheme.outline
                        },
                    shape = shape,
                )
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = mode.title,
                color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = mode.detail,
                color =
                    if (selected) {
                        colorScheme.onPrimary.copy(alpha = 0.78f)
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ControlActionChip(
    label: String,
    selected: Boolean,
    onTap: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .clip(shape)
                .background(
                    if (selected) {
                        colorScheme.primary
                    } else {
                        colorScheme.surface
                    },
                )
                .border(
                    width = 1.dp,
                    color =
                        if (selected) {
                            colorScheme.primary
                        } else {
                            colorScheme.outline
                        },
                    shape = shape,
                )
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
