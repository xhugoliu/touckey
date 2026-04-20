package io.github.xhugoliu.touckey.feature.control

import io.github.xhugoliu.touckey.gesture.GestureInterpreter
import io.github.xhugoliu.touckey.input.ActionDispatcher
import io.github.xhugoliu.touckey.input.DispatchResult
import io.github.xhugoliu.touckey.input.MappingEngine
import io.github.xhugoliu.touckey.session.SessionController

class ControlPresenter(
    private val sessionController: SessionController,
    private val mappingEngine: MappingEngine,
    private val gestureInterpreter: GestureInterpreter,
    private val actionDispatcher: ActionDispatcher,
) {
    fun buildUiState(lastDispatchMessage: String? = null): ControlUiState {
        val sessionSnapshot = sessionController.snapshot()

        return ControlUiState(
            connectionLabel = sessionSnapshot.status.label,
            connectionDetail = sessionSnapshot.status.detail,
            hostLabel = sessionSnapshot.host?.let { "${it.name} · ${it.platformLabel}" } ?: "等待首个已配对桌面设备",
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
            lastDispatchMessage = lastDispatchMessage,
        )
    }

    fun dispatch(actionId: String): DispatchResult = actionDispatcher.dispatch(actionId)
}
