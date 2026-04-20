package io.github.xhugoliu.touckey.hid

import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.MouseButton
import kotlin.math.roundToInt

data class HidPacket(
    val reportId: Int,
    val payload: ByteArray,
)

sealed interface HidEncodingResult {
    data class Supported(
        val summary: String,
        val packets: List<HidPacket>,
        val nextMouseButtons: Int,
    ) : HidEncodingResult

    data class Unsupported(
        val message: String,
    ) : HidEncodingResult
}

object HidReportEncoder {
    private val modifierBits =
        buildMap {
            put("Ctrl", 0x01)
            put("Control", 0x01)
            put("Shift", 0x02)
            put("Alt", 0x04)
            put("Option", 0x04)
            put("Win", 0x08)
            put("Cmd", 0x08)
            put("Command", 0x08)
            put("Meta", 0x08)
            put("Super", 0x08)
        }

    private val keyUsages =
        buildMap {
            ('A'..'Z').forEachIndexed { index, char ->
                put(char.toString(), 0x04 + index)
                put(char.lowercase(), 0x04 + index)
            }

            listOf(
                "1" to 0x1E,
                "2" to 0x1F,
                "3" to 0x20,
                "4" to 0x21,
                "5" to 0x22,
                "6" to 0x23,
                "7" to 0x24,
                "8" to 0x25,
                "9" to 0x26,
                "0" to 0x27,
            ).forEach { (label, usage) ->
                put(label, usage)
            }

            put("Enter", 0x28)
            put("Escape", 0x29)
            put("Esc", 0x29)
            put("Backspace", 0x2A)
            put("Tab", 0x2B)
            put("Space", 0x2C)
            put("Minus", 0x2D)
            put("-", 0x2D)
            put("Equal", 0x2E)
            put("=", 0x2E)
            put("LeftBracket", 0x2F)
            put("[", 0x2F)
            put("RightBracket", 0x30)
            put("]", 0x30)
            put("Backslash", 0x31)
            put("\\", 0x31)
            put("Semicolon", 0x33)
            put(";", 0x33)
            put("Quote", 0x34)
            put("'", 0x34)
            put("Grave", 0x35)
            put("`", 0x35)
            put("Comma", 0x36)
            put(",", 0x36)
            put("Period", 0x37)
            put(".", 0x37)
            put("Slash", 0x38)
            put("/", 0x38)
            put("CapsLock", 0x39)

            (1..12).forEach { functionNumber ->
                put("F$functionNumber", 0x39 + functionNumber)
            }

            put("PrintScreen", 0x46)
            put("ScrollLock", 0x47)
            put("Pause", 0x48)
            put("Insert", 0x49)
            put("Home", 0x4A)
            put("PageUp", 0x4B)
            put("Delete", 0x4C)
            put("End", 0x4D)
            put("PageDown", 0x4E)
            put("Right", 0x4F)
            put("Left", 0x50)
            put("Down", 0x51)
            put("Up", 0x52)
            put("Menu", 0x65)
        }

    private val consumerUsages =
        mapOf(
            "PlayPause" to 0x00CD,
            "VolumeUp" to 0x00E9,
            "VolumeDown" to 0x00EA,
            "Mute" to 0x00E2,
            "AcBack" to 0x0224,
        )

    private val mouseBits =
        mapOf(
            MouseButton.Left to 0x01,
            MouseButton.Right to 0x02,
            MouseButton.Middle to 0x04,
            MouseButton.Back to 0x08,
        )

    fun encode(
        action: InputAction,
        currentMouseButtons: Int,
    ): HidEncodingResult =
        when (action) {
            is InputAction.PointerMoveAction -> encodePointerMove(action, currentMouseButtons)
            is InputAction.KeyComboAction -> encodeKeyCombo(action, currentMouseButtons)
            is InputAction.MouseButtonPressAction -> encodeMouseButtonPress(action, currentMouseButtons)
            is InputAction.MouseButtonReleaseAction -> encodeMouseButtonRelease(action, currentMouseButtons)
            is InputAction.MouseButtonClickAction -> encodeMouseClick(action, currentMouseButtons)
            is InputAction.ScrollAction -> encodeScroll(action, currentMouseButtons)
            is InputAction.ConsumerControlAction -> encodeConsumer(action, currentMouseButtons)
        }

    private fun encodePointerMove(
        action: InputAction.PointerMoveAction,
        currentMouseButtons: Int,
    ): HidEncodingResult {
        val deltaX = pointerDelta(action.deltaX)
        val deltaY = pointerDelta(action.deltaY)

        if (deltaX == 0.toByte() && deltaY == 0.toByte()) {
            return HidEncodingResult.Unsupported("本次位移太小，已忽略。")
        }

        return HidEncodingResult.Supported(
            summary = "指针移动",
            packets =
                listOf(
                    HidPacket(
                        BluetoothHidDescriptor.MOUSE_REPORT_ID,
                        byteArrayOf(currentMouseButtons.toByte(), deltaX, deltaY, 0),
                    ),
                ),
            nextMouseButtons = currentMouseButtons,
        )
    }

    private fun encodeKeyCombo(
        action: InputAction.KeyComboAction,
        currentMouseButtons: Int,
    ): HidEncodingResult {
        val unknownKey = action.keys.firstOrNull { it !in keyUsages }
        if (unknownKey != null) {
            return HidEncodingResult.Unsupported("暂不支持键位 $unknownKey 的 HID 编码。")
        }

        val unknownModifier = action.modifiers.firstOrNull { it !in modifierBits }
        if (unknownModifier != null) {
            return HidEncodingResult.Unsupported("暂不支持修饰键 $unknownModifier。")
        }

        val report = ByteArray(8)
        report[0] = action.modifiers.fold(0) { acc, modifier -> acc or modifierBits.getValue(modifier) }.toByte()
        action.keys.take(6).forEachIndexed { index, key ->
            report[index + 2] = keyUsages.getValue(key).toByte()
        }

        return HidEncodingResult.Supported(
            summary = buildString {
                if (action.modifiers.isNotEmpty()) {
                    append(action.modifiers.joinToString("+"))
                    append("+")
                }
                append(action.keys.joinToString("+"))
            },
            packets =
                listOf(
                    HidPacket(BluetoothHidDescriptor.KEYBOARD_REPORT_ID, report),
                    HidPacket(BluetoothHidDescriptor.KEYBOARD_REPORT_ID, ByteArray(8)),
                ),
            nextMouseButtons = currentMouseButtons,
        )
    }

    private fun encodeMouseButtonPress(
        action: InputAction.MouseButtonPressAction,
        currentMouseButtons: Int,
    ): HidEncodingResult {
        val buttonMask = mouseBits[action.button]
            ?: return HidEncodingResult.Unsupported("暂不支持 ${action.button} 的鼠标按键映射。")

        val nextButtons = currentMouseButtons or buttonMask

        return HidEncodingResult.Supported(
            summary = "${action.button.name} 按下",
            packets =
                listOf(
                    HidPacket(BluetoothHidDescriptor.MOUSE_REPORT_ID, byteArrayOf(nextButtons.toByte(), 0, 0, 0)),
                ),
            nextMouseButtons = nextButtons,
        )
    }

    private fun encodeMouseButtonRelease(
        action: InputAction.MouseButtonReleaseAction,
        currentMouseButtons: Int,
    ): HidEncodingResult {
        val buttonMask = mouseBits[action.button]
            ?: return HidEncodingResult.Unsupported("暂不支持 ${action.button} 的鼠标按键映射。")

        val nextButtons = currentMouseButtons and buttonMask.inv()

        return HidEncodingResult.Supported(
            summary = "${action.button.name} 释放",
            packets =
                listOf(
                    HidPacket(BluetoothHidDescriptor.MOUSE_REPORT_ID, byteArrayOf(nextButtons.toByte(), 0, 0, 0)),
                ),
            nextMouseButtons = nextButtons,
        )
    }

    private fun encodeMouseClick(
        action: InputAction.MouseButtonClickAction,
        currentMouseButtons: Int,
    ): HidEncodingResult {
        val buttonMask = mouseBits[action.button]
            ?: return HidEncodingResult.Unsupported("暂不支持 ${action.button} 的鼠标按键映射。")

        val pressedButtons = currentMouseButtons or buttonMask

        return HidEncodingResult.Supported(
            summary = "${action.button.name} 鼠标按键",
            packets =
                listOf(
                    HidPacket(BluetoothHidDescriptor.MOUSE_REPORT_ID, byteArrayOf(pressedButtons.toByte(), 0, 0, 0)),
                    HidPacket(BluetoothHidDescriptor.MOUSE_REPORT_ID, byteArrayOf(currentMouseButtons.toByte(), 0, 0, 0)),
                ),
            nextMouseButtons = currentMouseButtons,
        )
    }

    private fun encodeScroll(
        action: InputAction.ScrollAction,
        currentMouseButtons: Int,
    ): HidEncodingResult {
        val wheel = action.vertical.coerceIn(-127, 127).toByte()
        val pan = action.horizontal.coerceIn(-127, 127).toByte()
        val scrollReport = byteArrayOf(0, 0, 0, wheel)

        return HidEncodingResult.Supported(
            summary =
                if (action.horizontal != 0) {
                    "滚轮(v=${action.vertical}, h=${action.horizontal})"
                } else {
                    "滚轮 ${action.vertical}"
                },
            packets =
                listOf(
                    HidPacket(
                        BluetoothHidDescriptor.MOUSE_REPORT_ID,
                        byteArrayOf(currentMouseButtons.toByte(), scrollReport[1], scrollReport[2], scrollReport[3], pan),
                    ),
                ),
            nextMouseButtons = currentMouseButtons,
        )
    }

    private fun encodeConsumer(
        action: InputAction.ConsumerControlAction,
        currentMouseButtons: Int,
    ): HidEncodingResult {
        val usage = consumerUsages[action.usage]
            ?: return HidEncodingResult.Unsupported("暂不支持 consumer control 用途 ${action.usage}。")

        val press = byteArrayOf((usage and 0xFF).toByte(), ((usage shr 8) and 0xFF).toByte())
        val release = byteArrayOf(0, 0)

        return HidEncodingResult.Supported(
            summary = action.usage,
            packets =
                listOf(
                    HidPacket(BluetoothHidDescriptor.CONSUMER_REPORT_ID, press),
                    HidPacket(BluetoothHidDescriptor.CONSUMER_REPORT_ID, release),
                ),
            nextMouseButtons = currentMouseButtons,
        )
    }

    fun pointerDelta(value: Float): Byte = value.roundToInt().coerceIn(-127, 127).toByte()
}
