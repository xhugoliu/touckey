package io.github.xhugoliu.touckey.feature.control

data class ControlUiState(
    val connection: ControlConnectionUiState,
    val setupPrompt: ControlSetupPrompt?,
    val isInputEnabled: Boolean,
)

data class ControlConnectionUiState(
    val label: String,
    val detail: String,
    val accent: ControlStatusAccent,
    val isActionable: Boolean,
    val panelTitle: String,
    val panelDetail: String,
    val currentHost: ControlHostUiState?,
    val recentHosts: List<ControlHostUiState>,
    val actions: List<ControlConnectionAction>,
    val pendingLabel: String?,
)

data class ControlSetupPrompt(
    val title: String,
    val detail: String,
    val actions: List<ControlEnvironmentAction>,
)

data class ControlEnvironmentAction(
    val id: ControlEnvironmentActionId,
    val label: String,
)

data class ControlHostUiState(
    val name: String,
    val address: String,
    val platformLabel: String,
    val isCurrent: Boolean,
)

data class ControlConnectionAction(
    val id: ControlConnectionActionId,
    val label: String,
    val targetAddress: String? = null,
    val emphasized: Boolean = false,
    val enabled: Boolean = true,
)

enum class ControlStatusAccent {
    Positive,
    Warning,
    Neutral,
    Critical,
}

enum class ControlEnvironmentActionId {
    GrantPermissions,
    EnableBluetooth,
    RegisterHid,
    MakeDiscoverable,
    StopKeepAlive,
    RefreshStatus,
}

enum class ControlConnectionActionId {
    Disconnect,
    ReconnectLast,
    ConnectHost,
}
