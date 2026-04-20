package io.github.xhugoliu.touckey.session

import io.github.xhugoliu.touckey.core.model.ConnectionStatus
import io.github.xhugoliu.touckey.core.model.HostDevice

data class SessionSnapshot(
    val status: ConnectionStatus,
    val host: HostDevice?,
)

interface SessionController {
    fun snapshot(): SessionSnapshot
}
