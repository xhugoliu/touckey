package io.github.xhugoliu.touckey.input.surface

object UserSurfaceProfiles {
    const val USER_LAYOUT_PROFILE_ID = "touckey-user-layout"
    const val USER_KEYMAP_PROFILE_ID = "touckey-user-keymap"

    fun keyboardProfileFromRows(
        baseProfileSet: DefaultSurfaceProfileSet,
        rows: List<List<DefaultKeyboardKeySpec>>,
        pageId: String = DefaultSurfaceProfiles.KEYBOARD_PAGE_ID,
        variantId: String = DefaultSurfaceProfiles.KEYBOARD_VARIANT_ID,
    ): DefaultSurfaceProfileSet {
        val page = baseProfileSet.layoutProfile.pages.first { it.id == pageId }
        val variant = page.variants.first { it.id == variantId }
        val editedZoneIds = rows.flatten().map { key -> key.zoneId }.toSet()
        val editedZones = rows.toSurfaceZones()
        val zones = editedZones + variant.zones.filterNot { zone -> zone.id in editedZoneIds }
        val grid =
            variant.grid.copy(
                columnsU = maxOf(variant.grid.columnsU, rows.maxRowWidthU()),
                rowsU = maxOf(variant.grid.rowsU, rows.size.toFloat()),
            )
        val updatedVariant =
            variant.copy(
                grid = grid,
                zones = zones,
                generated = false,
                readOnly = false,
            )
        val updatedPage =
            page.copy(
                variants =
                    page.variants.map { candidate ->
                        if (candidate.id == variant.id) updatedVariant else candidate
                    },
            )
        val layoutProfile =
            baseProfileSet.layoutProfile.copy(
                id = USER_LAYOUT_PROFILE_ID,
                pages =
                    baseProfileSet.layoutProfile.pages.map { candidate ->
                        if (candidate.id == page.id) updatedPage else candidate
                    },
            )

        val editedBindings =
            rows
                .flatten()
                .associate { key -> key.zoneId to BehaviorBinding.Key(key.keyName) }
        val keymapProfile =
            baseProfileSet.keymapProfile.copy(
                id = USER_KEYMAP_PROFILE_ID,
                layers =
                    baseProfileSet.keymapProfile.layers.map { layer ->
                        if (layer.id == KeymapProfile.DEFAULT_LAYER_ID && layer.pageId == page.id) {
                            layer.copy(
                                bindings = layer.bindings.filterKeys { zoneId -> zoneId !in editedZoneIds } + editedBindings,
                            )
                        } else {
                            layer
                        }
                    },
            )

        return DefaultSurfaceProfileSet(
            layoutProfile = layoutProfile,
            keymapProfile = keymapProfile,
        )
    }

    fun keyboardWidthOverrides(
        baseProfileSet: DefaultSurfaceProfileSet,
        rows: List<List<DefaultKeyboardKeySpec>>,
    ): Map<String, Float> {
        val baseWidths =
            DefaultSurfaceProfiles
                .keyboardRows(
                    layoutProfile = baseProfileSet.layoutProfile,
                    keymapProfile = baseProfileSet.keymapProfile,
                )
                .flatten()
                .associate { key -> key.zoneId to key.weight }

        return rows
            .flatten()
            .mapNotNull { key ->
                val baseWidth = baseWidths[key.zoneId] ?: return@mapNotNull key.zoneId to key.weight
                if (key.weight.isSameUnitAs(baseWidth)) {
                    null
                } else {
                    key.zoneId to key.weight
                }
            }
            .toMap()
    }

    fun keyboardBindingOverrides(
        baseProfileSet: DefaultSurfaceProfileSet,
        rows: List<List<DefaultKeyboardKeySpec>>,
    ): Map<String, String> {
        val baseBindings =
            DefaultSurfaceProfiles
                .keyboardRows(
                    layoutProfile = baseProfileSet.layoutProfile,
                    keymapProfile = baseProfileSet.keymapProfile,
                )
                .flatten()
                .associate { key -> key.zoneId to key.keyName }

        return rows
            .flatten()
            .mapNotNull { key ->
                val baseKeyName = baseBindings[key.zoneId] ?: return@mapNotNull key.zoneId to key.keyName
                if (key.keyName == baseKeyName) {
                    null
                } else {
                    key.zoneId to key.keyName
                }
            }
            .toMap()
    }

    private fun List<List<DefaultKeyboardKeySpec>>.toSurfaceZones(): List<SurfaceZone> =
        flatMapIndexed { rowIndex, row ->
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
                                widthU = key.weight,
                                heightU = 1f,
                            ),
                    )
                xU += key.weight
                zone
            }
        }

    private fun List<List<DefaultKeyboardKeySpec>>.maxRowWidthU(): Float =
        maxOfOrNull { row -> row.sumOf { key -> key.weight.toDouble() }.toFloat() } ?: 0f

    private fun Float.isSameUnitAs(other: Float): Boolean = kotlin.math.abs(this - other) < 0.001f
}
