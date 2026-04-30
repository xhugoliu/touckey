package io.github.xhugoliu.touckey.hid

import io.github.xhugoliu.touckey.input.MouseButton

/**
 * Single source of truth for the HID usages Touckey can validate and encode.
 *
 * Layout/keymap validation and report encoding must both consume this catalog so
 * profile support cannot drift away from the Bluetooth HID report path.
 */
object HidCapabilityCatalog {
    val modifierBits: Map<String, Int> =
        mapOf(
            "Ctrl" to 0x01,
            "Control" to 0x01,
            "Shift" to 0x02,
            "Alt" to 0x04,
            "Option" to 0x04,
            "Win" to 0x08,
            "Cmd" to 0x08,
            "Command" to 0x08,
            "Meta" to 0x08,
            "Super" to 0x08,
        )

    val keyUsages: Map<String, Int> =
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

    val consumerUsages: Map<String, Int> =
        mapOf(
            "PlayPause" to 0x00CD,
            "VolumeUp" to 0x00E9,
            "VolumeDown" to 0x00EA,
            "Mute" to 0x00E2,
            "AcBack" to 0x0224,
        )

    val mouseButtonBits: Map<MouseButton, Int> =
        mapOf(
            MouseButton.Left to 0x01,
            MouseButton.Right to 0x02,
            MouseButton.Middle to 0x04,
            MouseButton.Back to 0x08,
        )

    fun modifierBit(name: String): Int? = modifierBits[name]

    fun keyUsage(name: String): Int? = keyUsages[name]

    fun consumerUsage(name: String): Int? = consumerUsages[name]

    fun mouseButtonBit(button: MouseButton): Int? = mouseButtonBits[button]

    fun supportsKeyboardInput(name: String): Boolean = keyUsage(name) != null || modifierBit(name) != null

    fun supportsConsumerUsage(name: String): Boolean = consumerUsage(name) != null

    fun isModifier(name: String): Boolean = modifierBit(name) != null
}
