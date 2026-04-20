package io.github.xhugoliu.touckey.input

enum class MouseButton {
    Left,
    Right,
    Middle,
    Back,
}

sealed interface InputAction {
    data object PointerMove : InputAction

    data class KeyComboAction(
        val keys: List<String>,
        val modifiers: List<String> = emptyList(),
    ) : InputAction

    data class MouseButtonClickAction(
        val button: MouseButton,
    ) : InputAction

    data class ScrollAction(
        val vertical: Int = 0,
        val horizontal: Int = 0,
    ) : InputAction

    data class ConsumerControlAction(
        val usage: String,
    ) : InputAction
}

fun InputAction.summary(): String =
    when (this) {
        InputAction.PointerMove -> "指针移动"
        is InputAction.KeyComboAction -> buildString {
            if (modifiers.isNotEmpty()) {
                append(modifiers.joinToString("+"))
                append("+")
            }
            append(keys.joinToString("+"))
        }
        is InputAction.MouseButtonClickAction -> "${button.name} 点击"
        is InputAction.ScrollAction -> "滚动(v=$vertical, h=$horizontal)"
        is InputAction.ConsumerControlAction -> usage
    }
