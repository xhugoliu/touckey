package io.github.xhugoliu.touckey.feature.control

import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabInteractionTest {
    @Test
    fun `tap returns candidate to center`() {
        val move =
            applyLabGesture(
                cell = LabCell(row = 0, column = 4),
                gesture = LabGesture.Tap,
            )

        assertEquals(LabCell(), move.nextCell)
        assertTrue(move.moved)
        assertFalse(move.blocked)
    }

    @Test
    fun `directional gesture moves candidate inside matrix`() {
        val move =
            applyLabGesture(
                cell = LabCell(row = 2, column = 2),
                gesture = LabGesture.Up,
            )

        assertEquals(LabCell(row = 1, column = 2), move.nextCell)
        assertTrue(move.moved)
        assertFalse(move.blocked)
    }

    @Test
    fun `directional gesture blocks at matrix edge`() {
        val move =
            applyLabGesture(
                cell = LabCell(row = 0, column = 4),
                gesture = LabGesture.Up,
            )

        assertEquals(LabCell(row = 0, column = 4), move.nextCell)
        assertFalse(move.moved)
        assertTrue(move.blocked)
    }

    @Test
    fun `gesture detector recognizes taps and four directions`() {
        val threshold = 10f

        assertEquals(LabGesture.Tap, detectLabGesture(deltaX = 3f, deltaY = 2f, minSwipePx = threshold))
        assertEquals(LabGesture.Up, detectLabGesture(deltaX = 0f, deltaY = -20f, minSwipePx = threshold))
        assertEquals(LabGesture.Down, detectLabGesture(deltaX = 0f, deltaY = 20f, minSwipePx = threshold))
        assertEquals(LabGesture.Left, detectLabGesture(deltaX = -20f, deltaY = 0f, minSwipePx = threshold))
        assertEquals(LabGesture.Right, detectLabGesture(deltaX = 20f, deltaY = 0f, minSwipePx = threshold))
        assertEquals(LabGesture.Right, detectLabGesture(deltaX = 20f, deltaY = -20f, minSwipePx = threshold))
        assertEquals(LabGesture.Down, detectLabGesture(deltaX = -19f, deltaY = 20f, minSwipePx = threshold))
    }

    @Test
    fun `cell virtual ids use side prefix and one based coordinates`() {
        val cell = LabCell(row = 2, column = 2)

        assertEquals("L33", cell.virtualId(LabSide.Left))
        assertEquals("R33", cell.virtualId(LabSide.Right))
    }

    @Test
    fun `lab key binding maps left and right matrices to expected keys`() {
        assertEquals(
            LabKeyBinding(key = "D", label = "D"),
            labBinding(LabSide.Left, LabCell(row = 2, column = 2)),
        )
        assertEquals(
            LabKeyBinding(key = "Cmd", label = "GUI"),
            labBinding(LabSide.Left, LabCell(row = 4, column = 0)),
        )
        assertEquals(
            LabKeyBinding(key = "Enter", label = "Enter"),
            labBinding(LabSide.Right, LabCell(row = 0, column = 2)),
        )
        assertEquals(
            LabKeyBinding(key = "\\", label = "\\"),
            labBinding(LabSide.Right, LabCell(row = 4, column = 3)),
        )
    }

    @Test
    fun `fn number layer maps extended function keys keypad operators and none cells`() {
        assertEquals(
            LabKeyBinding(key = "F21", label = "F21"),
            labBinding(LabSide.Left, LabCell(row = 4, column = 3), LabLayer.FnNumber),
        )
        assertEquals(
            LabKeyBinding(key = "KeypadPlus", label = "+"),
            labBinding(LabSide.Right, LabCell(row = 1, column = 0), LabLayer.FnNumber),
        )
        assertEquals(
            LabKeyBinding(key = "KeypadPeriod", label = "."),
            labBinding(LabSide.Right, LabCell(row = 4, column = 2), LabLayer.FnNumber),
        )
        assertEquals(
            LabEmptyBinding,
            labBinding(LabSide.Right, LabCell(row = 0, column = 1), LabLayer.FnNumber),
        )
    }

    @Test
    fun `mouse navigation layer maps pointer scroll mouse and arrow bindings`() {
        assertEquals(
            LabScrollBinding(vertical = 6, label = "Scr U"),
            labBinding(LabSide.Left, LabCell(row = 0, column = 2), LabLayer.MouseNav),
        )
        assertEquals(
            LabPointerMoveBinding(deltaX = 10f, deltaY = -10f, label = "Ptr UR"),
            labBinding(LabSide.Left, LabCell(row = 1, column = 3), LabLayer.MouseNav),
        )
        assertEquals(
            LabMouseButtonBinding(button = MouseButton.Forward, label = "M5"),
            labBinding(LabSide.Right, LabCell(row = 3, column = 3), LabLayer.MouseNav),
        )
        assertEquals(
            LabKeyBinding(key = "PageDown", label = "PgDn"),
            labBinding(LabSide.Right, LabCell(row = 4, column = 2), LabLayer.MouseNav),
        )
    }

    @Test
    fun `only ctrl shift alt and cmd are lockable modifiers`() {
        assertTrue(isLockableModifier("Ctrl"))
        assertTrue(isLockableModifier("Shift"))
        assertTrue(isLockableModifier("Alt"))
        assertTrue(isLockableModifier("Cmd"))
        assertFalse(isLockableModifier("Enter"))
        assertFalse(isLockableModifier("C"))
    }

    @Test
    fun `locked modifiers preserve order and can be toggled off`() {
        val first = toggleLockedModifier(emptyList(), "Ctrl")
        val second = toggleLockedModifier(first, "Shift")
        val removed = toggleLockedModifier(second, "Ctrl")

        assertEquals(listOf("Ctrl"), first)
        assertEquals(listOf("Ctrl", "Shift"), second)
        assertEquals(listOf("Shift"), removed)
    }

    @Test
    fun `hold is interrupted only by a successful candidate move`() {
        val moved =
            applyLabGesture(
                cell = LabCell(),
                gesture = LabGesture.Right,
            )
        val blocked =
            applyLabGesture(
                cell = LabCell(row = 0, column = 4),
                gesture = LabGesture.Right,
            )
        val reset =
            applyLabGesture(
                cell = LabCell(row = 0, column = 4),
                gesture = LabGesture.Tap,
            )

        assertEquals("Enter", interruptedLabHoldKey("Enter", moved))
        assertEquals(null, interruptedLabHoldKey("Enter", blocked))
        assertEquals("Enter", interruptedLabHoldKey("Enter", reset))
        assertEquals(null, interruptedLabHoldKey(null, moved))
    }

    @Test
    fun `candidate move clears locked modifiers only when movement succeeds`() {
        val moved =
            applyLabGesture(
                cell = LabCell(),
                gesture = LabGesture.Right,
            )
        val blocked =
            applyLabGesture(
                cell = LabCell(row = 0, column = 4),
                gesture = LabGesture.Right,
            )

        assertEquals(listOf("Ctrl", "Shift"), interruptedLockedModifiers(listOf("Ctrl", "Shift"), moved))
        assertEquals(listOf("Ctrl", "Shift"), interruptedLockedModifiers(listOf("Ctrl", "Shift"), blocked))
    }

    @Test
    fun `hold press and release actions include locked modifiers in order`() {
        assertEquals(
            listOf(
                InputAction.KeyPressAction("Ctrl"),
                InputAction.KeyPressAction("Shift"),
                InputAction.KeyPressAction("Cmd"),
            ),
            labHoldPressActions(activeKey = "Cmd", lockedModifiers = listOf("Ctrl", "Shift")),
        )
        assertEquals(
            listOf(
                InputAction.KeyReleaseAction("Cmd"),
                InputAction.KeyReleaseAction("Shift"),
                InputAction.KeyReleaseAction("Ctrl"),
            ),
            labHoldReleaseActions(activeKey = "Cmd", lockedModifiers = listOf("Ctrl", "Shift")),
        )
    }

    @Test
    fun `state transition actions support independent left and right holds`() {
        val previous =
            listOf(
                LabTriggerState(lockedModifiers = listOf("Ctrl"), activeHoldBinding = LabKeyBinding("C")),
                LabTriggerState(),
            )
        val next =
            listOf(
                LabTriggerState(lockedModifiers = listOf("Ctrl"), activeHoldBinding = LabKeyBinding("C")),
                LabTriggerState(lockedModifiers = listOf("Shift"), activeHoldBinding = LabKeyBinding("M")),
            )
        val released =
            listOf(
                LabTriggerState(),
                LabTriggerState(lockedModifiers = listOf("Shift"), activeHoldBinding = LabKeyBinding("M")),
            )

        assertEquals(
            listOf(
                InputAction.KeyPressAction("Shift"),
                InputAction.KeyPressAction("M"),
            ),
            labStateTransitionActions(previous, next),
        )
        assertEquals(
            listOf(
                InputAction.KeyReleaseAction("C"),
                InputAction.KeyReleaseAction("Ctrl"),
            ),
            labStateTransitionActions(next, released),
        )
    }

    @Test
    fun `tap actions reuse hold press and release order`() {
        assertEquals(
            listOf<InputAction>(
                InputAction.KeyPressAction("Ctrl"),
                InputAction.KeyPressAction("C"),
                InputAction.KeyReleaseAction("C"),
                InputAction.KeyReleaseAction("Ctrl"),
            ),
            labTapActions(activeKey = "C", lockedModifiers = listOf("Ctrl")),
        )
    }

    @Test
    fun `promoting held modifier into locked modifier does not emit release`() {
        val previous =
            listOf(
                LabTriggerState(activeHoldBinding = LabKeyBinding("Ctrl")),
                LabTriggerState(),
            )
        val next =
            listOf(
                LabTriggerState(lockedModifiers = listOf("Ctrl")),
                LabTriggerState(),
            )

        assertEquals(
            emptyList<InputAction>(),
            labStateTransitionActions(previous, next),
        )
    }

    @Test
    fun `mouse button hold emits button press and release transitions`() {
        val previous =
            listOf(
                LabTriggerState(activeHoldBinding = LabMouseButtonBinding(MouseButton.Left, "M1")),
                LabTriggerState(),
            )
        val next =
            listOf(
                LabTriggerState(activeHoldBinding = LabMouseButtonBinding(MouseButton.Left, "M1")),
                LabTriggerState(activeHoldBinding = LabMouseButtonBinding(MouseButton.Forward, "M5")),
            )
        val released =
            listOf(
                LabTriggerState(),
                LabTriggerState(activeHoldBinding = LabMouseButtonBinding(MouseButton.Forward, "M5")),
            )

        assertEquals(
            listOf<InputAction>(
                InputAction.MouseButtonPressAction(MouseButton.Forward),
            ),
            labStateTransitionActions(previous, next),
        )
        assertEquals(
            listOf<InputAction>(
                InputAction.MouseButtonReleaseAction(MouseButton.Left),
            ),
            labStateTransitionActions(next, released),
        )
    }

    @Test
    fun `inner and outer paired horizontal gestures switch layers`() {
        assertEquals(
            LabLayerSwitch.Next,
            labLayerSwitchForGestures(LabGesture.Right, LabGesture.Left),
        )
        assertEquals(
            LabLayerSwitch.Previous,
            labLayerSwitchForGestures(LabGesture.Left, LabGesture.Right),
        )
        assertEquals(null, labLayerSwitchForGestures(LabGesture.Right, LabGesture.Right))

        assertEquals(LabLayer.FnNumber, labLayerAfter(LabLayer.Default, LabLayerSwitch.Next))
        assertEquals(LabLayer.MouseNav, labLayerAfter(LabLayer.FnNumber, LabLayerSwitch.Next))
        assertEquals(LabLayer.Default, labLayerAfter(LabLayer.MouseNav, LabLayerSwitch.Next))
        assertEquals(LabLayer.MouseNav, labLayerAfter(LabLayer.Default, LabLayerSwitch.Previous))
    }

    @Test
    fun `layer switch resets candidate and trigger state`() {
        assertEquals(LabCell(), labResetCellForLayerSwitch())
        assertEquals(
            LabTriggerState(),
            labResetTriggerStateForLayerSwitch(),
        )
    }

    @Test
    fun `drag segmentation emits repeated orthogonal moves`() {
        val segments =
            segmentLabDrag(
                deltaX = 25f,
                deltaY = 3f,
                segmentPx = 10f,
            )

        assertEquals(listOf(LabGesture.Right, LabGesture.Right), segments.gestures)
        assertEquals(20f, segments.consumedX)
        assertEquals(0f, segments.consumedY)
    }

    @Test
    fun `drag segmentation converts mixed axis drag into four way moves`() {
        val segments =
            segmentLabDrag(
                deltaX = 24f,
                deltaY = -25f,
                segmentPx = 10f,
            )

        assertEquals(listOf(LabGesture.Up, LabGesture.Right, LabGesture.Up, LabGesture.Right), segments.gestures)
        assertEquals(20f, segments.consumedX)
        assertEquals(-20f, segments.consumedY)
    }

    @Test
    fun `drag segmentation uses dominant axis first for mixed axis drags`() {
        val segments =
            segmentLabDrag(
                deltaX = 32f,
                deltaY = -11f,
                segmentPx = 10f,
            )

        assertEquals(listOf(LabGesture.Right, LabGesture.Right, LabGesture.Right, LabGesture.Up), segments.gestures)
        assertEquals(30f, segments.consumedX)
        assertEquals(-10f, segments.consumedY)
    }
}
