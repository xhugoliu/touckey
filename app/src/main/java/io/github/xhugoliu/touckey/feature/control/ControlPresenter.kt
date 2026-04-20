package io.github.xhugoliu.touckey.feature.control

import io.github.xhugoliu.touckey.gesture.GestureInterpreter
import io.github.xhugoliu.touckey.input.ActionDispatcher
import io.github.xhugoliu.touckey.input.DispatchResult
import io.github.xhugoliu.touckey.input.MappingEngine
import io.github.xhugoliu.touckey.session.SessionController
import io.github.xhugoliu.touckey.session.SessionSnapshot

class ControlPresenter(
    private val mappingEngine: MappingEngine,
    private val gestureInterpreter: GestureInterpreter,
    private val actionDispatcher: ActionDispatcher,
) {
    fun buildUiState(
        sessionSnapshot: SessionSnapshot,
        lastDispatchMessage: String? = null,
    ): ControlUiState {
        return ControlUiState(
            connectionLabel = sessionSnapshot.status.label,
            connectionDetail = sessionSnapshot.detail,
            hostLabel =
                sessionSnapshot.host?.let { "${it.name} · ${it.address}" }
                    ?: "等待首个已配对桌面设备",
            adapterLabel = sessionSnapshot.adapterName ?: "当前手机蓝牙名尚不可用",
            quickActions =
                mappingEngine.quickActions().map {
                    ControlQuickAction(
                        id = it.id,
                        label = it.label,
                        detail = it.detail,
                    )
                },
            gestureHints =
                gestureInterpreter.supportedBindings().map {
                    ControlGestureHint(
                        title = it.title,
                        detail = it.detail,
                    )
                },
            environmentActions = buildEnvironmentActions(sessionSnapshot),
            canSendQuickActions = sessionSnapshot.status == io.github.xhugoliu.touckey.core.model.ConnectionStatus.Connected,
            foregroundHint =
                if (sessionSnapshot.isAppRegistered) {
                    "Android 的 BluetoothHidDevice 在应用不处于前台时可能被系统自动注销。测试配对时请保持 Touckey 打开，或启动前台服务。"
                } else {
                    null
                },
            lastDispatchMessage = lastDispatchMessage,
        )
    }

    fun dispatch(actionId: String): DispatchResult = actionDispatcher.dispatch(actionId)

    private fun buildEnvironmentActions(sessionSnapshot: SessionSnapshot): List<ControlEnvironmentAction> {
        val actions = mutableListOf<ControlEnvironmentAction>()

        if (!sessionSnapshot.hasRequiredPermissions) {
            actions +=
                ControlEnvironmentAction(
                    id = ControlEnvironmentActionId.GrantPermissions,
                    label = "授予蓝牙权限",
                    detail = "请求 Nearby devices 权限，允许注册 HID 与广播设备。",
                )
        }

        if (!sessionSnapshot.isBluetoothEnabled) {
            actions +=
                ControlEnvironmentAction(
                    id = ControlEnvironmentActionId.EnableBluetooth,
                    label = "开启蓝牙",
                    detail = "蓝牙关闭时无法注册 HID 设备身份。",
                )
        }

        if (sessionSnapshot.hasRequiredPermissions && sessionSnapshot.isBluetoothEnabled && !sessionSnapshot.isAppRegistered) {
            actions +=
                ControlEnvironmentAction(
                    id = ControlEnvironmentActionId.RegisterHid,
                    label = "注册 HID 设备",
                    detail = "向系统注册 Touckey 的键盘/鼠标/媒体控制 report。",
                )
        }

        if (sessionSnapshot.isAppRegistered && sessionSnapshot.host == null) {
            actions +=
                ControlEnvironmentAction(
                    id = ControlEnvironmentActionId.MakeDiscoverable,
                    label = "让电脑发现 5 分钟",
                    detail = "打开系统弹窗，让桌面端蓝牙设置能搜索到当前手机。",
                )
        }

        actions +=
            ControlEnvironmentAction(
                id = ControlEnvironmentActionId.RefreshStatus,
                label = "刷新状态",
                detail = "重新读取蓝牙、权限和 HID 注册状态。",
            )

        if (sessionSnapshot.isKeepAliveServiceRunning) {
            actions +=
                ControlEnvironmentAction(
                    id = ControlEnvironmentActionId.StopKeepAlive,
                    label = "停止前台服务",
                    detail = "停止保持 HID 前台存活的通知服务。",
                )
        }

        return actions
    }
}
