package io.github.xhugoliu.touckey.core.model

enum class ConnectionStatus(val label: String, val detail: String) {
    Idle("未就绪", "HID 注册与前台服务骨架还未接入"),
    Discoverable("可配对", "等待桌面端发现与配对"),
    Connected("已连接", "已连接到当前主机，可以发送 report"),
    Disconnected("未连接", "当前没有桌面主机连接"),
}
