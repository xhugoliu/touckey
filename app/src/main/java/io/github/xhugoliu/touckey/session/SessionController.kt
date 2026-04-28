package io.github.xhugoliu.touckey.session

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.core.model.HostDevice
import kotlinx.coroutines.flow.StateFlow

data class SessionSnapshot(
    val status: ConnectionStatus,
    val detail: String,
    val host: HostDevice?,
    val hostControl: HostControlState,
    val hasRequiredPermissions: Boolean,
    val missingPermissions: List<String>,
    val isBluetoothEnabled: Boolean,
    val isProfileReady: Boolean,
    val isAppRegistered: Boolean,
    val adapterName: String?,
    val isKeepAliveServiceRunning: Boolean,
) {
    companion object {
        fun initial(): SessionSnapshot =
            SessionSnapshot(
                status = ConnectionStatus.Initializing,
                detail = "正在准备 Touckey 的蓝牙 HID 环境。",
                host = null,
                hostControl = HostControlState(),
                hasRequiredPermissions = false,
                missingPermissions = emptyList(),
                isBluetoothEnabled = false,
                isProfileReady = false,
                isAppRegistered = false,
                adapterName = null,
                isKeepAliveServiceRunning = false,
            )
    }

    val canSendInput: Boolean
        get() = hostControl.canSendInput
}

data class SessionCommandResult(
    val accepted: Boolean,
    val message: String,
)

data class HostControlState(
    val currentHost: HostDevice? = null,
    val recentHosts: List<HostDevice> = emptyList(),
    val pendingOperation: HostPendingOperation? = null,
    val lastCommandMessage: String? = null,
) {
    val canSendInput: Boolean
        get() = currentHost != null && pendingOperation == null
}

data class HostPendingOperation(
    val kind: HostOperationKind,
    val targetAddress: String?,
    val targetName: String?,
    val sourceAddress: String? = null,
)

enum class HostOperationKind {
    Connecting,
    Disconnecting,
    Switching,
}

sealed interface SessionHostCommand {
    data object Disconnect : SessionHostCommand
    data object ReconnectLast : SessionHostCommand

    data class Connect(
        val address: String,
    ) : SessionHostCommand
}

interface SessionController {
    val snapshots: StateFlow<SessionSnapshot>

    fun snapshot(): SessionSnapshot = snapshots.value

    fun refreshState()

    fun ensureRegistered(): SessionCommandResult

    fun performHostCommand(command: SessionHostCommand): SessionCommandResult
}
