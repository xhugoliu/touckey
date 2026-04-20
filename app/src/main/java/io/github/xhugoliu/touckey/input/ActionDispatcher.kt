package io.github.xhugoliu.touckey.input

data class DispatchResult(
    val accepted: Boolean,
    val message: String,
)

interface ActionDispatcher {
    fun dispatch(actionId: String): DispatchResult

    fun dispatch(action: InputAction): DispatchResult
}
