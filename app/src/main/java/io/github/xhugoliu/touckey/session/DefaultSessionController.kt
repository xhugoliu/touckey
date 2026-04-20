package io.github.xhugoliu.touckey.session

import io.github.xhugoliu.touckey.core.model.ConnectionStatus

class DefaultSessionController : SessionController {
    override fun snapshot(): SessionSnapshot =
        SessionSnapshot(
            status = ConnectionStatus.Disconnected,
            host = null,
        )
}
