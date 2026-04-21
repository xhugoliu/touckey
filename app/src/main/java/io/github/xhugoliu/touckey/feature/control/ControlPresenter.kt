package io.github.xhugoliu.touckey.feature.control

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.input.ActionDispatcher
import io.github.xhugoliu.touckey.input.DispatchResult
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.session.SessionSnapshot

class ControlPresenter(
    private val actionDispatcher: ActionDispatcher,
) {
    fun buildUiState(sessionSnapshot: SessionSnapshot): ControlUiState {
        val hostName = sessionSnapshot.host?.name

        return ControlUiState(
            connection =
                ControlConnectionUiState(
                    label = buildConnectionLabel(sessionSnapshot, hostName),
                    detail = buildConnectionDetail(sessionSnapshot, hostName),
                    accent = buildAccent(sessionSnapshot.status),
                ),
            setupPrompt = buildSetupPrompt(sessionSnapshot),
            isInputEnabled = sessionSnapshot.status == ConnectionStatus.Connected,
        )
    }

    fun dispatch(action: InputAction): DispatchResult = actionDispatcher.dispatch(action)

    private fun buildConnectionLabel(
        sessionSnapshot: SessionSnapshot,
        hostName: String?,
    ): String =
        when (sessionSnapshot.status) {
            ConnectionStatus.Connected -> hostName ?: "Connected"
            ConnectionStatus.Ready -> "Ready"
            ConnectionStatus.NeedsRegistration -> "Register HID"
            ConnectionStatus.MissingPermission -> "Permission"
            ConnectionStatus.BluetoothDisabled -> "Bluetooth Off"
            ConnectionStatus.Initializing -> "Starting"
            ConnectionStatus.Unsupported -> "Unsupported"
            ConnectionStatus.Error -> "Attention"
        }

    private fun buildConnectionDetail(
        sessionSnapshot: SessionSnapshot,
        hostName: String?,
    ): String =
        when (sessionSnapshot.status) {
            ConnectionStatus.Connected -> "Connected to ${hostName ?: "desktop"}"
            ConnectionStatus.Ready -> "Registered and waiting for a desktop to connect."
            ConnectionStatus.NeedsRegistration -> "Bluetooth is available, but the HID profile is not registered yet."
            ConnectionStatus.MissingPermission -> "Nearby devices permission is required before Touckey can appear as a keyboard and touchpad."
            ConnectionStatus.BluetoothDisabled -> "Turn Bluetooth on to register and pair Touckey."
            ConnectionStatus.Initializing -> "Preparing the Bluetooth HID environment."
            ConnectionStatus.Unsupported -> sessionSnapshot.detail
            ConnectionStatus.Error -> sessionSnapshot.detail
        }

    private fun buildAccent(status: ConnectionStatus): ControlStatusAccent =
        when (status) {
            ConnectionStatus.Connected -> ControlStatusAccent.Positive
            ConnectionStatus.MissingPermission,
            ConnectionStatus.BluetoothDisabled,
            ConnectionStatus.NeedsRegistration,
            ConnectionStatus.Ready,
            -> ControlStatusAccent.Warning
            ConnectionStatus.Error,
            ConnectionStatus.Unsupported,
            -> ControlStatusAccent.Critical
            ConnectionStatus.Initializing -> ControlStatusAccent.Neutral
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

        if (sessionSnapshot.status == ConnectionStatus.Connected) {
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
}
