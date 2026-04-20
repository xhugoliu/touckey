package io.github.xhugoliu.touckey.feature.control

enum class ModifierMode(
    val title: String,
    val detail: String,
) {
    Preset(
        title = "Preset",
        detail = "Tap a modifier to arm the next key stroke once.",
    ),
    Hold(
        title = "Hold",
        detail = "Touch down presses a key, lift releases it like a real keyboard.",
    ),
}

fun toggleArmedModifier(
    armedModifiers: List<String>,
    modifierName: String,
): List<String> =
    if (armedModifiers.contains(modifierName)) {
        armedModifiers - modifierName
    } else {
        armedModifiers + modifierName
    }

fun modifiersAfterKeyTap(
    mode: ModifierMode,
    armedModifiers: List<String>,
): List<String> =
    when (mode) {
        ModifierMode.Preset -> emptyList()
        ModifierMode.Hold -> armedModifiers
    }
