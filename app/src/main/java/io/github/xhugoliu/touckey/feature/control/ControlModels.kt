package io.github.xhugoliu.touckey.feature.control

data class ControlUiState(
    val connectionLabel: String,
    val connectionDetail: String,
    val hostLabel: String,
    val adapterLabel: String,
    val quickActions: List<ControlQuickAction>,
    val gestureHints: List<ControlGestureHint>,
    val environmentActions: List<ControlEnvironmentAction>,
    val canSendQuickActions: Boolean,
    val foregroundHint: String?,
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

data class ControlEnvironmentAction(
    val id: ControlEnvironmentActionId,
    val label: String,
    val detail: String,
)

enum class ControlEnvironmentActionId {
    GrantPermissions,
    EnableBluetooth,
    RegisterHid,
    MakeDiscoverable,
    StopKeepAlive,
    RefreshStatus,
}
