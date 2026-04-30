package io.github.xhugoliu.touckey.input.surface

import io.github.xhugoliu.touckey.hid.HidCapabilityCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceProfileValidatorTest {
    @Test
    fun `valid default layout profile passes validation`() {
        val defaultSurface = DefaultSurfaceProfiles.defaultKeyboard()
        val result =
            SurfaceProfileValidator.validate(
                layoutProfile = defaultSurface.layoutProfile,
                keymapProfile = defaultSurface.keymapProfile,
            )

        assertTrue(result.errors.toString(), result.isValid)
        assertEquals(emptyList<SurfaceValidationIssue>(), result.warnings)
    }

    @Test
    fun `default profile key bindings exist in HID capability catalog`() {
        val defaultSurface = DefaultSurfaceProfiles.defaultKeyboard()
        val bindings = defaultSurface.keymapProfile.layers.single().bindings.values

        assertTrue(bindings.isNotEmpty())
        bindings
            .filterIsInstance<BehaviorBinding.Key>()
            .forEach { binding ->
                assertTrue(
                    "${binding.key} should be supported by the HID catalog",
                    HidCapabilityCatalog.supportsKeyboardInput(binding.key),
                )
            }
    }

    @Test
    fun `default keyboard rows preserve labels roles and snapped width classes`() {
        val rows = DefaultSurfaceProfiles.defaultKeyboardRows()

        assertEquals(6, rows.size)
        assertEquals(listOf("Esc", "F1", "F2", "F3"), rows[0].take(4).map { it.label })
        assertEquals(listOf("Ctrl", "Cmd", "Alt", "Space", "Left", "Down", "Right"), rows.last().map { it.label })
        assertEquals(SurfaceZoneRole.Modifier, rows.last().first().role)
        assertEquals(4.25f, rows.last()[3].weight)
        assertEquals("!", rows[1][1].shiftedLabel)
    }

    @Test
    fun `duplicate zone IDs fail`() {
        val duplicate = zone("duplicate", x = 0f)
        val result =
            SurfaceProfileValidator.validate(
                layoutProfile = profile(zones = listOf(duplicate, duplicate.copy(frame = frame(x = 1f)))),
                keymapProfile = keymap("duplicate"),
            )

        assertHasError(result, SurfaceValidationCode.DuplicateId)
    }

    @Test
    fun `zone outside grid fails`() {
        val result =
            SurfaceProfileValidator.validate(
                layoutProfile = profile(zones = listOf(zone("a", x = 3.5f, width = 1f))),
                keymapProfile = keymap("a"),
            )

        assertHasError(result, SurfaceValidationCode.ZoneOutsideGrid)
    }

    @Test
    fun `overlapping interactive zones fail`() {
        val result =
            SurfaceProfileValidator.validate(
                layoutProfile = profile(zones = listOf(zone("a", x = 0f, width = 1.5f), zone("b", x = 1f))),
                keymapProfile = keymap("a", "b"),
            )

        assertHasError(result, SurfaceValidationCode.OverlappingInteractiveZones)
    }

    @Test
    fun `invalid grid dimensions and unsnapped frame values fail`() {
        val result =
            SurfaceProfileValidator.validate(
                layoutProfile =
                    profile(
                        grid = SurfaceGrid(columnsU = 4f, rowsU = 2f, snapU = 0.25f),
                        zones = listOf(zone("a", x = 0.1f)),
                    ).copy(
                        pages =
                            listOf(
                                page(
                                    grid = SurfaceGrid(columnsU = 0f, rowsU = 2f, snapU = 0.25f),
                                    zones = listOf(zone("a", x = 0.1f)),
                                ),
                            ),
                    ),
                keymapProfile = keymap("a"),
            )

        assertHasError(result, SurfaceValidationCode.InvalidGrid)
        assertHasError(result, SurfaceValidationCode.InvalidFrame)
    }

    @Test
    fun `missing binding and unknown binding zone fail`() {
        val result =
            SurfaceProfileValidator.validate(
                layoutProfile = profile(zones = listOf(zone("a"))),
                keymapProfile =
                    KeymapProfile(
                        id = "test-keymap",
                        layers =
                            listOf(
                                KeymapLayer(
                                    id = KeymapProfile.DEFAULT_LAYER_ID,
                                    pageId = "page",
                                    bindings = mapOf("unknown" to BehaviorBinding.Key("A")),
                                ),
                            ),
                    ),
            )

        assertHasError(result, SurfaceValidationCode.MissingBinding)
        assertHasError(result, SurfaceValidationCode.UnknownBindingZone)
    }

    @Test
    fun `unsupported usage validation fails before dispatch`() {
        val result =
            SurfaceProfileValidator.validate(
                layoutProfile = profile(zones = listOf(zone("a"))),
                keymapProfile =
                    KeymapProfile(
                        id = "test-keymap",
                        layers =
                            listOf(
                                KeymapLayer(
                                    id = KeymapProfile.DEFAULT_LAYER_ID,
                                    pageId = "page",
                                    bindings = mapOf("a" to BehaviorBinding.Key("UnsupportedKey")),
                                ),
                            ),
                    ),
            )

        assertHasError(result, SurfaceValidationCode.UnsupportedHidUsage)
    }

    @Test
    fun `unused binding returns warning`() {
        val result =
            SurfaceProfileValidator.validate(
                layoutProfile = profile(zones = listOf(zone("label", role = SurfaceZoneRole.Decoration))),
                keymapProfile = keymap("label"),
            )

        assertTrue(result.isValid)
        assertHasWarning(result, SurfaceValidationCode.UnusedBinding)
    }

    @Test
    fun `rendered touch size below hard minimum fails and below recommended warns`() {
        val surface = profile(grid = SurfaceGrid(columnsU = 4f, rowsU = 1f), zones = listOf(zone("a")))
        val keymap = keymap("a")
        val renderedHard =
            SurfaceRuntime.render(
                pageId = "page",
                variant = surface.pages.single().variants.single(),
                availableWidthPx = 120f,
                availableHeightPx = 30f,
            )
        val renderedRecommended =
            SurfaceRuntime.render(
                pageId = "page",
                variant = surface.pages.single().variants.single(),
                availableWidthPx = 160f,
                availableHeightPx = 40f,
            )

        assertHasError(
            SurfaceProfileValidator.validateRendered(surface, keymap, renderedHard),
            SurfaceValidationCode.BelowHardMinimumTouchSize,
        )
        assertHasWarning(
            SurfaceProfileValidator.validateRendered(surface, keymap, renderedRecommended),
            SurfaceValidationCode.BelowRecommendedTouchSize,
        )
    }

    private fun assertHasError(
        result: SurfaceValidationResult,
        code: SurfaceValidationCode,
    ) {
        assertFalse("Expected $code error in $result", result.isValid)
        assertTrue(result.errors.any { it.code == code })
    }

    private fun assertHasWarning(
        result: SurfaceValidationResult,
        code: SurfaceValidationCode,
    ) {
        assertTrue("Expected $code warning in $result", result.warnings.any { it.code == code })
    }

    private fun profile(
        grid: SurfaceGrid = SurfaceGrid(columnsU = 4f, rowsU = 2f),
        zones: List<SurfaceZone>,
    ): SurfaceLayoutProfile =
        SurfaceLayoutProfile(
            id = "profile",
            pages = listOf(page(grid = grid, zones = zones)),
        )

    private fun page(
        grid: SurfaceGrid,
        zones: List<SurfaceZone>,
    ): SurfacePage =
        SurfacePage(
            id = "page",
            label = "Page",
            variants =
                listOf(
                    SurfaceLayoutVariant(
                        id = "variant",
                        viewportClass = ViewportClass.Expanded,
                        orientation = SurfaceOrientation.Landscape,
                        alignment = VariantAlignment.TopStart,
                        grid = grid,
                        zones = zones,
                    ),
                ),
        )

    private fun keymap(vararg zoneIds: String): KeymapProfile =
        KeymapProfile(
            id = "test-keymap",
            layers =
                listOf(
                    KeymapLayer(
                        id = KeymapProfile.DEFAULT_LAYER_ID,
                        pageId = "page",
                        bindings = zoneIds.associateWith { BehaviorBinding.Key("A") },
                    ),
                ),
        )

    private fun zone(
        id: String,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = 1f,
        height: Float = 1f,
        role: SurfaceZoneRole = SurfaceZoneRole.Key,
    ): SurfaceZone =
        SurfaceZone(
            id = id,
            role = role,
            label = SurfaceLabel(id),
            frame = frame(x = x, y = y, width = width, height = height),
        )

    private fun frame(
        x: Float = 0f,
        y: Float = 0f,
        width: Float = 1f,
        height: Float = 1f,
    ): UnitFrame = UnitFrame(xU = x, yU = y, widthU = width, heightU = height)
}
