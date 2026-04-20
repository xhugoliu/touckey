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
        mapOf(
            "Ctrl" to 0x01,
            "Shift" to 0x02,
            "Alt" to 0x04,
            "Win" to 0x08,
        )

    private val keyUsages =
        mapOf(
            "Tab" to 0x2B,
            "Up" to 0x52,
            "Left" to 0x50,
            "Right" to 0x4F,
            "Down" to 0x51,
            "Space" to 0x2C,
            "Enter" to 0x28,
            "Escape" to 0x29,
        )

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
