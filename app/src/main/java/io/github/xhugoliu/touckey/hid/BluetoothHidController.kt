package io.github.xhugoliu.touckey.hid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.core.model.HostDevice
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.session.HostControlState
import io.github.xhugoliu.touckey.session.HostOperationKind
import io.github.xhugoliu.touckey.session.HostPendingOperation
import io.github.xhugoliu.touckey.session.SessionCommandResult
import io.github.xhugoliu.touckey.session.SessionController
import io.github.xhugoliu.touckey.session.SessionHostCommand
import io.github.xhugoliu.touckey.session.SessionSnapshot
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class BluetoothHidController(
    context: Context,
) : SessionController, HidGateway {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val sendExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mutableSnapshots = MutableStateFlow(SessionSnapshot.initial())

    private val stateLock = Any()
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var knownHostDevices: Map<String, BluetoothDevice> = emptyMap()
    private var recentHosts: List<HostDevice> = emptyList()
    private var pendingHostOperation: HostPendingOperation? = null
    private var profileReady: Boolean = false
    private var registrationRequested: Boolean = false
    private var appRegistered: Boolean = false
    private var profileRequestInFlight: Boolean = false
    private var profileUnavailable: Boolean = false
    private var lastDetailOverride: String? = null
    private var lastHostCommandMessage: String? = null
    private var mouseButtons: Int = 0
    private var keyboardModifiers: Int = 0
    private var keyboardKeys: List<Int> = emptyList()

    override val snapshots: StateFlow<SessionSnapshot> = mutableSnapshots.asStateFlow()

    init {
        refreshState()
    }

    override fun refreshState() {
        synchronized(stateLock) {
            updateSnapshot(lastDetailOverride)
        }
    }

    override fun ensureRegistered(): SessionCommandResult = synchronized(stateLock) {
        updateSnapshot(lastDetailOverride)

        val adapter = bluetoothAdapter()
            ?: return updateAndReturn(
                ConnectionStatus.Unsupported,
                "当前设备没有可用的蓝牙适配器，无法注册 Bluetooth HID Device profile。",
                accepted = false,
            )

        val missingPermissions = missingPermissions()
        if (missingPermissions.isNotEmpty()) {
            return updateAndReturn(
                ConnectionStatus.MissingPermission,
                "还缺少 Nearby devices 权限，先授予蓝牙连接和广播权限。",
                accepted = false,
            )
        }

        if (!adapter.isEnabled) {
            return updateAndReturn(
                ConnectionStatus.BluetoothDisabled,
                "蓝牙当前是关闭状态，请先打开蓝牙。",
                accepted = false,
            )
        }

        if (profileUnavailable) {
            return updateAndReturn(
                ConnectionStatus.Unsupported,
                "系统没有给当前设备提供 Bluetooth HID Device profile。",
                accepted = false,
            )
        }

        if (hidDevice == null) {
            if (!profileRequestInFlight) {
                profileRequestInFlight = true
                val ok = adapter.getProfileProxy(appContext, serviceListener, BluetoothProfile.HID_DEVICE)
                if (!ok) {
                    profileRequestInFlight = false
                    profileUnavailable = true
                    return updateAndReturn(
                        ConnectionStatus.Unsupported,
                        "无法向系统请求 Bluetooth HID 设备代理，当前手机可能不支持该 profile。",
                        accepted = false,
                    )
                }
            }

            return updateAndReturn(
                ConnectionStatus.Initializing,
                "正在连接系统蓝牙 HID 服务，稍等片刻后再试一次。",
                accepted = false,
            )
        }

        if (appRegistered) {
            return updateAndReturn(
                status = ConnectionStatus.Ready,
                detail =
                    if (connectedHost != null) {
                        "已连接到 ${connectedHost?.name ?: connectedHost?.address ?: "主机"}，可以发送键盘和媒体 report。"
                    } else {
                        "HID 已注册。现在可在电脑蓝牙设置里搜索手机名 ${adapter.name ?: "Touckey"} 并完成配对。"
                    },
                accepted = true,
            )
        }

        registrationRequested = true
        val registerAccepted =
            hidDevice?.registerApp(
                BluetoothHidDescriptor.sdpRecord,
                null,
                BluetoothHidDescriptor.qosOut,
                callbackExecutor,
                hidCallback,
            ) == true

        return if (registerAccepted) {
            updateAndReturn(
                ConnectionStatus.Initializing,
                "HID 注册请求已发出，等待系统完成注册回调。",
                accepted = true,
            )
        } else {
            registrationRequested = false
            updateAndReturn(
                ConnectionStatus.Error,
                "系统拒绝了 HID 注册请求，请确认蓝牙已开启并重试。",
                accepted = false,
            )
        }
    }

    override fun performHostCommand(command: SessionHostCommand): SessionCommandResult =
        synchronized(stateLock) {
            val pending = pendingHostOperation
            if (pending != null) {
                val message = "正在${pending.kind.label}，请稍后再试。"
                lastHostCommandMessage = message
                updateSnapshot(lastDetailOverride)
                return SessionCommandResult(accepted = false, message = message)
            }

            val hid = hidDevice
                ?: return hostCommandRejected("系统蓝牙 HID 服务还没准备好。")

            if (!appRegistered) {
                return hostCommandRejected("Touckey 尚未注册 HID，请先完成注册。")
            }

            when (command) {
                SessionHostCommand.Disconnect -> performDisconnectCommand(hid)
                SessionHostCommand.ReconnectLast -> performReconnectLastCommand(hid)
                is SessionHostCommand.Connect -> performConnectCommand(hid, command.address)
            }
        }

    override fun send(action: InputAction): HidSendResult = synchronized(stateLock) {
        if (pendingHostOperation != null) {
            return HidSendResult(
                accepted = false,
                detail = "当前正在切换主机，动作不会被发送。",
            )
        }

        val host = connectedHost
            ?: return HidSendResult(
                accepted = false,
                detail = "当前没有已连接的桌面主机，动作不会被发送。",
            )

        val hid = hidDevice
            ?: return HidSendResult(
                accepted = false,
                detail = "系统蓝牙 HID 服务还没准备好。",
            )

        return when (val encoded = HidReportEncoder.encode(action, mouseButtons, keyboardModifiers, keyboardKeys)) {
            is HidEncodingResult.Unsupported ->
                HidSendResult(
                    accepted = false,
                    detail = encoded.message,
                )

            is HidEncodingResult.Supported -> {
                if (shouldQueueOnBackgroundThread(action)) {
                    mouseButtons = encoded.nextMouseButtons
                    keyboardModifiers = encoded.nextKeyboardModifiers
                    keyboardKeys = encoded.nextKeyboardKeys
                    HidSendResult(
                        accepted = true,
                        detail = "已发送 ${encoded.summary}。",
                    )
                        .also {
                            sendExecutor.execute {
                                encoded.packets.forEach { packet ->
                                    hid.sendReport(host, packet.reportId, packet.payload)
                                }
                            }
                        }
                } else {
                    val success =
                        encoded.packets.all { packet ->
                            hid.sendReport(host, packet.reportId, packet.payload)
                        }

                    if (success) {
                        mouseButtons = encoded.nextMouseButtons
                        keyboardModifiers = encoded.nextKeyboardModifiers
                        keyboardKeys = encoded.nextKeyboardKeys
                        HidSendResult(
                            accepted = true,
                            detail = "已发送 ${encoded.summary}。",
                        )
                    } else {
                        HidSendResult(
                            accepted = false,
                            detail = "系统没有接受 ${encoded.summary} 的 HID report。",
                        )
                    }
                }
            }
        }
    }

    private fun shouldQueueOnBackgroundThread(action: InputAction): Boolean =
        when (action) {
            is InputAction.PointerMoveAction -> true
            is InputAction.ScrollAction -> true
            is InputAction.MouseButtonPressAction -> true
            is InputAction.MouseButtonReleaseAction -> true
            is InputAction.MouseButtonClickAction -> true
            else -> false
        }

    private fun performDisconnectCommand(hid: BluetoothHidDevice): SessionCommandResult {
        val host = connectedHost
            ?: return hostCommandRejected("当前没有已连接的桌面主机。")
        val hostDevice = rememberHost(host)

        releaseHeldInput(hid, host)
        pendingHostOperation =
            HostPendingOperation(
                kind = HostOperationKind.Disconnecting,
                targetAddress = hostDevice.address,
                targetName = hostDevice.name,
            )

        val message = "正在断开 ${hostDevice.name}。"
        lastHostCommandMessage = message
        val accepted = hid.disconnect(host)
        if (!accepted) {
            pendingHostOperation = null
            val rejectedMessage = "系统没有接受断开 ${hostDevice.name} 的请求。"
            lastHostCommandMessage = rejectedMessage
            updateSnapshot(lastDetailOverride)
            return SessionCommandResult(accepted = false, message = rejectedMessage)
        }

        updateSnapshot(lastDetailOverride)
        return SessionCommandResult(accepted = true, message = message)
    }

    private fun performReconnectLastCommand(hid: BluetoothHidDevice): SessionCommandResult {
        val current = connectedHost
        if (current != null) {
            val host = rememberHost(current)
            return hostCommandRejected("当前已连接到 ${host.name}。")
        }

        val target = recentHosts.firstOrNull()
            ?: return hostCommandRejected("还没有可重新连接的历史主机。")

        return connectKnownHost(hid, target.address, switchingFrom = null)
    }

    private fun performConnectCommand(
        hid: BluetoothHidDevice,
        address: String,
    ): SessionCommandResult {
        val current = connectedHost
        if (current?.address == address) {
            val host = rememberHost(current)
            return hostCommandRejected("当前已连接到 ${host.name}。")
        }

        return connectKnownHost(hid, address, switchingFrom = current)
    }

    private fun connectKnownHost(
        hid: BluetoothHidDevice,
        address: String,
        switchingFrom: BluetoothDevice?,
    ): SessionCommandResult {
        val targetDevice = knownHostDevices[address]
            ?: return hostCommandRejected("无法连接未知主机 $address。")
        val target = rememberHost(targetDevice)

        if (switchingFrom != null) {
            val source = rememberHost(switchingFrom)
            releaseHeldInput(hid, switchingFrom)
            pendingHostOperation =
                HostPendingOperation(
                    kind = HostOperationKind.Switching,
                    targetAddress = target.address,
                    targetName = target.name,
                    sourceAddress = source.address,
                )
            val message = "正在从 ${source.name} 切换到 ${target.name}。"
            lastHostCommandMessage = message
            val accepted = hid.disconnect(switchingFrom)
            if (!accepted) {
                pendingHostOperation = null
                val rejectedMessage = "系统没有接受断开 ${source.name} 的请求，无法切换到 ${target.name}。"
                lastHostCommandMessage = rejectedMessage
                updateSnapshot(lastDetailOverride)
                return SessionCommandResult(accepted = false, message = rejectedMessage)
            }

            updateSnapshot(lastDetailOverride)
            return SessionCommandResult(accepted = true, message = message)
        }

        pendingHostOperation =
            HostPendingOperation(
                kind = HostOperationKind.Connecting,
                targetAddress = target.address,
                targetName = target.name,
            )
        val message = "正在连接 ${target.name}。"
        lastHostCommandMessage = message
        val accepted = hid.connect(targetDevice)
        if (!accepted) {
            pendingHostOperation = null
            val rejectedMessage = "系统没有接受连接 ${target.name} 的请求。"
            lastHostCommandMessage = rejectedMessage
            updateSnapshot(lastDetailOverride)
            return SessionCommandResult(accepted = false, message = rejectedMessage)
        }

        updateSnapshot(lastDetailOverride)
        return SessionCommandResult(accepted = true, message = message)
    }

    private fun hostCommandRejected(message: String): SessionCommandResult {
        lastHostCommandMessage = message
        updateSnapshot(lastDetailOverride)
        return SessionCommandResult(accepted = false, message = message)
    }

    private val serviceListener =
        object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(
                profile: Int,
                proxy: BluetoothProfile,
            ) {
                synchronized(stateLock) {
                    if (profile != BluetoothProfile.HID_DEVICE) {
                        return
                    }

                    hidDevice = proxy as? BluetoothHidDevice
                    profileReady = hidDevice != null
                    profileUnavailable = hidDevice == null
                    profileRequestInFlight = false
                    lastDetailOverride =
                        if (profileReady) {
                            "系统蓝牙 HID 服务已就绪，接下来可以注册当前应用。"
                        } else {
                            "系统返回了异常的 HID 代理对象。"
                        }
                    updateSnapshot(lastDetailOverride)

                    if (profileReady && registrationRequested) {
                        ensureRegistered()
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                synchronized(stateLock) {
                    if (profile != BluetoothProfile.HID_DEVICE) {
                        return
                    }

                    hidDevice = null
                    profileReady = false
                    profileRequestInFlight = false
                    appRegistered = false
                    connectedHost = null
                    pendingHostOperation = null
                    clearHeldInputState()
                    lastDetailOverride = "系统蓝牙 HID 服务已断开。"
                    updateSnapshot(lastDetailOverride)
                }
            }
        }

    private val hidCallback =
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(
                pluggedDevice: BluetoothDevice?,
                registered: Boolean,
            ) {
                synchronized(stateLock) {
                    appRegistered = registered
                    if (!registered) {
                        connectedHost = null
                        pendingHostOperation = null
                        clearHeldInputState()
                    }

                    lastDetailOverride =
                        if (registered) {
                            "HID 已注册。保持 Touckey 前台或前台服务运行，然后在电脑蓝牙设置里搜索当前手机。"
                        } else {
                            "HID 当前未注册。通常在应用不再处于前台时，系统会自动注销。"
                        }

                    if (registered && pluggedDevice != null) {
                        handleAutoConnectCandidate(pluggedDevice)
                    }

                    updateSnapshot(lastDetailOverride)
                }
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                synchronized(stateLock) {
                    handleConnectionStateChanged(device, state)
                    updateSnapshot(lastDetailOverride)
                }
            }
        }

    private fun handleAutoConnectCandidate(device: BluetoothDevice) {
        val candidate = rememberHost(device)
        val pending = pendingHostOperation
        if (pending != null && pending.targetAddress != candidate.address) {
            lastHostCommandMessage = "已发现 ${candidate.name}，当前仍在${pending.kind.label}。"
            return
        }

        if (pending == null) {
            pendingHostOperation =
                HostPendingOperation(
                    kind = HostOperationKind.Connecting,
                    targetAddress = candidate.address,
                    targetName = candidate.name,
                )
            lastHostCommandMessage = "正在连接 ${candidate.name}。"
        }

        val accepted = hidDevice?.connect(device) == true
        if (!accepted) {
            pendingHostOperation = null
            lastHostCommandMessage = "系统没有接受连接 ${candidate.name} 的请求。"
        }
    }

    private fun handleConnectionStateChanged(
        device: BluetoothDevice,
        state: Int,
    ) {
        val host = rememberHost(device)
        val pending = pendingHostOperation

        when (state) {
            BluetoothProfile.STATE_CONNECTING -> {
                if (pending == null) {
                    pendingHostOperation =
                        HostPendingOperation(
                            kind = HostOperationKind.Connecting,
                            targetAddress = host.address,
                            targetName = host.name,
                        )
                }
                lastDetailOverride = "主机 ${host.name} 正在建立 HID 连接。"
            }

            BluetoothProfile.STATE_CONNECTED -> {
                connectedHost = device
                pendingHostOperation = null
                clearHeldInputState()
                lastDetailOverride = "已连接到 ${host.name}，可以发送 report。"
                lastHostCommandMessage = lastDetailOverride
            }

            BluetoothProfile.STATE_DISCONNECTING -> {
                if (connectedHost?.address == host.address) {
                    connectedHost = null
                    clearHeldInputState()
                }
                lastDetailOverride = "主机 ${host.name} 正在断开。"
            }

            BluetoothProfile.STATE_DISCONNECTED -> {
                if (connectedHost?.address == host.address) {
                    connectedHost = null
                }
                clearHeldInputState()
                handleDisconnectedCallback(host, pending)
            }

            else -> {
                if (connectedHost?.address == host.address) {
                    connectedHost = null
                }
                clearHeldInputState()
                if (pending?.targetAddress == host.address || pending?.sourceAddress == host.address) {
                    pendingHostOperation = null
                }
                lastDetailOverride = "当前没有活跃主机连接，可继续等待配对或重新连接。"
            }
        }
    }

    private fun handleDisconnectedCallback(
        host: HostDevice,
        pending: HostPendingOperation?,
    ) {
        when {
            pending?.kind == HostOperationKind.Disconnecting && pending.targetAddress == host.address -> {
                pendingHostOperation = null
                lastDetailOverride = "已断开 ${host.name}。"
                lastHostCommandMessage = lastDetailOverride
            }

            pending?.kind == HostOperationKind.Switching && pending.sourceAddress == host.address -> {
                val targetAddress = pending.targetAddress
                val targetDevice = targetAddress?.let(knownHostDevices::get)
                if (targetDevice == null) {
                    pendingHostOperation = null
                    lastDetailOverride = "已断开 ${host.name}，但找不到要切换的目标主机。"
                    lastHostCommandMessage = lastDetailOverride
                    return
                }

                val target = rememberHost(targetDevice)
                pendingHostOperation =
                    HostPendingOperation(
                        kind = HostOperationKind.Connecting,
                        targetAddress = target.address,
                        targetName = target.name,
                    )
                lastDetailOverride = "已断开 ${host.name}，正在连接 ${target.name}。"
                lastHostCommandMessage = lastDetailOverride
                val accepted = hidDevice?.connect(targetDevice) == true
                if (!accepted) {
                    pendingHostOperation = null
                    lastDetailOverride = "系统没有接受连接 ${target.name} 的请求。"
                    lastHostCommandMessage = lastDetailOverride
                }
            }

            pending?.kind == HostOperationKind.Connecting && pending.targetAddress == host.address -> {
                pendingHostOperation = null
                lastDetailOverride = "连接 ${host.name} 未完成，可稍后重试。"
                lastHostCommandMessage = lastDetailOverride
            }

            else -> {
                lastDetailOverride = "当前没有活跃主机连接，可继续等待配对或重新连接。"
            }
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? = bluetoothManager.adapter

    private fun missingPermissions(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return emptyList()
        }

        return requiredPermissions().filterNot(::hasPermission)
    }

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            emptyList()
        }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun updateAndReturn(
        status: ConnectionStatus,
        detail: String,
        accepted: Boolean,
    ): SessionCommandResult {
        lastDetailOverride = detail
        updateSnapshot(detail)
        return SessionCommandResult(accepted = accepted, message = detail)
    }

    private fun updateSnapshot(detailOverride: String?) {
        val adapter = bluetoothAdapter()
        val missingPermissions = missingPermissions()
        val host = connectedHost?.toHostDevice()
        val adapterName =
            if (adapter != null && missingPermissions.isEmpty()) {
                adapter.name
            } else {
                null
            }

        val status =
            when {
                adapter == null -> ConnectionStatus.Unsupported
                missingPermissions.isNotEmpty() -> ConnectionStatus.MissingPermission
                !adapter.isEnabled -> ConnectionStatus.BluetoothDisabled
                profileUnavailable -> ConnectionStatus.Unsupported
                appRegistered -> ConnectionStatus.Ready
                profileRequestInFlight || registrationRequested -> ConnectionStatus.Initializing
                profileReady -> ConnectionStatus.NeedsRegistration
                else -> ConnectionStatus.NeedsRegistration
            }

        val detail =
            detailOverride
                ?: when (status) {
                    ConnectionStatus.Unsupported -> "当前设备或系统没有提供 Bluetooth HID Device profile。"
                    ConnectionStatus.MissingPermission -> "还缺少 Nearby devices 权限，无法注册 HID 或让电脑发现当前手机。"
                    ConnectionStatus.BluetoothDisabled -> "蓝牙关闭时无法初始化 HID profile。"
                    ConnectionStatus.Initializing -> "正在等待系统初始化蓝牙 HID 服务。"
                    ConnectionStatus.NeedsRegistration -> "蓝牙环境已满足，可以开始注册 Touckey 的 HID 设备身份。"
                    ConnectionStatus.Ready -> "HID 已注册。现在可以让电脑搜索手机蓝牙并进行配对。"
                    ConnectionStatus.Connected -> "已连接到桌面主机，可以发送快捷动作。"
                    ConnectionStatus.Error -> "蓝牙 HID 初始化出现错误。"
                }
        val hostControl =
            HostControlState(
                currentHost = host,
                recentHosts = recentHosts,
                pendingOperation = pendingHostOperation,
                lastCommandMessage = lastHostCommandMessage,
            )

        mutableSnapshots.value =
            SessionSnapshot(
                status = status,
                detail = detail,
                host = host,
                hostControl = hostControl,
                hasRequiredPermissions = missingPermissions.isEmpty(),
                missingPermissions = missingPermissions,
                isBluetoothEnabled = adapter?.isEnabled == true,
                isProfileReady = profileReady,
                isAppRegistered = appRegistered,
                adapterName = adapterName,
                isKeepAliveServiceRunning = BluetoothHidForegroundService.isRunning(),
            )
    }

    private fun releaseHeldInput(
        hid: BluetoothHidDevice,
        host: BluetoothDevice,
    ) {
        HidReportEncoder
            .releaseAllPackets(
                currentMouseButtons = mouseButtons,
                currentKeyboardModifiers = keyboardModifiers,
                currentKeyboardKeys = keyboardKeys,
            )
            .forEach { packet ->
                hid.sendReport(host, packet.reportId, packet.payload)
            }
        clearHeldInputState()
    }

    private fun clearHeldInputState() {
        mouseButtons = 0
        keyboardModifiers = 0
        keyboardKeys = emptyList()
    }

    private fun rememberHost(device: BluetoothDevice): HostDevice {
        val host = device.toHostDevice()
        knownHostDevices = knownHostDevices + (host.address to device)
        recentHosts =
            (listOf(host) + recentHosts.filterNot { it.address == host.address })
                .take(MAX_RECENT_HOSTS)
        return host
    }

    private fun BluetoothDevice.toHostDevice(): HostDevice =
        HostDevice(
            name = name ?: "未知主机",
            address = address,
            platformLabel = "蓝牙主机",
        )

    private val HostOperationKind.label: String
        get() =
            when (this) {
                HostOperationKind.Connecting -> "连接主机"
                HostOperationKind.Disconnecting -> "断开主机"
                HostOperationKind.Switching -> "切换主机"
            }

    private companion object {
        const val MAX_RECENT_HOSTS = 5
    }
}
