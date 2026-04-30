package io.github.xhugoliu.touckey.input.surface

import io.github.xhugoliu.touckey.input.InputAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorReducerTest {
    @Test
    fun `preset modifier behavior remains compatible`() {
        val shiftResult =
            BehaviorReducer.reduce(
                event = tap("shift"),
                keymapProfile = keymap("shift" to BehaviorBinding.Key("Shift"), "a" to BehaviorBinding.Key("A")),
                state = BehaviorState(modifierMode = BehaviorModifierMode.Preset),
            )
        val aResult =
            BehaviorReducer.reduce(
                event = tap("a"),
                keymapProfile = keymap("shift" to BehaviorBinding.Key("Shift"), "a" to BehaviorBinding.Key("A")),
                state = shiftResult.nextState,
            )

        assertEquals(listOf("Shift"), shiftResult.nextState.armedModifiers)
        assertEquals(
            listOf(InputAction.KeyComboAction(keys = listOf("A"), modifiers = listOf("Shift"))),
            aResult.dispatch.actions,
        )
        assertEquals(emptyList<String>(), aResult.nextState.armedModifiers)
    }

    @Test
    fun `hold modifier behavior remains compatible`() {
        val profile = keymap("shift" to BehaviorBinding.Key("Shift"))
        val down =
            BehaviorReducer.reduce(
                event = down("shift"),
                keymapProfile = profile,
                state = BehaviorState(modifierMode = BehaviorModifierMode.Hold),
            )
        val up =
            BehaviorReducer.reduce(
                event = up("shift"),
                keymapProfile = profile,
                state = down.nextState,
            )

        assertEquals(listOf(InputAction.KeyPressAction("Shift")), down.dispatch.actions)
        assertEquals(listOf("Shift"), down.nextState.activeHoldKeys)
        assertEquals(listOf(InputAction.KeyReleaseAction("Shift")), up.dispatch.actions)
        assertEquals(emptyList<String>(), up.nextState.activeHoldKeys)
    }

    @Test
    fun `regular key tap produces existing input action output`() {
        val result =
            BehaviorReducer.reduce(
                event = tap("a"),
                keymapProfile = keymap("a" to BehaviorBinding.Key("A")),
                state = BehaviorState(modifierMode = BehaviorModifierMode.Preset, armedModifiers = listOf("Cmd")),
            )

        assertEquals(
            listOf(InputAction.KeyComboAction(keys = listOf("A"), modifiers = listOf("Cmd"))),
            result.dispatch.actions,
        )
        assertEquals(emptyList<String>(), result.nextState.armedModifiers)
    }

    @Test
    fun `consumer control binding produces dispatch action`() {
        val result =
            BehaviorReducer.reduce(
                event = tap("media"),
                keymapProfile = keymap("media" to BehaviorBinding.ConsumerControl("PlayPause")),
                state = BehaviorState(modifierMode = BehaviorModifierMode.Preset),
            )

        assertEquals(listOf(InputAction.ConsumerControlAction("PlayPause")), result.dispatch.actions)
    }

    @Test
    fun `pointer and scroll surface bindings dispatch drag actions without key taps`() {
        val profile =
            keymap(
                "pointer" to BehaviorBinding.PointerSurface(sensitivity = 2f),
                "scroll" to BehaviorBinding.ScrollSurface(scale = 3f),
            )

        val pointer =
            BehaviorReducer.reduce(
                event = SurfaceEvent.ZoneDrag(zoneId = "pointer", pointerId = 1, deltaX = 2f, deltaY = -3f),
                keymapProfile = profile,
                state = BehaviorState(modifierMode = BehaviorModifierMode.Preset),
            )
        val scroll =
            BehaviorReducer.reduce(
                event = SurfaceEvent.ZoneDrag(zoneId = "scroll", pointerId = 1, deltaX = 2f, deltaY = -3f),
                keymapProfile = profile,
                state = BehaviorState(modifierMode = BehaviorModifierMode.Preset),
            )
        val pointerTap =
            BehaviorReducer.reduce(
                event = tap("pointer"),
                keymapProfile = profile,
                state = BehaviorState(modifierMode = BehaviorModifierMode.Preset),
            )

        assertEquals(listOf(InputAction.PointerMoveAction(deltaX = 4f, deltaY = -6f)), pointer.dispatch.actions)
        assertEquals(listOf(InputAction.ScrollAction(vertical = 9, horizontal = -6)), scroll.dispatch.actions)
        assertTrue(pointerTap.dispatch.actions.isEmpty())
    }

    private fun keymap(vararg bindings: Pair<String, BehaviorBinding>): KeymapProfile =
        KeymapProfile(
            id = "keymap",
            layers =
                listOf(
                    KeymapLayer(
                        id = KeymapProfile.DEFAULT_LAYER_ID,
                        pageId = "page",
                        bindings = bindings.toMap(),
                    ),
                ),
        )

    private fun tap(zoneId: String): SurfaceEvent.ZoneTap =
        SurfaceEvent.ZoneTap(zoneId = zoneId, pointerId = 1, x = 0f, y = 0f)

    private fun down(zoneId: String): SurfaceEvent.ZoneDown =
        SurfaceEvent.ZoneDown(zoneId = zoneId, pointerId = 1, x = 0f, y = 0f)

    private fun up(zoneId: String): SurfaceEvent.ZoneUp =
        SurfaceEvent.ZoneUp(zoneId = zoneId, pointerId = 1, x = 0f, y = 0f)
}
