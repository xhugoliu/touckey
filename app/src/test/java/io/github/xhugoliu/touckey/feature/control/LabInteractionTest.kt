package io.github.xhugoliu.touckey.feature.control

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
            labKeyBinding(LabSide.Left, LabCell(row = 2, column = 2)),
        )
        assertEquals(
            LabKeyBinding(key = "Cmd", label = "GUI"),
            labKeyBinding(LabSide.Left, LabCell(row = 4, column = 0)),
        )
        assertEquals(
            LabKeyBinding(key = "Enter", label = "Enter"),
            labKeyBinding(LabSide.Right, LabCell(row = 0, column = 2)),
        )
        assertEquals(
            LabKeyBinding(key = "\\", label = "\\"),
            labKeyBinding(LabSide.Right, LabCell(row = 4, column = 3)),
        )
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
