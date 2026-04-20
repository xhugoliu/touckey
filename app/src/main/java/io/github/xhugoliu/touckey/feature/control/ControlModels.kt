package io.github.xhugoliu.touckey.feature.control

data class ControlUiState(
    val connectionLabel: String,
    val connectionDetail: String,
    val hostLabel: String,
    val quickActions: List<ControlQuickAction>,
    val gestureHints: List<ControlGestureHint>,
    val lastDispatchMessage: String?,
)

data class ControlQuickAction(
    val id: String,
    val label: String,
    val detail: String,
)

data class ControlGestureHint(
    val title: String,
    val detail: String,
)
