package io.github.xhugoliu.touckey.input.surface

data class VariantSelection(
    val pageId: String,
    val variant: SurfaceLayoutVariant,
    val notices: List<SurfaceRuntimeNotice> = emptyList(),
)

enum class SurfaceRuntimeNotice {
    MissingExactResponsiveVariant,
    GeneratedFallbackVariant,
}

object SurfaceRuntime {
    fun selectVariant(
        layoutProfile: SurfaceLayoutProfile,
        pageId: String,
        viewportClass: ViewportClass,
        orientation: SurfaceOrientation,
    ): VariantSelection {
        val page =
            layoutProfile.pages.firstOrNull { it.id == pageId }
                ?: error("Surface page $pageId does not exist in profile ${layoutProfile.id}.")

        page.variants
            .firstOrNull { it.viewportClass == viewportClass && it.orientation == orientation }
            ?.let { exact ->
                return VariantSelection(pageId = pageId, variant = exact)
            }

        val sameViewportFallback =
            page.variants
                .filter { it.viewportClass == viewportClass }
                .sortedWith(compareBy<SurfaceLayoutVariant> { it.orientation.sortOrder }.thenBy { it.id })
                .firstOrNull { it.orientation == SurfaceOrientation.Any }
                ?: page.variants
                    .filter { it.viewportClass == viewportClass }
                    .sortedBy { it.id }
                    .firstOrNull()

        if (sameViewportFallback != null) {
            return VariantSelection(
                pageId = pageId,
                variant = sameViewportFallback,
                notices = listOf(SurfaceRuntimeNotice.MissingExactResponsiveVariant),
            )
        }

        val seed =
            page.variants
                .sortedWith(compareBy<SurfaceLayoutVariant> { it.viewportClass.name }.thenBy { it.orientation.name }.thenBy { it.id })
                .firstOrNull()
                ?: error("Surface page $pageId has no layout variants.")
        val generated =
            seed.copy(
                id = "${seed.id}-generated-${viewportClass.name.lowercase()}-${orientation.name.lowercase()}",
                viewportClass = viewportClass,
                orientation = orientation,
                generated = true,
                readOnly = true,
            )

        return VariantSelection(
            pageId = pageId,
            variant = generated,
            notices =
                listOf(
                    SurfaceRuntimeNotice.MissingExactResponsiveVariant,
                    SurfaceRuntimeNotice.GeneratedFallbackVariant,
                ),
        )
    }

    fun render(
        pageId: String,
        variant: SurfaceLayoutVariant,
        availableWidthPx: Float,
        availableHeightPx: Float,
    ): RenderedSurface {
        val unitPx = minOf(availableWidthPx / variant.grid.columnsU, availableHeightPx / variant.grid.rowsU)
        val contentWidthPx = variant.grid.columnsU * unitPx
        val contentHeightPx = variant.grid.rowsU * unitPx
        val offsetX =
            when (variant.alignment) {
                VariantAlignment.TopStart -> 0f
                VariantAlignment.Center -> (availableWidthPx - contentWidthPx) / 2f
            }
        val offsetY =
            when (variant.alignment) {
                VariantAlignment.TopStart -> 0f
                VariantAlignment.Center -> (availableHeightPx - contentHeightPx) / 2f
            }
        val gapPx = variant.grid.gapU * unitPx
        val insetPx = gapPx / 2f
        val zones =
            variant.zones.map { zone ->
                val left = offsetX + zone.frame.xU * unitPx + insetPx
                val top = offsetY + zone.frame.yU * unitPx + insetPx
                val right = offsetX + zone.frame.rightU * unitPx - insetPx
                val bottom = offsetY + zone.frame.bottomU * unitPx - insetPx
                RenderedZone(
                    zone = zone,
                    bounds =
                        PixelRect(
                            left = left,
                            top = top,
                            right = maxOf(left, right),
                            bottom = maxOf(top, bottom),
                        ),
                )
            }

        return RenderedSurface(
            pageId = pageId,
            variant = variant,
            unitPx = unitPx,
            offsetX = offsetX,
            offsetY = offsetY,
            zones = zones,
        )
    }

    fun hitTest(
        renderedSurface: RenderedSurface,
        x: Float,
        y: Float,
    ): RenderedZone? =
        renderedSurface.zones.firstOrNull { renderedZone ->
            renderedZone.zone.enabled &&
                renderedZone.zone.role.isInteractive &&
                renderedZone.bounds.contains(x, y)
        }

    private val SurfaceOrientation.sortOrder: Int
        get() =
            when (this) {
                SurfaceOrientation.Any -> 0
                SurfaceOrientation.Portrait -> 1
                SurfaceOrientation.Landscape -> 2
            }
}

class SurfacePointerRuntime(
    private val renderedSurface: RenderedSurface,
) {
    private val downZoneIdsByPointer = mutableMapOf<Int, String>()
    private val capturedZoneIdsByPointer = mutableMapOf<Int, String>()
    private val lastPointerPositions = mutableMapOf<Int, Pair<Float, Float>>()

    fun onPointerDown(
        pointerId: Int,
        x: Float,
        y: Float,
    ): List<SurfaceEvent> {
        val renderedZone = SurfaceRuntime.hitTest(renderedSurface, x, y) ?: return emptyList()
        val zone = renderedZone.zone

        downZoneIdsByPointer[pointerId] = zone.id
        lastPointerPositions[pointerId] = x to y
        if (zone.role == SurfaceZoneRole.PointerSurface || zone.role == SurfaceZoneRole.ScrollSurface) {
            capturedZoneIdsByPointer[pointerId] = zone.id
        }

        return listOf(
            SurfaceEvent.ZoneDown(
                zoneId = zone.id,
                pointerId = pointerId,
                x = x,
                y = y,
            ),
        )
    }

    fun onPointerMove(
        pointerId: Int,
        x: Float,
        y: Float,
    ): List<SurfaceEvent> {
        val capturedZoneId = capturedZoneIdsByPointer[pointerId] ?: return emptyList()
        val lastPosition = lastPointerPositions[pointerId] ?: (x to y)
        lastPointerPositions[pointerId] = x to y

        return listOf(
            SurfaceEvent.ZoneDrag(
                zoneId = capturedZoneId,
                pointerId = pointerId,
                deltaX = x - lastPosition.first,
                deltaY = y - lastPosition.second,
            ),
        )
    }

    fun onPointerUp(
        pointerId: Int,
        x: Float,
        y: Float,
    ): List<SurfaceEvent> {
        val downZoneId = downZoneIdsByPointer.remove(pointerId)
        val capturedZoneId = capturedZoneIdsByPointer.remove(pointerId)
        lastPointerPositions.remove(pointerId)

        val upZone = SurfaceRuntime.hitTest(renderedSurface, x, y)?.zone
        val eventZoneId = capturedZoneId ?: downZoneId ?: upZone?.id ?: return emptyList()
        val events =
            mutableListOf<SurfaceEvent>(
                SurfaceEvent.ZoneUp(
                    zoneId = eventZoneId,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                ),
            )
        val downZone =
            renderedSurface.zones
                .firstOrNull { it.zone.id == downZoneId }
                ?.zone

        if (
            capturedZoneId == null &&
            downZone != null &&
            downZone.role.isKeyboardLike &&
            upZone?.id == downZone.id
        ) {
            events +=
                SurfaceEvent.ZoneTap(
                    zoneId = downZone.id,
                    pointerId = pointerId,
                    x = x,
                    y = y,
                )
        }

        return events
    }

    fun onPointerCancel(pointerId: Int): List<SurfaceEvent> {
        val capturedZoneId = capturedZoneIdsByPointer.remove(pointerId)
        val downZoneId = downZoneIdsByPointer.remove(pointerId)
        val zoneId = capturedZoneId ?: downZoneId ?: return emptyList()
        lastPointerPositions.remove(pointerId)
        return listOf(
            SurfaceEvent.ZoneCancel(
                zoneId = zoneId,
                pointerId = pointerId,
            ),
        )
    }
}
