package io.github.xhugoliu.touckey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xhugoliu.touckey.app.AppContainer
import io.github.xhugoliu.touckey.feature.control.ControlEnvironmentActionId
import io.github.xhugoliu.touckey.feature.control.ControlScreen
import io.github.xhugoliu.touckey.input.InputAction

@Composable
fun TouckeyApp(
    appContainer: AppContainer,
    onEnvironmentAction: (ControlEnvironmentActionId) -> String,
) {
    var lastDispatchMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val presenter = remember(appContainer) { appContainer.controlPresenter }
    val sessionSnapshot by appContainer.sessionController.snapshots.collectAsStateWithLifecycle()
    val uiState = remember(sessionSnapshot, lastDispatchMessage, presenter) {
        presenter.buildUiState(
            sessionSnapshot = sessionSnapshot,
            lastDispatchMessage = lastDispatchMessage,
        )
    }

    ControlScreen(
        uiState = uiState,
        onQuickActionTap = { actionId ->
            lastDispatchMessage = presenter.dispatch(actionId).message
        },
        onTouchpadAction = { action, shouldSurfaceResult ->
            val result = presenter.dispatch(action)
            if (!result.accepted || shouldSurfaceResult) {
                lastDispatchMessage = result.message
            }
        },
        onEnvironmentActionTap = { actionId ->
            lastDispatchMessage = onEnvironmentAction(actionId)
        },
    )
}
