package io.github.xhugoliu.touckey.feature.control

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierModeTest {
    @Test
    fun `preset mode clears armed modifiers after key tap`() {
        val next =
            modifiersAfterKeyTap(
                mode = ModifierMode.Preset,
                armedModifiers = listOf("Shift", "Cmd"),
            )

        assertEquals(emptyList<String>(), next)
    }

    @Test
    fun `hold mode keeps armed modifiers after key tap`() {
        val next =
            modifiersAfterKeyTap(
                mode = ModifierMode.Hold,
                armedModifiers = listOf("Ctrl"),
            )

        assertEquals(listOf("Ctrl"), next)
    }

    @Test
    fun `tapping modifier toggles armed state`() {
        val armed = toggleArmedModifier(emptyList(), "Alt")
        val cleared = toggleArmedModifier(armed, "Alt")

        assertEquals(listOf("Alt"), armed)
        assertEquals(emptyList<String>(), cleared)
    }

    @Test
    fun `hold pointer releases only the lifted pointer in multi touch flow`() {
        val firstPress =
            reduceHoldPointerEvent(
                activePointerId = null,
                actionMasked = MotionEvent.ACTION_DOWN,
                actionPointerId = 3,
            )
        val secondPress =
            reduceHoldPointerEvent(
                activePointerId = null,
                actionMasked = MotionEvent.ACTION_POINTER_DOWN,
                actionPointerId = 7,
            )
        val keepFirstHeld =
            reduceHoldPointerEvent(
                activePointerId = firstPress.nextPointerId,
                actionMasked = MotionEvent.ACTION_POINTER_UP,
                actionPointerId = 7,
            )
        val releaseFirst =
            reduceHoldPointerEvent(
                activePointerId = firstPress.nextPointerId,
                actionMasked = MotionEvent.ACTION_POINTER_UP,
                actionPointerId = 3,
            )

        assertTrue(firstPress.shouldPress)
        assertEquals(3, firstPress.nextPointerId)

        assertTrue(secondPress.shouldPress)
        assertEquals(7, secondPress.nextPointerId)

        assertFalse(keepFirstHeld.shouldRelease)
        assertEquals(3, keepFirstHeld.nextPointerId)

        assertTrue(releaseFirst.shouldRelease)
        assertNull(releaseFirst.nextPointerId)
    }

    @Test
    fun `hold pointer cancel releases active key`() {
        val cancelled =
            reduceHoldPointerEvent(
                activePointerId = 5,
                actionMasked = MotionEvent.ACTION_CANCEL,
                actionPointerId = -1,
            )

        assertTrue(cancelled.shouldRelease)
        assertNull(cancelled.nextPointerId)
    }
}
