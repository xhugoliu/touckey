package io.github.xhugoliu.touckey.input.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceRuntimeTest {
    @Test
    fun `exact viewport and orientation variant is selected first`() {
        val profile =
            profile(
                variants =
                    listOf(
                        variant("fallback", SurfaceOrientation.Any),
                        variant("exact", SurfaceOrientation.Landscape),
                    ),
            )

        val selection =
            SurfaceRuntime.selectVariant(
                layoutProfile = profile,
                pageId = "page",
                viewportClass = ViewportClass.Expanded,
                orientation = SurfaceOrientation.Landscape,
            )

        assertEquals("exact", selection.variant.id)
        assertEquals(emptyList<SurfaceRuntimeNotice>(), selection.notices)
    }

    @Test
    fun `same viewport compatible orientation fallback is deterministic`() {
        val profile =
            profile(
                variants =
                    listOf(
                        variant("landscape", SurfaceOrientation.Landscape),
                        variant("any", SurfaceOrientation.Any),
                    ),
            )

        val selection =
            SurfaceRuntime.selectVariant(
                layoutProfile = profile,
                pageId = "page",
                viewportClass = ViewportClass.Expanded,
                orientation = SurfaceOrientation.Portrait,
            )

        assertEquals("any", selection.variant.id)
        assertEquals(listOf(SurfaceRuntimeNotice.MissingExactResponsiveVariant), selection.notices)
    }

    @Test
    fun `generated fallback is deterministic and read only`() {
        val profile =
            profile(
                variants =
                    listOf(
                        variant("seed-b", SurfaceOrientation.Portrait, viewportClass = ViewportClass.Expanded),
                        variant("seed-a", SurfaceOrientation.Landscape, viewportClass = ViewportClass.Expanded),
                    ),
            )

        val selection =
            SurfaceRuntime.selectVariant(
                layoutProfile = profile,
                pageId = "page",
                viewportClass = ViewportClass.Compact,
                orientation = SurfaceOrientation.Portrait,
            )

        assertEquals("seed-a-generated-compact-portrait", selection.variant.id)
        assertTrue(selection.variant.generated)
        assertTrue(selection.variant.readOnly)
        assertEquals(
            listOf(
                SurfaceRuntimeNotice.MissingExactResponsiveVariant,
                SurfaceRuntimeNotice.GeneratedFallbackVariant,
            ),
            selection.notices,
        )
    }

    @Test
    fun `renderer uses stable unit pixel and gap math`() {
        val rendered =
            SurfaceRuntime.render(
                pageId = "page",
                variant =
                    variant(
                        id = "v",
                        orientation = SurfaceOrientation.Landscape,
                        grid = SurfaceGrid(columnsU = 4f, rowsU = 2f, gapU = 0.25f),
                        zones = listOf(zone("a", frame = UnitFrame(xU = 1f, yU = 0f, widthU = 2f, heightU = 1f))),
                    ),
                availableWidthPx = 200f,
                availableHeightPx = 60f,
            )

        assertEquals(30f, rendered.unitPx)
        assertEquals(0f, rendered.offsetX)
        assertEquals(0f, rendered.offsetY)
        assertEquals(33.75f, rendered.zones.single().bounds.left)
        assertEquals(86.25f, rendered.zones.single().bounds.right)
    }

    @Test
    fun `center alignment produces expected bounds`() {
        val rendered =
            SurfaceRuntime.render(
                pageId = "page",
                variant =
                    variant(
                        id = "center",
                        orientation = SurfaceOrientation.Landscape,
                        alignment = VariantAlignment.Center,
                        grid = SurfaceGrid(columnsU = 2f, rowsU = 1f),
                        zones = listOf(zone("a")),
                    ),
                availableWidthPx = 300f,
                availableHeightPx = 100f,
            )

        assertEquals(50f, rendered.offsetX)
        assertEquals(0f, rendered.offsetY)
        assertEquals(50f, rendered.zones.single().bounds.left)
        assertEquals(150f, rendered.zones.single().bounds.right)
    }

    @Test
    fun `hit testing resolves zones and ignores gaps`() {
        val rendered =
            SurfaceRuntime.render(
                pageId = "page",
                variant =
                    variant(
                        id = "v",
                        orientation = SurfaceOrientation.Landscape,
                        grid = SurfaceGrid(columnsU = 3f, rowsU = 1f, gapU = 0.2f),
                        zones =
                            listOf(
                                zone("left", frame = UnitFrame(0f, 0f, 1f, 1f)),
                                zone("right", frame = UnitFrame(2f, 0f, 1f, 1f)),
                            ),
                    ),
                availableWidthPx = 300f,
                availableHeightPx = 100f,
            )

        assertEquals("left", SurfaceRuntime.hitTest(rendered, x = 50f, y = 50f)?.zone?.id)
        assertNull(SurfaceRuntime.hitTest(rendered, x = 150f, y = 50f))
    }

    @Test
    fun `keyboard-like zones emit down up and tap while pointer zones capture drag`() {
        val rendered =
            SurfaceRuntime.render(
                pageId = "page",
                variant =
                    variant(
                        id = "v",
                        orientation = SurfaceOrientation.Landscape,
                        grid = SurfaceGrid(columnsU = 2f, rowsU = 1f),
                        zones =
                            listOf(
                                zone("key", frame = UnitFrame(0f, 0f, 1f, 1f)),
                                zone("pointer", role = SurfaceZoneRole.PointerSurface, frame = UnitFrame(1f, 0f, 1f, 1f)),
                            ),
                    ),
                availableWidthPx = 200f,
                availableHeightPx = 100f,
            )
        val runtime = SurfacePointerRuntime(rendered)

        assertTrue(runtime.onPointerDown(pointerId = 1, x = 50f, y = 50f).single() is SurfaceEvent.ZoneDown)
        val keyUpEvents = runtime.onPointerUp(pointerId = 1, x = 50f, y = 50f)
        assertEquals(listOf("ZoneUp", "ZoneTap"), keyUpEvents.map { it.javaClass.simpleName })

        assertEquals("pointer", (runtime.onPointerDown(pointerId = 2, x = 150f, y = 50f).single() as SurfaceEvent.ZoneDown).zoneId)
        assertEquals("pointer", (runtime.onPointerMove(pointerId = 2, x = 170f, y = 60f).single() as SurfaceEvent.ZoneDrag).zoneId)
        val pointerUpEvents = runtime.onPointerUp(pointerId = 2, x = 190f, y = 90f)
        assertEquals(1, pointerUpEvents.size)
        assertFalse(pointerUpEvents.any { it is SurfaceEvent.ZoneTap })
    }

    @Test
    fun `disabled and non interactive zones do not hit test`() {
        val rendered =
            SurfaceRuntime.render(
                pageId = "page",
                variant =
                    variant(
                        id = "v",
                        orientation = SurfaceOrientation.Landscape,
                        grid = SurfaceGrid(columnsU = 2f, rowsU = 1f),
                        zones =
                            listOf(
                                zone("disabled", enabled = false, frame = UnitFrame(0f, 0f, 1f, 1f)),
                                zone("decoration", role = SurfaceZoneRole.Decoration, frame = UnitFrame(1f, 0f, 1f, 1f)),
                            ),
                    ),
                availableWidthPx = 200f,
                availableHeightPx = 100f,
            )

        assertNull(SurfaceRuntime.hitTest(rendered, x = 50f, y = 50f))
        assertNull(SurfaceRuntime.hitTest(rendered, x = 150f, y = 50f))
    }

    private fun profile(variants: List<SurfaceLayoutVariant>): SurfaceLayoutProfile =
        SurfaceLayoutProfile(
            id = "profile",
            pages =
                listOf(
                    SurfacePage(
                        id = "page",
                        label = "Page",
                        variants = variants,
                    ),
                ),
        )

    private fun variant(
        id: String,
        orientation: SurfaceOrientation,
        viewportClass: ViewportClass = ViewportClass.Expanded,
        alignment: VariantAlignment = VariantAlignment.TopStart,
        grid: SurfaceGrid = SurfaceGrid(columnsU = 2f, rowsU = 1f),
        zones: List<SurfaceZone> = listOf(zone("a")),
    ): SurfaceLayoutVariant =
        SurfaceLayoutVariant(
            id = id,
            viewportClass = viewportClass,
            orientation = orientation,
            alignment = alignment,
            grid = grid,
            zones = zones,
        )

    private fun zone(
        id: String,
        role: SurfaceZoneRole = SurfaceZoneRole.Key,
        frame: UnitFrame = UnitFrame(0f, 0f, 1f, 1f),
        enabled: Boolean = true,
    ): SurfaceZone =
        SurfaceZone(
            id = id,
            role = role,
            label = SurfaceLabel(id),
            frame = frame,
            enabled = enabled,
        )
}
