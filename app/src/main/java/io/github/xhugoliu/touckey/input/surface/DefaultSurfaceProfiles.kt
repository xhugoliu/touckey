package io.github.xhugoliu.touckey.input.surface

data class DefaultSurfaceProfileSet(
    val layoutProfile: SurfaceLayoutProfile,
    val keymapProfile: KeymapProfile,
)

data class DefaultKeyboardKeySpec(
    val zoneId: String,
    val keyName: String,
    val label: String,
    val weight: Float,
    val role: SurfaceZoneRole,
    val shiftedLabel: String? = null,
) {
    fun displayLabel(shiftActive: Boolean): String = if (shiftActive) shiftedLabel ?: label else label
}

object DefaultSurfaceProfiles {
    const val DEFAULT_LAYOUT_PROFILE_ID = "touckey-default-layout"
    const val DEFAULT_KEYMAP_PROFILE_ID = "touckey-default-keymap"
    const val KEYBOARD_PAGE_ID = "keyboard"
    const val KEYBOARD_VARIANT_ID = "keyboard-expanded-landscape"

    fun defaultKeyboard(): DefaultSurfaceProfileSet {
        val rows = defaultKeyboardSourceRows()
        val columnsU = rows.maxOf { row -> row.sumOf { it.widthU.toDouble() }.toFloat() }
        val zones =
            rows.flatMapIndexed { rowIndex, row ->
                var xU = 0f
                row.map { key ->
                    val zone =
                        SurfaceZone(
                            id = key.zoneId,
                            role = key.role,
                            label = SurfaceLabel(text = key.label, shiftedText = key.shiftedLabel),
                            frame =
                                UnitFrame(
                                    xU = xU,
                                    yU = rowIndex.toFloat(),
                                    widthU = key.widthU,
                                    heightU = 1f,
                                ),
                        )
                    xU += key.widthU
                    zone
                }
            }
        val keyBindings =
            rows
                .flatten()
                .associate { key -> key.zoneId to BehaviorBinding.Key(key.keyName) }

        return DefaultSurfaceProfileSet(
            layoutProfile =
                SurfaceLayoutProfile(
                    id = DEFAULT_LAYOUT_PROFILE_ID,
                    pages =
                        listOf(
                            SurfacePage(
                                id = KEYBOARD_PAGE_ID,
                                label = "Keyboard",
                                variants =
                                    listOf(
                                        SurfaceLayoutVariant(
                                            id = KEYBOARD_VARIANT_ID,
                                            viewportClass = ViewportClass.Expanded,
                                            orientation = SurfaceOrientation.Landscape,
                                            alignment = VariantAlignment.TopStart,
                                            grid =
                                                SurfaceGrid(
                                                    columnsU = columnsU,
                                                    rowsU = rows.size.toFloat(),
                                                    snapU = SurfaceGrid.DEFAULT_SNAP_U,
                                                    gapU = 0.08f,
                                                ),
                                            zones = zones,
                                        ),
                                    ),
                            ),
                        ),
                ),
            keymapProfile =
                KeymapProfile(
                    id = DEFAULT_KEYMAP_PROFILE_ID,
                    layers =
                        listOf(
                            KeymapLayer(
                                id = KeymapProfile.DEFAULT_LAYER_ID,
                                pageId = KEYBOARD_PAGE_ID,
                                bindings = keyBindings,
                            ),
                        ),
                ),
        )
    }

    fun defaultKeyboardRows(): List<List<DefaultKeyboardKeySpec>> =
        defaultKeyboard().let { defaultKeyboard ->
            keyboardRows(defaultKeyboard.layoutProfile, defaultKeyboard.keymapProfile)
        }

    fun keyboardRows(
        layoutProfile: SurfaceLayoutProfile,
        keymapProfile: KeymapProfile,
        pageId: String = KEYBOARD_PAGE_ID,
        variantId: String = KEYBOARD_VARIANT_ID,
    ): List<List<DefaultKeyboardKeySpec>> {
        val page = layoutProfile.pages.first { it.id == pageId }
        val variant = page.variants.first { it.id == variantId }
        return variant.zones
            .filter { it.role.isKeyboardLike }
            .sortedWith(compareBy<SurfaceZone> { it.frame.yU }.thenBy { it.frame.xU }.thenBy { it.id })
            .groupBy { it.frame.yU }
            .values
            .map { row ->
                row.map { zone ->
                    val binding = keymapProfile.bindingFor(zone.id, pageId = pageId) as? BehaviorBinding.Key
                    DefaultKeyboardKeySpec(
                        zoneId = zone.id,
                        keyName = binding?.key ?: zone.id,
                        label = zone.label.text,
                        weight = zone.frame.widthU,
                        role = zone.role,
                        shiftedLabel = zone.label.shiftedText,
                    )
                }
            }
    }

    private fun defaultKeyboardSourceRows(): List<List<DefaultKeyboardSourceKey>> =
        listOf(
            listOf(
                key("Escape", "Esc", role = SurfaceZoneRole.Function),
                key("F1", "F1", role = SurfaceZoneRole.Function),
                key("F2", "F2", role = SurfaceZoneRole.Function),
                key("F3", "F3", role = SurfaceZoneRole.Function),
                key("F4", "F4", role = SurfaceZoneRole.Function),
                key("F5", "F5", role = SurfaceZoneRole.Function),
                key("F6", "F6", role = SurfaceZoneRole.Function),
                key("F7", "F7", role = SurfaceZoneRole.Function),
                key("F8", "F8", role = SurfaceZoneRole.Function),
                key("F9", "F9", role = SurfaceZoneRole.Function),
                key("F10", "F10", role = SurfaceZoneRole.Function),
                key("F11", "F11", role = SurfaceZoneRole.Function),
                key("F12", "F12", role = SurfaceZoneRole.Function),
                key("Home", "Home", role = SurfaceZoneRole.Navigation),
                key("End", "End", role = SurfaceZoneRole.Navigation),
                key("PageUp", "PgUp", role = SurfaceZoneRole.Navigation),
                key("PageDown", "PgDn", role = SurfaceZoneRole.Navigation),
                key("Delete", "Delete", role = SurfaceZoneRole.Navigation),
            ),
            listOf(
                key("Grave", "`", shiftedLabel = "~"),
                key("1", "1", shiftedLabel = "!"),
                key("2", "2", shiftedLabel = "@"),
                key("3", "3", shiftedLabel = "#"),
                key("4", "4", shiftedLabel = "$"),
                key("5", "5", shiftedLabel = "%"),
                key("6", "6", shiftedLabel = "^"),
                key("7", "7", shiftedLabel = "&"),
                key("8", "8", shiftedLabel = "*"),
                key("9", "9", shiftedLabel = "("),
                key("0", "0", shiftedLabel = ")"),
                key("Minus", "-", shiftedLabel = "_"),
                key("Equal", "=", shiftedLabel = "+"),
                key("Backspace", "Backspace", widthU = 1.75f, role = SurfaceZoneRole.Navigation),
            ),
            listOf(
                key("Tab", "Tab", widthU = 1.5f, role = SurfaceZoneRole.Navigation),
                key("Q", "q", shiftedLabel = "Q"),
                key("W", "w", shiftedLabel = "W"),
                key("E", "e", shiftedLabel = "E"),
                key("R", "r", shiftedLabel = "R"),
                key("T", "t", shiftedLabel = "T"),
                key("Y", "y", shiftedLabel = "Y"),
                key("U", "u", shiftedLabel = "U"),
                key("I", "i", shiftedLabel = "I"),
                key("O", "o", shiftedLabel = "O"),
                key("P", "p", shiftedLabel = "P"),
                key("LeftBracket", "[", shiftedLabel = "{"),
                key("RightBracket", "]", shiftedLabel = "}"),
                key("Backslash", "\\", shiftedLabel = "|"),
            ),
            listOf(
                key("CapsLock", "Caps", widthU = 1.75f, role = SurfaceZoneRole.System),
                key("A", "a", shiftedLabel = "A"),
                key("S", "s", shiftedLabel = "S"),
                key("D", "d", shiftedLabel = "D"),
                key("F", "f", shiftedLabel = "F"),
                key("G", "g", shiftedLabel = "G"),
                key("H", "h", shiftedLabel = "H"),
                key("J", "j", shiftedLabel = "J"),
                key("K", "k", shiftedLabel = "K"),
                key("L", "l", shiftedLabel = "L"),
                key("Semicolon", ";", shiftedLabel = ":"),
                key("Quote", "'", shiftedLabel = "\""),
                key("Enter", "Enter", widthU = 2f, role = SurfaceZoneRole.Navigation),
            ),
            listOf(
                key("Shift", "Shift", widthU = 1.5f, role = SurfaceZoneRole.Modifier),
                key("Z", "z", shiftedLabel = "Z"),
                key("X", "x", shiftedLabel = "X"),
                key("C", "c", shiftedLabel = "C"),
                key("V", "v", shiftedLabel = "V"),
                key("B", "b", shiftedLabel = "B"),
                key("N", "n", shiftedLabel = "N"),
                key("M", "m", shiftedLabel = "M"),
                key("Comma", ",", shiftedLabel = "<"),
                key("Period", ".", shiftedLabel = ">"),
                key("Slash", "/", shiftedLabel = "?"),
                key("Up", "Up", role = SurfaceZoneRole.Navigation),
            ),
            listOf(
                key("Ctrl", "Ctrl", widthU = 1.25f, role = SurfaceZoneRole.Modifier),
                key("Cmd", "Cmd", widthU = 1.25f, role = SurfaceZoneRole.Modifier),
                key("Alt", "Alt", widthU = 1.25f, role = SurfaceZoneRole.Modifier),
                key("Space", "Space", widthU = 4.25f, role = SurfaceZoneRole.System),
                key("Left", "Left", role = SurfaceZoneRole.Navigation),
                key("Down", "Down", role = SurfaceZoneRole.Navigation),
                key("Right", "Right", role = SurfaceZoneRole.Navigation),
            ),
        )

    private fun key(
        keyName: String,
        label: String,
        widthU: Float = 1f,
        role: SurfaceZoneRole = SurfaceZoneRole.Key,
        shiftedLabel: String? = null,
    ): DefaultKeyboardSourceKey =
        DefaultKeyboardSourceKey(
            zoneId = "key-${keyName.toStableId()}",
            keyName = keyName,
            label = label,
            widthU = widthU,
            role = role,
            shiftedLabel = shiftedLabel,
        )

    private fun String.toStableId(): String =
        map { char ->
            when {
                char.isLetterOrDigit() -> char.lowercaseChar()
                else -> '-'
            }
        }.joinToString(separator = "")

    private data class DefaultKeyboardSourceKey(
        val zoneId: String,
        val keyName: String,
        val label: String,
        val widthU: Float,
        val role: SurfaceZoneRole,
        val shiftedLabel: String? = null,
    )
}
