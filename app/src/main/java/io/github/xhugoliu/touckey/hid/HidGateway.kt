package io.github.xhugoliu.touckey.hid

import io.github.xhugoliu.touckey.input.InputAction

data class HidSendResult(
    val accepted: Boolean,
    val detail: String,
)

interface HidGateway {
    fun send(action: InputAction): HidSendResult
}
