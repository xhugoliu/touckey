package io.github.xhugoliu.touckey.input.surface

import io.github.xhugoliu.touckey.hid.HidCapabilityCatalog
import io.github.xhugoliu.touckey.input.InputAction
import kotlin.math.roundToInt

enum class BehaviorModifierMode {
    Preset,
    Hold,
}

data class BehaviorState(
    val modifierMode: BehaviorModifierMode,
    val armedModifiers: List<String> = emptyList(),
    val activeHoldKeys: List<String> = emptyList(),
)

data class BehaviorReducerResult(
    val nextState: BehaviorState,
    val dispatch: SurfaceDispatch = SurfaceDispatch(emptyList()),
)

object BehaviorReducer {
    fun reduce(
        event: SurfaceEvent,
        keymapProfile: KeymapProfile,
        state: BehaviorState,
        layerId: String = KeymapProfile.DEFAULT_LAYER_ID,
        pageId: String? = null,
    ): BehaviorReducerResult {
        val binding = keymapProfile.bindingFor(event.zoneId, layerId, pageId) ?: return BehaviorReducerResult(state)

        return when (event) {
            is SurfaceEvent.ZoneDown -> reduceZoneDown(binding, state)
            is SurfaceEvent.ZoneUp -> reduceZoneUp(binding, state)
            is SurfaceEvent.ZoneTap -> reduceZoneTap(binding, state)
            is SurfaceEvent.ZoneDrag -> reduceZoneDrag(event, binding, state)
            is SurfaceEvent.ZoneCancel -> reduceZoneCancel(binding, state)
        }
    }

    private fun reduceZoneDown(
        binding: BehaviorBinding,
        state: BehaviorState,
    ): BehaviorReducerResult {
        if (state.modifierMode != BehaviorModifierMode.Hold || binding !is BehaviorBinding.Key) {
            return BehaviorReducerResult(state)
        }
        if (binding.key in state.activeHoldKeys) {
            return BehaviorReducerResult(state)
        }

        return BehaviorReducerResult(
            nextState = state.copy(activeHoldKeys = state.activeHoldKeys + binding.key),
            dispatch = SurfaceDispatch(listOf(InputAction.KeyPressAction(binding.key))),
        )
    }

    private fun reduceZoneUp(
        binding: BehaviorBinding,
        state: BehaviorState,
    ): BehaviorReducerResult {
        if (state.modifierMode != BehaviorModifierMode.Hold || binding !is BehaviorBinding.Key) {
            return BehaviorReducerResult(state)
        }
        if (binding.key !in state.activeHoldKeys) {
            return BehaviorReducerResult(state)
        }

        return BehaviorReducerResult(
            nextState = state.copy(activeHoldKeys = state.activeHoldKeys - binding.key),
            dispatch = SurfaceDispatch(listOf(InputAction.KeyReleaseAction(binding.key))),
        )
    }

    private fun reduceZoneTap(
        binding: BehaviorBinding,
        state: BehaviorState,
    ): BehaviorReducerResult =
        when (binding) {
            is BehaviorBinding.Key -> reduceKeyTap(binding.key, state)
            is BehaviorBinding.ConsumerControl ->
                BehaviorReducerResult(
                    nextState = state,
                    dispatch = SurfaceDispatch(listOf(InputAction.ConsumerControlAction(binding.usage))),
                )
            is BehaviorBinding.PointerSurface,
            is BehaviorBinding.ScrollSurface,
            -> BehaviorReducerResult(state)
        }

    private fun reduceKeyTap(
        key: String,
        state: BehaviorState,
    ): BehaviorReducerResult {
        if (state.modifierMode == BehaviorModifierMode.Preset && HidCapabilityCatalog.isModifier(key)) {
            return BehaviorReducerResult(
                nextState = state.copy(armedModifiers = toggle(state.armedModifiers, key)),
            )
        }
        if (state.modifierMode == BehaviorModifierMode.Hold) {
            return BehaviorReducerResult(state)
        }

        return BehaviorReducerResult(
            nextState = state.copy(armedModifiers = emptyList()),
            dispatch =
                SurfaceDispatch(
                    listOf(
                        InputAction.KeyComboAction(
                            keys = listOf(key),
                            modifiers = state.armedModifiers,
                        ),
                    ),
                ),
        )
    }

    private fun reduceZoneDrag(
        event: SurfaceEvent.ZoneDrag,
        binding: BehaviorBinding,
        state: BehaviorState,
    ): BehaviorReducerResult =
        when (binding) {
            is BehaviorBinding.PointerSurface ->
                BehaviorReducerResult(
                    nextState = state,
                    dispatch =
                        SurfaceDispatch(
                            listOf(
                                InputAction.PointerMoveAction(
                                    deltaX = event.deltaX * binding.sensitivity,
                                    deltaY = event.deltaY * binding.sensitivity,
                                ),
                            ),
                        ),
                )
            is BehaviorBinding.ScrollSurface ->
                BehaviorReducerResult(
                    nextState = state,
                    dispatch =
                        SurfaceDispatch(
                            listOf(
                                InputAction.ScrollAction(
                                    vertical = (-event.deltaY * binding.scale).roundToInt(),
                                    horizontal = (-event.deltaX * binding.scale).roundToInt(),
                                ),
                            ),
                        ),
                )
            is BehaviorBinding.Key,
            is BehaviorBinding.ConsumerControl,
            -> BehaviorReducerResult(state)
        }

    private fun reduceZoneCancel(
        binding: BehaviorBinding,
        state: BehaviorState,
    ): BehaviorReducerResult {
        if (state.modifierMode != BehaviorModifierMode.Hold || binding !is BehaviorBinding.Key || binding.key !in state.activeHoldKeys) {
            return BehaviorReducerResult(state)
        }

        return BehaviorReducerResult(
            nextState = state.copy(activeHoldKeys = state.activeHoldKeys - binding.key),
            dispatch = SurfaceDispatch(listOf(InputAction.KeyReleaseAction(binding.key))),
        )
    }

    private fun toggle(
        values: List<String>,
        value: String,
    ): List<String> =
        if (value in values) {
            values - value
        } else {
            values + value
        }
}
