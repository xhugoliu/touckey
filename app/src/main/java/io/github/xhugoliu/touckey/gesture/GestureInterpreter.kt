package io.github.xhugoliu.touckey.gesture

import io.github.xhugoliu.touckey.input.InputAction

enum class GestureKind(val label: String) {
    SingleFingerMove("单指移动"),
    TwoFingerScroll("双指滚动"),
    DoubleTapAndHold("双击并按住"),
}

data class GesturePreset(
    val kind: GestureKind,
    val summary: String,
    val action: InputAction,
)

data class GestureBinding(
    val title: String,
    val detail: String,
)

interface GestureInterpreter {
    fun supportedBindings(): List<GestureBinding>
}
