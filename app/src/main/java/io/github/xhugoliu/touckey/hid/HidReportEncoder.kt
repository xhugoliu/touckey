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

    fun encode(action: InputAction): HidEncodingResult =
        when (action) {
            is InputAction.KeyComboAction -> encodeKeyCombo(action)
            is InputAction.MouseButtonClickAction -> encodeMouseClick(action)
            is InputAction.ScrollAction -> encodeScroll(action)
            is InputAction.ConsumerControlAction -> encodeConsumer(action)
            InputAction.PointerMove ->
                HidEncodingResult.Unsupported(
                    "当前骨架还没有把手势位移转换成真实鼠标位移数据。",
                )
        }

    private fun encodeKeyCombo(action: InputAction.KeyComboAction): HidEncodingResult {
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
        )
    }

    private fun encodeMouseClick(action: InputAction.MouseButtonClickAction): HidEncodingResult {
        val buttonMask = mouseBits[action.button]
            ?: return HidEncodingResult.Unsupported("暂不支持 ${action.button} 的鼠标按键映射。")

        val press = byteArrayOf(buttonMask.toByte(), 0, 0, 0)
        val release = byteArrayOf(0, 0, 0, 0)

        return HidEncodingResult.Supported(
            summary = "${action.button.name} 鼠标按键",
            packets =
                listOf(
                    HidPacket(BluetoothHidDescriptor.MOUSE_REPORT_ID, press),
                    HidPacket(BluetoothHidDescriptor.MOUSE_REPORT_ID, release),
                ),
        )
    }

    private fun encodeScroll(action: InputAction.ScrollAction): HidEncodingResult {
        if (action.horizontal != 0) {
            return HidEncodingResult.Unsupported("当前最小实现只接入了纵向滚轮，横向滚动还没打通。")
        }

        val wheel = action.vertical.coerceIn(-127, 127).toByte()
        val scrollReport = byteArrayOf(0, 0, 0, wheel)

        return HidEncodingResult.Supported(
            summary = "滚轮 ${action.vertical}",
            packets = listOf(HidPacket(BluetoothHidDescriptor.MOUSE_REPORT_ID, scrollReport)),
        )
    }

    private fun encodeConsumer(action: InputAction.ConsumerControlAction): HidEncodingResult {
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
        )
    }

    fun pointerDelta(value: Float): Byte = value.roundToInt().coerceIn(-127, 127).toByte()
}
