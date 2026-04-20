package io.github.xhugoliu.touckey.session

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.core.model.HostDevice
import kotlinx.coroutines.flow.StateFlow

data class SessionSnapshot(
    val status: ConnectionStatus,
    val detail: String,
    val host: HostDevice?,
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
                hasRequiredPermissions = false,
                missingPermissions = emptyList(),
                isBluetoothEnabled = false,
                isProfileReady = false,
                isAppRegistered = false,
                adapterName = null,
                isKeepAliveServiceRunning = false,
            )
    }
}

data class SessionCommandResult(
    val accepted: Boolean,
    val message: String,
)

interface SessionController {
    val snapshots: StateFlow<SessionSnapshot>

    fun snapshot(): SessionSnapshot = snapshots.value

    fun refreshState()

    fun ensureRegistered(): SessionCommandResult
}
