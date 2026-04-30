package io.github.xhugoliu.touckey.feature.control

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.xhugoliu.touckey.hid.HidCapabilityCatalog
import io.github.xhugoliu.touckey.input.surface.BehaviorBinding
import io.github.xhugoliu.touckey.input.surface.DefaultKeyboardKeySpec
import io.github.xhugoliu.touckey.input.surface.DefaultSurfaceProfileSet
import io.github.xhugoliu.touckey.input.surface.DefaultSurfaceProfiles
import io.github.xhugoliu.touckey.input.surface.SurfaceProfileValidator
import io.github.xhugoliu.touckey.input.surface.SurfaceZone
import io.github.xhugoliu.touckey.input.surface.SurfaceZoneRole
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifierMode: ModifierMode,
    layoutProfileSet: DefaultSurfaceProfileSet,
    onModifierModeSelected: (ModifierMode) -> Unit,
    onLayoutProfileSave: (List<List<DefaultKeyboardKeySpec>>) -> Unit,
    onLayoutProfileReset: () -> Unit,
    onBackTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(colorScheme.background),
    ) {
        val portraitInsets =
            if (maxHeight > maxWidth) {
                Modifier
                    .displayCutoutPadding()
                    .systemBarsPadding()
            } else {
                Modifier
            }

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(portraitInsets)
                    .verticalScroll(rememberScrollState())
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

            LayoutProfileCard(
                layoutProfileSet = layoutProfileSet,
                onSave = onLayoutProfileSave,
                onReset = onLayoutProfileReset,
            )
        }
    }
}

@Composable
private fun LayoutProfileCard(
    layoutProfileSet: DefaultSurfaceProfileSet,
    onSave: (List<List<DefaultKeyboardKeySpec>>) -> Unit,
    onReset: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val layoutProfile = layoutProfileSet.layoutProfile
    val keymapProfile = layoutProfileSet.keymapProfile
    val page = layoutProfile.pages.first { it.id == DefaultSurfaceProfiles.KEYBOARD_PAGE_ID }
    val variant = page.variants.first { it.id == DefaultSurfaceProfiles.KEYBOARD_VARIANT_ID }
    val rows =
        remember(layoutProfileSet) {
            DefaultSurfaceProfiles.keyboardRows(
                layoutProfile = layoutProfile,
                keymapProfile = keymapProfile,
            )
        }
    var widthOverrides by remember(layoutProfileSet) {
        mutableStateOf<Map<String, Float>>(emptyMap())
    }
    var bindingOverrides by remember(layoutProfileSet) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    val draftRows =
        remember(rows, widthOverrides, bindingOverrides) {
            rows.map { row ->
                row.map { key ->
                    key.copy(
                        keyName = bindingOverrides[key.zoneId] ?: key.keyName,
                        weight = widthOverrides[key.zoneId] ?: key.weight,
                    )
                }
            }
        }
    val hasDraftChanges = widthOverrides.isNotEmpty() || bindingOverrides.isNotEmpty()
    val validation =
        remember(layoutProfileSet) {
            SurfaceProfileValidator.validate(
                layoutProfile = layoutProfile,
                keymapProfile = keymapProfile,
            )
        }
    val zoneById = remember(variant) { variant.zones.associateBy { it.id } }
    var selectedZoneId by remember(layoutProfileSet) {
        mutableStateOf(rows.firstOrNull()?.firstOrNull()?.zoneId)
    }
    val selectedZone = selectedZoneId?.let { zoneById[it] }
    val selectedDraftKey = selectedZoneId?.let { zoneId ->
        draftRows.flatten().firstOrNull { it.zoneId == zoneId }
    }
    val selectedBaseKey = selectedZoneId?.let { zoneId ->
        rows.flatten().firstOrNull { it.zoneId == zoneId }
    }
    val selectedBinding = selectedDraftKey?.let { key -> BehaviorBinding.Key(key.keyName) }
    val layerCount = keymapProfile.layers.count { it.pageId == page.id }

    Surface(
        color = colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Layout profile",
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${page.label} · ${variant.grid.columnsU.formatU()} x ${variant.grid.rowsU.formatU()} · ${variant.zones.size} zones",
                        color = colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                ValidationBadge(
                    errors = validation.errors.size,
                    warnings = validation.warnings.size,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colorScheme.surfaceVariant)
                        .padding(8.dp),
            ) {
                draftRows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { key ->
                            val selected = key.zoneId == selectedZoneId
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .weight(key.weight)
                                        .height(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(layoutPreviewColor(key.role))
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color =
                                                if (selected) {
                                                    colorScheme.primary
                                                } else {
                                                    colorScheme.outline.copy(alpha = 0.42f)
                                                },
                                            shape = RoundedCornerShape(6.dp),
                                        )
                                        .clickable { selectedZoneId = key.zoneId },
                            ) {
                                Text(
                                    text = key.label,
                                    color =
                                        if (selected) {
                                            colorScheme.primary
                                        } else {
                                            colorScheme.onSurface
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LayoutMetric(label = "Snap", value = variant.grid.snapU.formatU(), modifier = Modifier.weight(1f))
                LayoutMetric(label = "Gap", value = variant.grid.gapU.formatU(), modifier = Modifier.weight(1f))
                LayoutMetric(label = "Layers", value = layerCount.toString(), modifier = Modifier.weight(1f))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProfileActionButton(
                    label = "Save",
                    emphasized = hasDraftChanges,
                    enabled = hasDraftChanges,
                    onTap = {
                        onSave(draftRows)
                        widthOverrides = emptyMap()
                        bindingOverrides = emptyMap()
                    },
                    modifier = Modifier.weight(1f),
                )
                ProfileActionButton(
                    label = "Reset",
                    emphasized = false,
                    enabled = true,
                    onTap = {
                        onReset()
                        widthOverrides = emptyMap()
                        bindingOverrides = emptyMap()
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            selectedZone?.let { zone ->
                SelectedZonePanel(
                    zone = zone,
                    binding = selectedBinding,
                    frameLabel = draftRows.draftFrameLabel(zone.id),
                    selectedWidth = selectedDraftKey?.weight ?: zone.frame.widthU,
                    selectedBindingKey = selectedDraftKey?.keyName ?: selectedBaseKey?.keyName ?: zone.id,
                    onWidthSelected = { width ->
                        widthOverrides =
                            if (abs(width - zone.frame.widthU) < 0.001f) {
                                widthOverrides - zone.id
                            } else {
                                widthOverrides + (zone.id to width)
                            }
                    },
                    onBindingSelected = { keyName ->
                        val baseKeyName = selectedBaseKey?.keyName
                        bindingOverrides =
                            if (keyName == baseKeyName) {
                                bindingOverrides - zone.id
                            } else {
                                bindingOverrides + (zone.id to keyName)
                            }
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileActionButton(
    label: String,
    emphasized: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(shape)
                .background(
                    when {
                        !enabled -> colorScheme.surfaceVariant
                        emphasized -> colorScheme.primary
                        else -> colorScheme.surface
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (emphasized && enabled) colorScheme.primary else colorScheme.outline,
                    shape = shape,
                )
                .clickable(enabled = enabled, onClick = onTap)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color =
                when {
                    !enabled -> colorScheme.onSurface.copy(alpha = 0.36f)
                    emphasized -> colorScheme.onPrimary
                    else -> colorScheme.onSurface
                },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SelectedZonePanel(
    zone: SurfaceZone,
    binding: BehaviorBinding?,
    frameLabel: String,
    selectedWidth: Float,
    selectedBindingKey: String,
    onWidthSelected: (Float) -> Unit,
    onBindingSelected: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colorScheme.surfaceVariant)
                .padding(14.dp),
    ) {
        Text(
            text = zone.label.text,
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LayoutMetric(label = "Zone", value = zone.id, modifier = Modifier.weight(1.5f))
            LayoutMetric(label = "Role", value = zone.role.name, modifier = Modifier.weight(1f))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            LayoutMetric(label = "Binding", value = binding.label(), modifier = Modifier.weight(1.5f))
            LayoutMetric(label = "Frame", value = frameLabel, modifier = Modifier.weight(1.5f))
        }
        WidthOptionPicker(
            selectedWidth = selectedWidth,
            onWidthSelected = onWidthSelected,
        )
        BindingOptionPicker(
            selectedKeyName = selectedBindingKey,
            onKeyNameSelected = onBindingSelected,
        )
    }
}

@Composable
private fun WidthOptionPicker(
    selectedWidth: Float,
    onWidthSelected: (Float) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val widthOptions = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Width",
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            widthOptions.forEach { width ->
                val selected = abs(width - selectedWidth) < 0.001f
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) colorScheme.primary else colorScheme.surface)
                            .border(
                                width = 1.dp,
                                color = if (selected) colorScheme.primary else colorScheme.outline,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onWidthSelected(width) }
                            .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = width.formatU(),
                        color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun BindingOptionPicker(
    selectedKeyName: String,
    onKeyNameSelected: (String) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val optionRows = remember(selectedKeyName) { keyboardBindingOptionRows(selectedKeyName) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Binding",
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        optionRows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { option ->
                    val selected = option.keyName == selectedKeyName
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) colorScheme.primary else colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) colorScheme.primary else colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { onKeyNameSelected(option.keyName) }
                                .padding(horizontal = 4.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = option.label,
                            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationBadge(
    errors: Int,
    warnings: Int,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isClean = errors == 0 && warnings == 0
    val label =
        when {
            errors > 0 -> "$errors errors"
            warnings > 0 -> "$warnings warnings"
            else -> "Valid"
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isClean) colorScheme.primary else colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = if (isClean) colorScheme.primary else colorScheme.outline,
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (isClean) colorScheme.onPrimary else colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LayoutMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun layoutPreviewColor(role: SurfaceZoneRole) =
    when (role) {
        SurfaceZoneRole.Modifier -> MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
        SurfaceZoneRole.Function -> MaterialTheme.colorScheme.surface
        SurfaceZoneRole.Navigation -> MaterialTheme.colorScheme.background
        SurfaceZoneRole.System -> MaterialTheme.colorScheme.surface
        SurfaceZoneRole.Key -> MaterialTheme.colorScheme.surface
        SurfaceZoneRole.PointerSurface,
        SurfaceZoneRole.ScrollSurface,
        SurfaceZoneRole.Decoration,
        -> MaterialTheme.colorScheme.surfaceVariant
    }

private fun Float.formatU(): String {
    val rounded = roundToInt()
    if (abs(this - rounded) < 0.001f) {
        return "${rounded}U"
    }
    val hundredths = (this * 100f).roundToInt() / 100f
    return "${hundredths}U"
}

private fun BehaviorBinding?.label(): String =
    when (this) {
        is BehaviorBinding.Key -> "Key · $key"
        is BehaviorBinding.ConsumerControl -> "Consumer · $usage"
        is BehaviorBinding.PointerSurface -> "Pointer · ${sensitivity.formatPlain()}"
        is BehaviorBinding.ScrollSurface -> "Scroll · ${scale.formatPlain()}"
        null -> "Unbound"
    }

private fun Float.formatPlain(): String {
    val rounded = roundToInt()
    if (abs(this - rounded) < 0.001f) {
        return rounded.toString()
    }
    return ((this * 100f).roundToInt() / 100f).toString()
}

private fun keyboardBindingOptionRows(selectedKeyName: String): List<List<KeyboardBindingOption>> {
    val supportedNames = HidCapabilityCatalog.keyboardBindingNames.toSet()
    val rows =
        listOf(
            listOf("Escape", "F1", "F2", "F3", "F4", "F5"),
            listOf("F6", "F7", "F8", "F9", "F10", "F11", "F12"),
            listOf("Ctrl", "Shift", "Alt", "Cmd", "CapsLock", "Tab"),
            listOf("A", "B", "C", "D", "E", "F", "G"),
            listOf("H", "I", "J", "K", "L", "M", "N"),
            listOf("O", "P", "Q", "R", "S", "T"),
            listOf("U", "V", "W", "X", "Y", "Z"),
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("Grave", "Minus", "Equal", "LeftBracket", "RightBracket", "Backslash"),
            listOf("Semicolon", "Quote", "Backspace", "Enter", "Space"),
            listOf("Insert", "Home", "PageUp", "Delete", "End", "PageDown"),
            listOf("Left", "Right", "Up", "Down", "Menu"),
        ).map { row ->
            row
                .filter { keyName -> keyName in supportedNames }
                .map { keyName -> KeyboardBindingOption(keyName = keyName, label = keyName.bindingOptionLabel()) }
        }
    val selectedIsShown = rows.flatten().any { option -> option.keyName == selectedKeyName }
    return if (selectedIsShown || selectedKeyName !in supportedNames) {
        rows
    } else {
        listOf(listOf(KeyboardBindingOption(selectedKeyName, selectedKeyName.bindingOptionLabel()))) + rows
    }
}

private fun String.bindingOptionLabel(): String =
    when (this) {
        "Escape" -> "Esc"
        "Backspace" -> "Bksp"
        "CapsLock" -> "Caps"
        "PageUp" -> "PgUp"
        "PageDown" -> "PgDn"
        "LeftBracket" -> "["
        "RightBracket" -> "]"
        "Backslash" -> "\\"
        "Semicolon" -> ";"
        "Quote" -> "'"
        "Grave" -> "`"
        "Minus" -> "-"
        "Equal" -> "="
        else -> this
    }

private data class KeyboardBindingOption(
    val keyName: String,
    val label: String,
)

private fun List<List<DefaultKeyboardKeySpec>>.draftFrameLabel(zoneId: String): String {
    forEachIndexed { rowIndex, row ->
        var xU = 0f
        row.forEach { key ->
            if (key.zoneId == zoneId) {
                return "x ${xU.formatU()} · y ${rowIndex.toFloat().formatU()} · w ${key.weight.formatU()} · h 1U"
            }
            xU += key.weight
        }
    }
    return "Unresolved"
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
