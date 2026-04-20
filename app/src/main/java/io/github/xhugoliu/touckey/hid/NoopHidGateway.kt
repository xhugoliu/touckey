package io.github.xhugoliu.touckey.hid

import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.summary

class NoopHidGateway : HidGateway {
    override fun send(action: InputAction): HidSendResult =
        HidSendResult(
            accepted = false,
            detail = "骨架阶段：动作已解析为 ${action.summary()}，但 HID 服务层尚未接入。",
        )
}
