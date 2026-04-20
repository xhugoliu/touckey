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
import io.github.xhugoliu.touckey.session.SessionCommandResult
import io.github.xhugoliu.touckey.session.SessionController
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
    private val mutableSnapshots = MutableStateFlow(SessionSnapshot.initial())

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var profileReady: Boolean = false
    private var registrationRequested: Boolean = false
    private var appRegistered: Boolean = false
    private var profileRequestInFlight: Boolean = false
    private var profileUnavailable: Boolean = false
    private var lastDetailOverride: String? = null

    override val snapshots: StateFlow<SessionSnapshot> = mutableSnapshots.asStateFlow()

    init {
        refreshState()
    }

    override fun refreshState() {
        updateSnapshot(lastDetailOverride)
    }

    override fun ensureRegistered(): SessionCommandResult {
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
                status = if (connectedHost != null) ConnectionStatus.Connected else ConnectionStatus.Ready,
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

    override fun send(action: InputAction): HidSendResult {
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

        return when (val encoded = HidReportEncoder.encode(action)) {
            is HidEncodingResult.Unsupported ->
                HidSendResult(
                    accepted = false,
                    detail = encoded.message,
                )

            is HidEncodingResult.Supported -> {
                val success =
                    encoded.packets.all { packet ->
                        hid.sendReport(host, packet.reportId, packet.payload)
                    }

                if (success) {
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

    private val serviceListener =
        object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(
                profile: Int,
                proxy: BluetoothProfile,
            ) {
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

            override fun onServiceDisconnected(profile: Int) {
                if (profile != BluetoothProfile.HID_DEVICE) {
                    return
                }

                hidDevice = null
                profileReady = false
                profileRequestInFlight = false
                appRegistered = false
                connectedHost = null
                lastDetailOverride = "系统蓝牙 HID 服务已断开。"
                updateSnapshot(lastDetailOverride)
            }
        }

    private val hidCallback =
        object : BluetoothHidDevice.Callback() {
            override fun onAppStatusChanged(
                pluggedDevice: BluetoothDevice?,
                registered: Boolean,
            ) {
                appRegistered = registered
                if (!registered) {
                    connectedHost = null
                }

                lastDetailOverride =
                    if (registered) {
                        "HID 已注册。保持 Touckey 前台或前台服务运行，然后在电脑蓝牙设置里搜索当前手机。"
                    } else {
                        "HID 当前未注册。通常在应用不再处于前台时，系统会自动注销。"
                    }

                if (registered && pluggedDevice != null) {
                    hidDevice?.connect(pluggedDevice)
                }

                updateSnapshot(lastDetailOverride)
            }

            override fun onConnectionStateChanged(
                device: BluetoothDevice,
                state: Int,
            ) {
                connectedHost =
                    when (state) {
                        BluetoothProfile.STATE_CONNECTED -> device
                        else -> null
                    }

                lastDetailOverride =
                    when (state) {
                        BluetoothProfile.STATE_CONNECTING -> "主机 ${device.name ?: device.address} 正在建立 HID 连接。"
                        BluetoothProfile.STATE_CONNECTED -> "已连接到 ${device.name ?: device.address}，可以发送 report。"
                        BluetoothProfile.STATE_DISCONNECTING -> "主机 ${device.name ?: device.address} 正在断开。"
                        else -> "当前没有活跃主机连接，可继续等待配对或重新连接。"
                    }
                updateSnapshot(lastDetailOverride)
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
                connectedHost != null -> ConnectionStatus.Connected
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

        mutableSnapshots.value =
            SessionSnapshot(
                status = status,
                detail = detail,
                host = host,
                hasRequiredPermissions = missingPermissions.isEmpty(),
                missingPermissions = missingPermissions,
                isBluetoothEnabled = adapter?.isEnabled == true,
                isProfileReady = profileReady,
                isAppRegistered = appRegistered,
                adapterName = adapterName,
                isKeepAliveServiceRunning = BluetoothHidForegroundService.isRunning(),
            )
    }

    private fun BluetoothDevice.toHostDevice(): HostDevice =
        HostDevice(
            name = name ?: "未知主机",
            address = address,
            platformLabel = "蓝牙主机",
        )
}
