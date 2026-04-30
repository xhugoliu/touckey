package io.github.xhugoliu.touckey.hid

import io.github.xhugoliu.touckey.input.InputAction
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

    fun releaseAllPackets(
        currentMouseButtons: Int,
        currentKeyboardModifiers: Int,
        currentKeyboardKeys: List<Int>,
    ): List<HidPacket> =
        buildList {
            if (currentKeyboardModifiers != 0 || currentKeyboardKeys.isNotEmpty()) {
                add(
                    HidPacket(
                        BluetoothHidDescriptor.KEYBOARD_REPORT_ID,
                        keyboardReport(modifiers = 0, keys = emptyList()),
                    ),
                )
            }
            if (currentMouseButtons != 0) {
                add(
                    HidPacket(
                        BluetoothHidDescriptor.MOUSE_REPORT_ID,
                        mouseReport(buttons = 0),
                    ),
                )
            }
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
        val buttonMask = HidCapabilityCatalog.mouseButtonBit(action.button)
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
        val buttonMask = HidCapabilityCatalog.mouseButtonBit(action.button)
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
        val buttonMask = HidCapabilityCatalog.mouseButtonBit(action.button)
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
        val usage = HidCapabilityCatalog.consumerUsage(action.usage)
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

    private fun resolveModifierBit(name: String): Int? = HidCapabilityCatalog.modifierBit(name)

    private fun resolveKeyUsage(name: String): Int? = HidCapabilityCatalog.keyUsage(name)

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
