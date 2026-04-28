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
        val nextKeyboardModifiers: Int,
        val nextKeyboardKeys: List<Int>,
    ) : HidEncodingResult

    data class Unsupported(
        val message: String,
    ) : HidEncodingResult
}

object HidReportEncoder {
    const val MOUSE_REPORT_PAYLOAD_SIZE = 5

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
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): HidEncodingResult =
        when (action) {
            is InputAction.PointerMoveAction -> encodePointerMove(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
            is InputAction.KeyComboAction -> encodeKeyCombo(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
            is InputAction.KeyPressAction -> encodeKeyPress(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
            is InputAction.KeyReleaseAction -> encodeKeyRelease(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
            is InputAction.MouseButtonPressAction -> encodeMouseButtonPress(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
            is InputAction.MouseButtonReleaseAction -> encodeMouseButtonRelease(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
            is InputAction.MouseButtonClickAction -> encodeMouseClick(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
            is InputAction.ScrollAction -> encodeScroll(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
            is InputAction.ConsumerControlAction -> encodeConsumer(action, currentMouseButtons, currentKeyboardModifiers, currentKeyboardKeys)
        }

    private fun encodePointerMove(
        action: InputAction.PointerMoveAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
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
                        mouseReport(
                            buttons = currentMouseButtons,
                            deltaX = deltaX,
                            deltaY = deltaY,
                        ),
                    ),
                ),
            nextMouseButtons = currentMouseButtons,
            nextKeyboardModifiers = currentKeyboardModifiers,
            nextKeyboardKeys = currentKeyboardKeys,
        )
    }

    private fun encodeKeyCombo(
        action: InputAction.KeyComboAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): HidEncodingResult {
        val keyUsagesForAction =
            action.keys.map { key ->
                resolveKeyUsage(key)
                    ?: return HidEncodingResult.Unsupported("暂不支持键位 $key 的 HID 编码。")
            }
        val temporaryModifierMask =
            action.modifiers.fold(0) { acc, modifier ->
                val bit = resolveModifierBit(modifier)
                    ?: return HidEncodingResult.Unsupported("暂不支持修饰键 $modifier。")
                acc or bit
            }
        val effectiveModifiers = currentKeyboardModifiers or temporaryModifierMask
        val effectiveKeys = (currentKeyboardKeys + keyUsagesForAction).distinct()
        if (effectiveKeys.size > 6) {
            return HidEncodingResult.Unsupported("同时按下的键位超过 6 个，当前 HID 键盘 report 无法编码。")
        }
        val report = keyboardReport(effectiveModifiers, effectiveKeys.take(6))
        val releaseReport = keyboardReport(currentKeyboardModifiers, currentKeyboardKeys)

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
                    HidPacket(BluetoothHidDescriptor.KEYBOARD_REPORT_ID, releaseReport),
                ),
            nextMouseButtons = currentMouseButtons,
            nextKeyboardModifiers = currentKeyboardModifiers,
            nextKeyboardKeys = currentKeyboardKeys,
        )
    }

    private fun encodeKeyPress(
        action: InputAction.KeyPressAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): HidEncodingResult {
        val modifierBit = resolveModifierBit(action.key)
        val keyUsage = resolveKeyUsage(action.key)
        if (modifierBit == null && keyUsage == null) {
            return HidEncodingResult.Unsupported("暂不支持键位 ${action.key} 的按下事件。")
        }
        val nextModifiers = if (modifierBit != null) currentKeyboardModifiers or modifierBit else currentKeyboardModifiers
        val nextKeys =
            if (keyUsage != null && keyUsage !in currentKeyboardKeys) {
                if (currentKeyboardKeys.size >= 6) {
                    return HidEncodingResult.Unsupported("同时按下的键位超过 6 个，当前 HID 键盘 report 无法编码。")
                }
                currentKeyboardKeys + keyUsage
            } else {
                currentKeyboardKeys
            }

        return HidEncodingResult.Supported(
            summary = "${action.key} 按下",
            packets =
                listOf(
                    HidPacket(
                        BluetoothHidDescriptor.KEYBOARD_REPORT_ID,
                        keyboardReport(nextModifiers, nextKeys),
                    ),
                ),
            nextMouseButtons = currentMouseButtons,
            nextKeyboardModifiers = nextModifiers,
            nextKeyboardKeys = nextKeys,
        )
    }

    private fun encodeKeyRelease(
        action: InputAction.KeyReleaseAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): HidEncodingResult {
        val modifierBit = resolveModifierBit(action.key)
        val keyUsage = resolveKeyUsage(action.key)
        if (modifierBit == null && keyUsage == null) {
            return HidEncodingResult.Unsupported("暂不支持键位 ${action.key} 的释放事件。")
        }
        val nextModifiers = if (modifierBit != null) currentKeyboardModifiers and modifierBit.inv() else currentKeyboardModifiers
        val nextKeys = if (keyUsage != null) currentKeyboardKeys - keyUsage else currentKeyboardKeys

        return HidEncodingResult.Supported(
            summary = "${action.key} 释放",
            packets =
                listOf(
                    HidPacket(
                        BluetoothHidDescriptor.KEYBOARD_REPORT_ID,
                        keyboardReport(nextModifiers, nextKeys),
                    ),
                ),
            nextMouseButtons = currentMouseButtons,
            nextKeyboardModifiers = nextModifiers,
            nextKeyboardKeys = nextKeys,
        )
    }

    private fun encodeMouseButtonPress(
        action: InputAction.MouseButtonPressAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): HidEncodingResult {
        val buttonMask = mouseBits[action.button]
            ?: return HidEncodingResult.Unsupported("暂不支持 ${action.button} 的鼠标按键映射。")

        val nextButtons = currentMouseButtons or buttonMask

        return HidEncodingResult.Supported(
            summary = "${action.button.name} 按下",
            packets =
                listOf(
                    HidPacket(
                        BluetoothHidDescriptor.MOUSE_REPORT_ID,
                        mouseReport(buttons = nextButtons),
                    ),
                ),
            nextMouseButtons = nextButtons,
            nextKeyboardModifiers = currentKeyboardModifiers,
            nextKeyboardKeys = currentKeyboardKeys,
        )
    }

    private fun encodeMouseButtonRelease(
        action: InputAction.MouseButtonReleaseAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): HidEncodingResult {
        val buttonMask = mouseBits[action.button]
            ?: return HidEncodingResult.Unsupported("暂不支持 ${action.button} 的鼠标按键映射。")

        val nextButtons = currentMouseButtons and buttonMask.inv()

        return HidEncodingResult.Supported(
            summary = "${action.button.name} 释放",
            packets =
                listOf(
                    HidPacket(
                        BluetoothHidDescriptor.MOUSE_REPORT_ID,
                        mouseReport(buttons = nextButtons),
                    ),
                ),
            nextMouseButtons = nextButtons,
            nextKeyboardModifiers = currentKeyboardModifiers,
            nextKeyboardKeys = currentKeyboardKeys,
        )
    }

    private fun encodeMouseClick(
        action: InputAction.MouseButtonClickAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): HidEncodingResult {
        val buttonMask = mouseBits[action.button]
            ?: return HidEncodingResult.Unsupported("暂不支持 ${action.button} 的鼠标按键映射。")

        val pressedButtons = currentMouseButtons or buttonMask

        return HidEncodingResult.Supported(
            summary = "${action.button.name} 鼠标按键",
            packets =
                listOf(
                    HidPacket(
                        BluetoothHidDescriptor.MOUSE_REPORT_ID,
                        mouseReport(buttons = pressedButtons),
                    ),
                    HidPacket(
                        BluetoothHidDescriptor.MOUSE_REPORT_ID,
                        mouseReport(buttons = currentMouseButtons),
                    ),
                ),
            nextMouseButtons = currentMouseButtons,
            nextKeyboardModifiers = currentKeyboardModifiers,
            nextKeyboardKeys = currentKeyboardKeys,
        )
    }

    private fun encodeScroll(
        action: InputAction.ScrollAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): HidEncodingResult {
        val wheel = action.vertical.coerceIn(-127, 127).toByte()
        val pan = action.horizontal.coerceIn(-127, 127).toByte()

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
                        mouseReport(
                            buttons = currentMouseButtons,
                            wheel = wheel,
                            pan = pan,
                        ),
                    ),
                ),
            nextMouseButtons = currentMouseButtons,
            nextKeyboardModifiers = currentKeyboardModifiers,
            nextKeyboardKeys = currentKeyboardKeys,
        )
    }

    private fun encodeConsumer(
        action: InputAction.ConsumerControlAction,
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
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
            nextKeyboardModifiers = currentKeyboardModifiers,
            nextKeyboardKeys = currentKeyboardKeys,
        )
    }

    private fun resolveModifierBit(name: String): Int? = modifierBits[name]

    private fun resolveKeyUsage(name: String): Int? = keyUsages[name]

    private fun keyboardReport(
        modifiers: Int,
        keys: List<Int>,
    ): ByteArray =
        ByteArray(8).apply {
            this[0] = modifiers.toByte()
            keys.take(6).forEachIndexed { index, usage ->
                this[index + 2] = usage.toByte()
            }
        }

    fun pointerDelta(value: Float): Byte = value.roundToInt().coerceIn(-127, 127).toByte()

    private fun mouseReport(
        buttons: Int,
        deltaX: Byte = 0,
        deltaY: Byte = 0,
        wheel: Byte = 0,
        pan: Byte = 0,
    ): ByteArray =
        byteArrayOf(
            buttons.toByte(),
            deltaX,
            deltaY,
            wheel,
            pan,
        )
}
