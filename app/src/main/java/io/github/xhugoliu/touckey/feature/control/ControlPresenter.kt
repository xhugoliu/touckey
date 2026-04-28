package io.github.xhugoliu.touckey.feature.control

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.core.model.HostDevice
import io.github.xhugoliu.touckey.input.ActionDispatcher
import io.github.xhugoliu.touckey.input.DispatchResult
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.session.HostOperationKind
import io.github.xhugoliu.touckey.session.HostPendingOperation
import io.github.xhugoliu.touckey.session.SessionSnapshot

class ControlPresenter(
    private val actionDispatcher: ActionDispatcher,
) {
    fun buildUiState(sessionSnapshot: SessionSnapshot): ControlUiState {
        val hostName = sessionSnapshot.hostControl.currentHost?.name

        return ControlUiState(
            connection =
                ControlConnectionUiState(
                    label = buildConnectionLabel(sessionSnapshot, hostName),
                    detail = buildConnectionDetail(sessionSnapshot, hostName),
                    accent = buildAccent(sessionSnapshot),
                    isActionable = buildConnectionActionable(sessionSnapshot),
                    panelTitle = "Connection",
                    panelDetail = buildConnectionPanelDetail(sessionSnapshot),
                    currentHost = sessionSnapshot.hostControl.currentHost?.toUiState(isCurrent = true),
                    recentHosts = buildRecentHostUiStates(sessionSnapshot),
                    actions = buildConnectionActions(sessionSnapshot),
                    pendingLabel = sessionSnapshot.hostControl.pendingOperation?.toLabel(),
                ),
            setupPrompt = buildSetupPrompt(sessionSnapshot),
            isInputEnabled = sessionSnapshot.canSendInput,
        )
    }

    fun dispatch(action: InputAction): DispatchResult = actionDispatcher.dispatch(action)

    private fun buildConnectionLabel(
        sessionSnapshot: SessionSnapshot,
        hostName: String?,
    ): String =
        when {
            sessionSnapshot.hostControl.pendingOperation != null -> sessionSnapshot.hostControl.pendingOperation.toShortLabel()
            sessionSnapshot.hostControl.currentHost != null -> hostName ?: "Connected"
            else -> when (sessionSnapshot.status) {
                ConnectionStatus.Ready -> "Ready"
                ConnectionStatus.NeedsRegistration -> "Register HID"
                ConnectionStatus.MissingPermission -> "Permission"
                ConnectionStatus.BluetoothDisabled -> "Bluetooth Off"
                ConnectionStatus.Initializing -> "Starting"
                ConnectionStatus.Unsupported -> "Unsupported"
                ConnectionStatus.Error -> "Attention"
                ConnectionStatus.Connected -> hostName ?: "Connected"
            }
        }

    private fun buildConnectionDetail(
        sessionSnapshot: SessionSnapshot,
        hostName: String?,
    ): String =
        when {
            sessionSnapshot.hostControl.pendingOperation != null ->
                sessionSnapshot.hostControl.lastCommandMessage
                    ?: sessionSnapshot.hostControl.pendingOperation.toLabel()
            sessionSnapshot.hostControl.currentHost != null -> "Connected to ${hostName ?: "desktop"}"
            else -> when (sessionSnapshot.status) {
                ConnectionStatus.Ready -> "Registered and waiting for a desktop to connect."
                ConnectionStatus.NeedsRegistration -> "Bluetooth is available, but the HID profile is not registered yet."
                ConnectionStatus.MissingPermission -> "Nearby devices permission is required before Touckey can appear as a keyboard and touchpad."
                ConnectionStatus.BluetoothDisabled -> "Turn Bluetooth on to register and pair Touckey."
                ConnectionStatus.Initializing -> "Preparing the Bluetooth HID environment."
                ConnectionStatus.Unsupported -> sessionSnapshot.detail
                ConnectionStatus.Error -> sessionSnapshot.detail
                ConnectionStatus.Connected -> "Connected to ${hostName ?: "desktop"}"
            }
        }

    private fun buildAccent(sessionSnapshot: SessionSnapshot): ControlStatusAccent =
        when {
            sessionSnapshot.hostControl.currentHost != null && sessionSnapshot.hostControl.pendingOperation == null -> ControlStatusAccent.Positive
            sessionSnapshot.hostControl.pendingOperation != null -> ControlStatusAccent.Warning
            else ->
                when (sessionSnapshot.status) {
                    ConnectionStatus.MissingPermission,
                    ConnectionStatus.BluetoothDisabled,
                    ConnectionStatus.NeedsRegistration,
                    ConnectionStatus.Ready,
                    -> ControlStatusAccent.Warning
                    ConnectionStatus.Error,
                    ConnectionStatus.Unsupported,
                    -> ControlStatusAccent.Critical
                    ConnectionStatus.Initializing -> ControlStatusAccent.Neutral
                    ConnectionStatus.Connected -> ControlStatusAccent.Positive
                }
        }

    private fun buildSetupPrompt(sessionSnapshot: SessionSnapshot): ControlSetupPrompt? {
        if (!sessionSnapshot.hasRequiredPermissions) {
            return ControlSetupPrompt(
                title = "Grant Bluetooth access",
                detail = "Allow Nearby devices so Touckey can register the HID profile and talk to your desktop.",
                actions =
                    listOf(
                        ControlEnvironmentAction(
                            id = ControlEnvironmentActionId.GrantPermissions,
                            label = "Grant access",
                        ),
                    ),
            )
        }

        if (!sessionSnapshot.isBluetoothEnabled) {
            return ControlSetupPrompt(
                title = "Turn Bluetooth on",
                detail = "Touckey needs the phone Bluetooth radio enabled before it can act as a keyboard and touchpad.",
                actions =
                    listOf(
                        ControlEnvironmentAction(
                            id = ControlEnvironmentActionId.EnableBluetooth,
                            label = "Enable Bluetooth",
                        ),
                    ),
            )
        }

        if (!sessionSnapshot.isAppRegistered) {
            return ControlSetupPrompt(
                title = "Register Touckey",
                detail = "Initialize the Bluetooth HID identity so the phone can show up as a keyboard and touchpad.",
                actions =
                    listOf(
                        ControlEnvironmentAction(
                            id = ControlEnvironmentActionId.RegisterHid,
                            label = "Register HID",
                        ),
                    ),
            )
        }

        if (sessionSnapshot.hostControl.currentHost != null) {
            return null
        }

        if (sessionSnapshot.status == ConnectionStatus.Initializing) {
            return ControlSetupPrompt(
                title = "Preparing Touckey",
                detail = "The HID service is starting up. Refresh in a moment if this state does not clear.",
                actions =
                    listOf(
                        ControlEnvironmentAction(
                            id = ControlEnvironmentActionId.RefreshStatus,
                            label = "Refresh",
                        ),
                    ),
            )
        }

        if (sessionSnapshot.status == ConnectionStatus.Error || sessionSnapshot.status == ConnectionStatus.Unsupported) {
            return ControlSetupPrompt(
                title = "Connection needs attention",
                detail = sessionSnapshot.detail,
                actions =
                    listOf(
                        ControlEnvironmentAction(
                            id = ControlEnvironmentActionId.RefreshStatus,
                            label = "Refresh",
                        ),
                    ),
            )
        }

        return ControlSetupPrompt(
            title = "Pair with your desktop",
            detail =
                buildString {
                    append("Open the desktop Bluetooth settings and search for Touckey.")
                    sessionSnapshot.adapterName?.let { adapterName ->
                        append(" Phone name: ")
                        append(adapterName)
                        append(".")
                    }
                },
            actions =
                listOf(
                    ControlEnvironmentAction(
                        id = ControlEnvironmentActionId.MakeDiscoverable,
                        label = "Make discoverable",
                    ),
                    ControlEnvironmentAction(
                        id = ControlEnvironmentActionId.RefreshStatus,
                        label = "Refresh",
                    ),
                ),
        )
    }

    private fun buildConnectionActionable(sessionSnapshot: SessionSnapshot): Boolean =
        sessionSnapshot.isAppRegistered ||
            sessionSnapshot.hostControl.currentHost != null ||
            sessionSnapshot.hostControl.recentHosts.isNotEmpty() ||
            sessionSnapshot.hostControl.pendingOperation != null

    private fun buildConnectionPanelDetail(sessionSnapshot: SessionSnapshot): String =
        sessionSnapshot.hostControl.lastCommandMessage
            ?: when {
                sessionSnapshot.hostControl.currentHost != null -> "Manage the active desktop host."
                sessionSnapshot.hostControl.recentHosts.isNotEmpty() -> "Reconnect or switch to a recent desktop host."
                sessionSnapshot.isAppRegistered -> "No host is connected yet. Pair from your desktop Bluetooth settings or retry a recent host."
                else -> buildConnectionDetail(sessionSnapshot, sessionSnapshot.hostControl.currentHost?.name)
            }

    private fun buildRecentHostUiStates(sessionSnapshot: SessionSnapshot): List<ControlHostUiState> {
        val currentAddress = sessionSnapshot.hostControl.currentHost?.address
        return sessionSnapshot.hostControl.recentHosts.map { host ->
            host.toUiState(isCurrent = host.address == currentAddress)
        }
    }

    private fun buildConnectionActions(sessionSnapshot: SessionSnapshot): List<ControlConnectionAction> {
        val pending = sessionSnapshot.hostControl.pendingOperation
        val actions = mutableListOf<ControlConnectionAction>()

        if (sessionSnapshot.hostControl.currentHost != null) {
            actions +=
                ControlConnectionAction(
                    id = ControlConnectionActionId.Disconnect,
                    label = "Disconnect",
                    emphasized = true,
                    enabled = pending == null,
                )
        } else if (sessionSnapshot.isAppRegistered) {
            actions +=
                ControlConnectionAction(
                    id = ControlConnectionActionId.ReconnectLast,
                    label = "Reconnect",
                    emphasized = true,
                    enabled = pending == null,
                )
        }

        sessionSnapshot.hostControl.recentHosts
            .filterNot { host -> host.address == sessionSnapshot.hostControl.currentHost?.address }
            .forEach { host ->
                actions +=
                    ControlConnectionAction(
                        id = ControlConnectionActionId.ConnectHost,
                        label = "Connect ${host.name}",
                        targetAddress = host.address,
                        enabled = pending == null,
                    )
            }

        return actions
    }

    private fun HostDevice.toUiState(isCurrent: Boolean): ControlHostUiState =
        ControlHostUiState(
            name = name,
            address = address,
            platformLabel = platformLabel,
            isCurrent = isCurrent,
        )

    private fun HostPendingOperation.toShortLabel(): String =
        when (kind) {
            HostOperationKind.Connecting -> "Connecting"
            HostOperationKind.Disconnecting -> "Disconnecting"
            HostOperationKind.Switching -> "Switching"
        }

    private fun HostPendingOperation.toLabel(): String {
        val target = targetName ?: targetAddress ?: "host"
        return when (kind) {
            HostOperationKind.Connecting -> "Connecting to $target"
            HostOperationKind.Disconnecting -> "Disconnecting $target"
            HostOperationKind.Switching -> "Switching to $target"
        }
    }
}
