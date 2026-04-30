package io.github.xhugoliu.touckey.input.surface

import io.github.xhugoliu.touckey.hid.HidCapabilityCatalog
import kotlin.math.abs
import kotlin.math.roundToInt

data class SurfaceValidationResult(
    val errors: List<SurfaceValidationIssue>,
    val warnings: List<SurfaceValidationIssue>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

data class SurfaceValidationIssue(
    val code: SurfaceValidationCode,
    val message: String,
    val subjectId: String? = null,
)

enum class SurfaceValidationCode {
    DuplicateId,
    InvalidGrid,
    InvalidFrame,
    ZoneOutsideGrid,
    OverlappingInteractiveZones,
    MissingBinding,
    UnknownBindingZone,
    UnsupportedHidUsage,
    BelowHardMinimumTouchSize,
    BelowRecommendedTouchSize,
    LikelyLabelOverflow,
    UnusedBinding,
}

object SurfaceProfileValidator {
    fun validate(
        layoutProfile: SurfaceLayoutProfile,
        keymapProfile: KeymapProfile,
    ): SurfaceValidationResult {
        val errors = mutableListOf<SurfaceValidationIssue>()
        val warnings = mutableListOf<SurfaceValidationIssue>()
        val pageIds = layoutProfile.pages.map { it.id }

        errors += duplicateIssues(pageIds, "page")
        errors += duplicateIssues(keymapProfile.layers.map { "${it.pageId}:${it.id}" }, "keymap layer")

        layoutProfile.pages.forEach { page ->
            errors += validatePage(page)
            errors += validateKeymapForPage(page, keymapProfile, warnings)
        }

        keymapProfile.layers
            .filterNot { it.pageId in pageIds }
            .forEach { layer ->
                errors +=
                    SurfaceValidationIssue(
                        code = SurfaceValidationCode.UnknownBindingZone,
                        message = "Keymap layer ${layer.id} references unknown page ${layer.pageId}.",
                        subjectId = layer.pageId,
                    )
            }

        return SurfaceValidationResult(errors = errors, warnings = warnings)
    }

    fun validateRendered(
        layoutProfile: SurfaceLayoutProfile,
        keymapProfile: KeymapProfile,
        renderedSurface: RenderedSurface,
        density: Float = 1f,
    ): SurfaceValidationResult {
        val base = validate(layoutProfile, keymapProfile)
        val errors = base.errors.toMutableList()
        val warnings = base.warnings.toMutableList()
        val safeDensity = density.takeIf { it > 0f } ?: 1f

        renderedSurface.zones
            .filter { it.zone.enabled && it.zone.role.isInteractive }
            .forEach { renderedZone ->
                val widthDp = renderedZone.bounds.width / safeDensity
                val heightDp = renderedZone.bounds.height / safeDensity
                val minSizeDp = minOf(widthDp, heightDp)
                val grid = renderedSurface.variant.grid

                if (minSizeDp < grid.hardMinTouchDp) {
                    errors +=
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.BelowHardMinimumTouchSize,
                            message = "Rendered zone ${renderedZone.zone.id} is ${minSizeDp.format()}dp, below hard minimum ${grid.hardMinTouchDp.format()}dp.",
                            subjectId = renderedZone.zone.id,
                        )
                } else if (minSizeDp < grid.recommendedMinTouchDp) {
                    warnings +=
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.BelowRecommendedTouchSize,
                            message = "Rendered zone ${renderedZone.zone.id} is ${minSizeDp.format()}dp, below recommended minimum ${grid.recommendedMinTouchDp.format()}dp.",
                            subjectId = renderedZone.zone.id,
                        )
                }

                val likelyTextWidthDp = renderedZone.zone.label.text.length * APPROX_LABEL_CHARACTER_WIDTH_DP
                if (likelyTextWidthDp > widthDp) {
                    warnings +=
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.LikelyLabelOverflow,
                            message = "Label for ${renderedZone.zone.id} may overflow its rendered width.",
                            subjectId = renderedZone.zone.id,
                        )
                }
            }

        return SurfaceValidationResult(errors = errors, warnings = warnings)
    }

    private fun validatePage(page: SurfacePage): List<SurfaceValidationIssue> {
        val errors = mutableListOf<SurfaceValidationIssue>()

        errors += duplicateIssues(page.variants.map { it.id }, "variant")
        page.variants.forEach { variant ->
            errors += validateVariant(variant)
        }

        return errors
    }

    private fun validateVariant(variant: SurfaceLayoutVariant): List<SurfaceValidationIssue> {
        val errors = mutableListOf<SurfaceValidationIssue>()
        val grid = variant.grid

        if (grid.columnsU <= 0f || grid.rowsU <= 0f || grid.snapU <= 0f || grid.gapU < 0f) {
            errors +=
                SurfaceValidationIssue(
                    code = SurfaceValidationCode.InvalidGrid,
                    message = "Variant ${variant.id} has invalid grid dimensions.",
                    subjectId = variant.id,
                )
        }

        errors += duplicateIssues(variant.zones.map { it.id }, "zone")

        variant.zones.forEach { zone ->
            errors += validateZoneFrame(variant, zone)
        }

        val interactiveZones = variant.zones.filter { it.enabled && it.role.isInteractive }
        interactiveZones.forEachIndexed { index, zone ->
            interactiveZones.drop(index + 1).forEach { other ->
                if (zone.frame.overlaps(other.frame)) {
                    errors +=
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.OverlappingInteractiveZones,
                            message = "Interactive zones ${zone.id} and ${other.id} overlap in variant ${variant.id}.",
                            subjectId = "${zone.id}:${other.id}",
                        )
                }
            }
        }

        return errors
    }

    private fun validateZoneFrame(
        variant: SurfaceLayoutVariant,
        zone: SurfaceZone,
    ): List<SurfaceValidationIssue> {
        val grid = variant.grid
        val frame = zone.frame
        val errors = mutableListOf<SurfaceValidationIssue>()

        if (frame.widthU <= 0f || frame.heightU <= 0f || frame.xU < 0f || frame.yU < 0f) {
            errors +=
                SurfaceValidationIssue(
                    code = SurfaceValidationCode.InvalidFrame,
                    message = "Zone ${zone.id} has a non-positive or negative frame.",
                    subjectId = zone.id,
                )
        }

        listOf(frame.xU, frame.yU, frame.widthU, frame.heightU).forEach { value ->
            if (!value.isSnappedTo(grid.snapU)) {
                errors +=
                    SurfaceValidationIssue(
                        code = SurfaceValidationCode.InvalidFrame,
                        message = "Zone ${zone.id} frame value ${value.format()}U is not snapped to ${grid.snapU.format()}U.",
                        subjectId = zone.id,
                    )
            }
        }

        if (frame.rightU > grid.columnsU + FLOAT_EPSILON || frame.bottomU > grid.rowsU + FLOAT_EPSILON) {
            errors +=
                SurfaceValidationIssue(
                    code = SurfaceValidationCode.ZoneOutsideGrid,
                    message = "Zone ${zone.id} extends outside grid ${grid.columnsU.format()}x${grid.rowsU.format()}U.",
                    subjectId = zone.id,
                )
        }

        return errors
    }

    private fun validateKeymapForPage(
        page: SurfacePage,
        keymapProfile: KeymapProfile,
        warnings: MutableList<SurfaceValidationIssue>,
    ): List<SurfaceValidationIssue> {
        val errors = mutableListOf<SurfaceValidationIssue>()
        val pageZoneById = page.variants.flatMap { it.zones }.associateBy { it.id }
        val interactiveZoneIds =
            pageZoneById.values
                .filter { it.enabled && it.role.isInteractive }
                .map { it.id }
                .toSet()
        val layers = keymapProfile.layers.filter { it.pageId == page.id }

        if (layers.isEmpty()) {
            interactiveZoneIds.forEach { zoneId ->
                errors +=
                    SurfaceValidationIssue(
                        code = SurfaceValidationCode.MissingBinding,
                        message = "Interactive zone $zoneId on page ${page.id} has no keymap layer.",
                        subjectId = zoneId,
                    )
            }
            return errors
        }

        layers.forEach { layer ->
            interactiveZoneIds
                .filterNot { it in layer.bindings }
                .forEach { zoneId ->
                    errors +=
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.MissingBinding,
                            message = "Interactive zone $zoneId has no binding in layer ${layer.id}.",
                            subjectId = zoneId,
                        )
                }

            layer.bindings.forEach { (zoneId, binding) ->
                val zone = pageZoneById[zoneId]
                if (zone == null) {
                    errors +=
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.UnknownBindingZone,
                            message = "Binding $zoneId in layer ${layer.id} references an unknown zone.",
                            subjectId = zoneId,
                        )
                    return@forEach
                }

                if (!zone.enabled || !zone.role.isInteractive) {
                    warnings +=
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.UnusedBinding,
                            message = "Binding $zoneId targets a disabled or non-interactive zone.",
                            subjectId = zoneId,
                        )
                }

                errors += validateBinding(zoneId, binding)
            }
        }

        return errors
    }

    private fun validateBinding(
        zoneId: String,
        binding: BehaviorBinding,
    ): List<SurfaceValidationIssue> =
        when (binding) {
            is BehaviorBinding.Key ->
                if (HidCapabilityCatalog.supportsKeyboardInput(binding.key)) {
                    emptyList()
                } else {
                    listOf(
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.UnsupportedHidUsage,
                            message = "Zone $zoneId uses unsupported keyboard input ${binding.key}.",
                            subjectId = zoneId,
                        ),
                    )
                }

            is BehaviorBinding.ConsumerControl ->
                if (HidCapabilityCatalog.supportsConsumerUsage(binding.usage)) {
                    emptyList()
                } else {
                    listOf(
                        SurfaceValidationIssue(
                            code = SurfaceValidationCode.UnsupportedHidUsage,
                            message = "Zone $zoneId uses unsupported consumer control ${binding.usage}.",
                            subjectId = zoneId,
                        ),
                    )
                }

            is BehaviorBinding.PointerSurface,
            is BehaviorBinding.ScrollSurface,
            -> emptyList()
        }

    private fun duplicateIssues(
        ids: List<String>,
        label: String,
    ): List<SurfaceValidationIssue> =
        ids
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .map { id ->
                SurfaceValidationIssue(
                    code = SurfaceValidationCode.DuplicateId,
                    message = "Duplicate $label id $id.",
                    subjectId = id,
                )
            }

    private fun Float.isSnappedTo(snapU: Float): Boolean {
        if (snapU <= 0f) {
            return false
        }
        val quotient = this / snapU
        return abs(quotient - quotient.roundToInt()) <= FLOAT_EPSILON
    }

    private fun Float.format(): String = "%.2f".format(this)

    private const val FLOAT_EPSILON = 0.001f
    private const val APPROX_LABEL_CHARACTER_WIDTH_DP = 7f
}
