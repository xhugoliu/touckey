package io.github.xhugoliu.touckey.input

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.hid.HidGateway
import io.github.xhugoliu.touckey.session.SessionController

class QueuedActionDispatcher(
    private val mappingEngine: MappingEngine,
    private val hidGateway: HidGateway,
    private val sessionController: SessionController,
) : ActionDispatcher {
    override fun dispatch(actionId: String): DispatchResult {
        val action = mappingEngine.resolve(actionId)
            ?: return DispatchResult(
                accepted = false,
                message = "未找到动作 $actionId，对应的映射还没定义。",
            )

        if (sessionController.snapshot().status != ConnectionStatus.Connected) {
            return DispatchResult(
                accepted = false,
                message = "骨架阶段：已解析 ${action.summary()}，但当前还没有活跃主机连接。",
            )
        }

        val result = hidGateway.send(action)
        return DispatchResult(
            accepted = result.accepted,
            message = result.detail,
        )
    }
}
