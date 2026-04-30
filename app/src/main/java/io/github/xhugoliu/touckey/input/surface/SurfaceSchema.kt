package io.github.xhugoliu.touckey.input.surface

import io.github.xhugoliu.touckey.input.InputAction

data class SurfaceLayoutProfile(
    val id: String,
    val pages: List<SurfacePage>,
)

data class SurfacePage(
    val id: String,
    val label: String,
    val variants: List<SurfaceLayoutVariant>,
)

enum class ViewportClass {
    Compact,
    Expanded,
}

enum class SurfaceOrientation {
    Portrait,
    Landscape,
    Any,
}

enum class VariantAlignment {
    TopStart,
    Center,
}

data class SurfaceLayoutVariant(
    val id: String,
    val viewportClass: ViewportClass,
    val orientation: SurfaceOrientation,
    val alignment: VariantAlignment,
    val grid: SurfaceGrid,
    val zones: List<SurfaceZone>,
    val generated: Boolean = false,
    val readOnly: Boolean = false,
)

data class SurfaceGrid(
    val columnsU: Float,
    val rowsU: Float,
    val snapU: Float = DEFAULT_SNAP_U,
    val gapU: Float = 0f,
    val hardMinTouchDp: Float = HARD_MIN_TOUCH_DP,
    val recommendedMinTouchDp: Float = RECOMMENDED_MIN_TOUCH_DP,
) {
    companion object {
        const val DEFAULT_SNAP_U = 0.25f
        const val HARD_MIN_TOUCH_DP = 36f
        const val RECOMMENDED_MIN_TOUCH_DP = 48f
    }
}

data class SurfaceZone(
    val id: String,
    val role: SurfaceZoneRole,
    val label: SurfaceLabel,
    val frame: UnitFrame,
    val enabled: Boolean = true,
)

enum class SurfaceZoneRole {
    Key,
    Modifier,
    Function,
    Navigation,
    System,
    PointerSurface,
    ScrollSurface,
    Decoration,
}

val SurfaceZoneRole.isInteractive: Boolean
    get() = this != SurfaceZoneRole.Decoration

val SurfaceZoneRole.isKeyboardLike: Boolean
    get() =
        when (this) {
            SurfaceZoneRole.Key,
            SurfaceZoneRole.Modifier,
            SurfaceZoneRole.Function,
            SurfaceZoneRole.Navigation,
            SurfaceZoneRole.System,
            -> true
            SurfaceZoneRole.PointerSurface,
            SurfaceZoneRole.ScrollSurface,
            SurfaceZoneRole.Decoration,
            -> false
        }

data class SurfaceLabel(
    val text: String,
    val shiftedText: String? = null,
)

data class UnitFrame(
    val xU: Float,
    val yU: Float,
    val widthU: Float,
    val heightU: Float,
) {
    val rightU: Float
        get() = xU + widthU

    val bottomU: Float
        get() = yU + heightU

    fun overlaps(other: UnitFrame): Boolean =
        xU < other.rightU &&
            rightU > other.xU &&
            yU < other.bottomU &&
            bottomU > other.yU
}

data class KeymapProfile(
    val id: String,
    val layers: List<KeymapLayer>,
) {
    fun bindingFor(
        zoneId: String,
        layerId: String = DEFAULT_LAYER_ID,
        pageId: String? = null,
    ): BehaviorBinding? =
        layers
            .firstOrNull { layer -> layer.id == layerId && (pageId == null || layer.pageId == pageId) }
            ?.bindings
            ?.get(zoneId)

    companion object {
        const val DEFAULT_LAYER_ID = "default"
    }
}

data class KeymapLayer(
    val id: String,
    val pageId: String,
    val bindings: Map<String, BehaviorBinding>,
)

sealed interface BehaviorBinding {
    data class Key(
        val key: String,
    ) : BehaviorBinding

    data class ConsumerControl(
        val usage: String,
    ) : BehaviorBinding

    data class PointerSurface(
        val sensitivity: Float = 1f,
    ) : BehaviorBinding

    data class ScrollSurface(
        val scale: Float = 1f,
    ) : BehaviorBinding
}

data class PixelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    fun contains(
        x: Float,
        y: Float,
    ): Boolean = x >= left && x < right && y >= top && y < bottom
}

data class RenderedZone(
    val zone: SurfaceZone,
    val bounds: PixelRect,
)

data class RenderedSurface(
    val pageId: String,
    val variant: SurfaceLayoutVariant,
    val unitPx: Float,
    val offsetX: Float,
    val offsetY: Float,
    val zones: List<RenderedZone>,
)

sealed interface SurfaceEvent {
    val zoneId: String

    data class ZoneDown(
        override val zoneId: String,
        val pointerId: Int,
        val x: Float,
        val y: Float,
    ) : SurfaceEvent

    data class ZoneUp(
        override val zoneId: String,
        val pointerId: Int,
        val x: Float,
        val y: Float,
    ) : SurfaceEvent

    data class ZoneTap(
        override val zoneId: String,
        val pointerId: Int,
        val x: Float,
        val y: Float,
    ) : SurfaceEvent

    data class ZoneDrag(
        override val zoneId: String,
        val pointerId: Int,
        val deltaX: Float,
        val deltaY: Float,
    ) : SurfaceEvent

    data class ZoneCancel(
        override val zoneId: String,
        val pointerId: Int,
    ) : SurfaceEvent
}

data class SurfaceDispatch(
    val actions: List<InputAction>,
    val shouldSurfaceResult: Boolean = false,
)
