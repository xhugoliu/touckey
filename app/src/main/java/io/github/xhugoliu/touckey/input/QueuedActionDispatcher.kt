package io.github.xhugoliu.touckey.input

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.hid.HidGateway
import io.github.xhugoliu.touckey.session.SessionController

class QueuedActionDispatcher(
    private val hidGateway: HidGateway,
    private val sessionController: SessionController,
) : ActionDispatcher {
    override fun dispatch(action: InputAction): DispatchResult {
        if (sessionController.snapshot().status != ConnectionStatus.Connected) {
            return DispatchResult(
                accepted = false,
                message = "当前还没有活跃主机连接，${action.summary()} 不会被发送。",
            )
        }

        val result = hidGateway.send(action)
        return DispatchResult(
            accepted = result.accepted,
            message = result.detail,
        )
    }
}
