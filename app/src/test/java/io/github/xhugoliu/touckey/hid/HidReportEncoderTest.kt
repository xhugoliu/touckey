package io.github.xhugoliu.touckey.hid

import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidReportEncoderTest {
    @Test
    fun `pointer move uses current mouse button mask`() {
        val result =
            HidReportEncoder.encode(
                InputAction.PointerMoveAction(deltaX = 12f, deltaY = -7f),
                currentMouseButtons = 1,
            ) as HidEncodingResult.Supported

        assertEquals(1, result.nextMouseButtons)
        assertEquals(1, result.packets.single().payload[0].toInt())
        assertEquals(12, result.packets.single().payload[1].toInt())
        assertEquals(-7, result.packets.single().payload[2].toInt())
    }

    @Test
    fun `mouse press and release update button state`() {
        val press =
            HidReportEncoder.encode(
                InputAction.MouseButtonPressAction(MouseButton.Left),
                currentMouseButtons = 0,
            ) as HidEncodingResult.Supported

        assertEquals(1, press.nextMouseButtons)
        assertEquals(1, press.packets.single().payload[0].toInt())

        val release =
            HidReportEncoder.encode(
                InputAction.MouseButtonReleaseAction(MouseButton.Left),
                currentMouseButtons = press.nextMouseButtons,
            ) as HidEncodingResult.Supported

        assertEquals(0, release.nextMouseButtons)
        assertEquals(0, release.packets.single().payload[0].toInt())
    }

    @Test
    fun `horizontal scroll is encoded in the mouse report tail byte`() {
        val result =
            HidReportEncoder.encode(
                InputAction.ScrollAction(vertical = -12, horizontal = 10),
                currentMouseButtons = 0,
            )

        assertTrue(result is HidEncodingResult.Supported)
        result as HidEncodingResult.Supported
        assertEquals(-12, result.packets.single().payload[3].toInt())
        assertEquals(10, result.packets.single().payload[4].toInt())
    }

    @Test
    fun `keyboard encoding supports full keyboard aliases and modifiers`() {
        val result =
            HidReportEncoder.encode(
                InputAction.KeyComboAction(
                    keys = listOf("A", "Delete"),
                    modifiers = listOf("Shift", "Cmd"),
                ),
                currentMouseButtons = 0,
            ) as HidEncodingResult.Supported

        assertEquals(0x0A, result.packets.first().payload[0].toInt())
        assertEquals(0x04, result.packets.first().payload[2].toInt())
        assertEquals(0x4C, result.packets.first().payload[3].toInt())
        assertEquals(0, result.packets.last().payload[0].toInt())
    }

    @Test
    fun `function and navigation keys are mapped for keyboard page`() {
        val result =
            HidReportEncoder.encode(
                InputAction.KeyComboAction(
                    keys = listOf("F12", "PageDown", "Home"),
                    modifiers = listOf("Option"),
                ),
                currentMouseButtons = 0,
            ) as HidEncodingResult.Supported

        assertEquals(0x04, result.packets.first().payload[0].toInt())
        assertEquals(0x45, result.packets.first().payload[2].toInt())
        assertEquals(0x4E, result.packets.first().payload[3].toInt())
        assertEquals(0x4A, result.packets.first().payload[4].toInt())
    }
}
