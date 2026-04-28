package io.github.xhugoliu.touckey.core.model

enum class ConnectionStatus(val label: String) {
    Unsupported("不支持"),
    MissingPermission("缺少权限"),
    BluetoothDisabled("蓝牙已关闭"),
    Initializing("初始化中"),
    NeedsRegistration("待注册"),
    Ready("可配对"),
    // Kept for compatibility; host connectivity is now represented by HostControlState.
    Connected("已连接"),
    Error("错误"),
}
