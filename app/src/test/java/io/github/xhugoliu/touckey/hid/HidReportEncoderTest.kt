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
                currentKeyboardModifiers = 0,
                currentKeyboardKeys = emptyList(),
            ) as HidEncodingResult.Supported

        assertEquals(1, result.nextMouseButtons)
        assertEquals(0, result.nextKeyboardModifiers)
        assertEquals(emptyList<Int>(), result.nextKeyboardKeys)
        assertEquals(HidReportEncoder.MOUSE_REPORT_PAYLOAD_SIZE, result.packets.single().payload.size)
        assertEquals(1, result.packets.single().payload[0].toInt())
        assertEquals(12, result.packets.single().payload[1].toInt())
        assertEquals(-7, result.packets.single().payload[2].toInt())
        assertEquals(0, result.packets.single().payload[3].toInt())
        assertEquals(0, result.packets.single().payload[4].toInt())
    }

    @Test
    fun `mouse press and release update button state`() {
        val press =
            HidReportEncoder.encode(
                InputAction.MouseButtonPressAction(MouseButton.Left),
                currentMouseButtons = 0,
                currentKeyboardModifiers = 0,
                currentKeyboardKeys = emptyList(),
            ) as HidEncodingResult.Supported

        assertEquals(1, press.nextMouseButtons)
        assertEquals(HidReportEncoder.MOUSE_REPORT_PAYLOAD_SIZE, press.packets.single().payload.size)
        assertEquals(1, press.packets.single().payload[0].toInt())

        val release =
            HidReportEncoder.encode(
                InputAction.MouseButtonReleaseAction(MouseButton.Left),
                currentMouseButtons = press.nextMouseButtons,
                currentKeyboardModifiers = 0,
                currentKeyboardKeys = emptyList(),
            ) as HidEncodingResult.Supported

        assertEquals(0, release.nextMouseButtons)
        assertEquals(HidReportEncoder.MOUSE_REPORT_PAYLOAD_SIZE, release.packets.single().payload.size)
        assertEquals(0, release.packets.single().payload[0].toInt())
    }

    @Test
    fun `horizontal scroll is encoded in the mouse report tail byte`() {
        val result =
            HidReportEncoder.encode(
                InputAction.ScrollAction(vertical = -12, horizontal = 10),
                currentMouseButtons = 0,
                currentKeyboardModifiers = 0,
                currentKeyboardKeys = emptyList(),
            )

        assertTrue(result is HidEncodingResult.Supported)
        result as HidEncodingResult.Supported
        assertEquals(HidReportEncoder.MOUSE_REPORT_PAYLOAD_SIZE, result.packets.single().payload.size)
        assertEquals(-12, result.packets.single().payload[3].toInt())
        assertEquals(10, result.packets.single().payload[4].toInt())
    }

    @Test
    fun `mouse descriptor declares x y wheel and ac pan usages`() {
        val reportMap = BluetoothHidDescriptor.reportMap.map { it.toInt() and 0xFF }

        assertTrue(
            reportMap.containsSequence(
                listOf(
                    0x09,
                    0x30,
                    0x09,
                    0x31,
                    0x09,
                    0x38,
                    0x15,
                    0x81,
                    0x25,
                    0x7F,
                    0x75,
                    0x08,
                    0x95,
                    0x03,
                    0x81,
                    0x06,
                    0x05,
                    0x0C,
                    0x0A,
                    0x38,
                    0x02,
                    0x95,
                    0x01,
                    0x81,
                    0x06,
                ),
            ),
        )
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
                currentKeyboardModifiers = 0,
                currentKeyboardKeys = emptyList(),
            ) as HidEncodingResult.Supported

        assertEquals(0x0A, result.packets.first().payload[0].toInt())
        assertEquals(0x04, result.packets.first().payload[2].toInt())
        assertEquals(0x4C, result.packets.first().payload[3].toInt())
        assertEquals(0, result.packets.last().payload[0].toInt())
        assertEquals(emptyList<Int>(), result.nextKeyboardKeys)
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
                currentKeyboardModifiers = 0,
                currentKeyboardKeys = emptyList(),
            ) as HidEncodingResult.Supported

        assertEquals(0x04, result.packets.first().payload[0].toInt())
        assertEquals(0x45, result.packets.first().payload[2].toInt())
        assertEquals(0x4E, result.packets.first().payload[3].toInt())
        assertEquals(0x4A, result.packets.first().payload[4].toInt())
    }

    @Test
    fun `key press and release keep keyboard state like a real keyboard`() {
        val pressModifier =
            HidReportEncoder.encode(
                InputAction.KeyPressAction("Shift"),
                currentMouseButtons = 0,
                currentKeyboardModifiers = 0,
                currentKeyboardKeys = emptyList(),
            ) as HidEncodingResult.Supported

        assertEquals(0x02, pressModifier.nextKeyboardModifiers)
        assertEquals(0x02, pressModifier.packets.single().payload[0].toInt())

        val pressKey =
            HidReportEncoder.encode(
                InputAction.KeyPressAction("A"),
                currentMouseButtons = 0,
                currentKeyboardModifiers = pressModifier.nextKeyboardModifiers,
                currentKeyboardKeys = pressModifier.nextKeyboardKeys,
            ) as HidEncodingResult.Supported

        assertEquals(0x02, pressKey.packets.single().payload[0].toInt())
        assertEquals(0x04, pressKey.packets.single().payload[2].toInt())
        assertEquals(listOf(0x04), pressKey.nextKeyboardKeys)

        val releaseKey =
            HidReportEncoder.encode(
                InputAction.KeyReleaseAction("A"),
                currentMouseButtons = 0,
                currentKeyboardModifiers = pressKey.nextKeyboardModifiers,
                currentKeyboardKeys = pressKey.nextKeyboardKeys,
            ) as HidEncodingResult.Supported

        assertEquals(0x02, releaseKey.packets.single().payload[0].toInt())
        assertEquals(0, releaseKey.packets.single().payload[2].toInt())
        assertEquals(emptyList<Int>(), releaseKey.nextKeyboardKeys)

        val releaseModifier =
            HidReportEncoder.encode(
                InputAction.KeyReleaseAction("Shift"),
                currentMouseButtons = 0,
                currentKeyboardModifiers = releaseKey.nextKeyboardModifiers,
                currentKeyboardKeys = releaseKey.nextKeyboardKeys,
            ) as HidEncodingResult.Supported

        assertEquals(0, releaseModifier.packets.single().payload[0].toInt())
        assertEquals(0, releaseModifier.nextKeyboardModifiers)
    }

    private fun List<Int>.containsSequence(sequence: List<Int>): Boolean =
        windowed(sequence.size).any { window ->
            window == sequence
        }
}
