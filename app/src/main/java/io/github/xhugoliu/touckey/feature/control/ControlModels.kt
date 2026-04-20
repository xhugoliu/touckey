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
