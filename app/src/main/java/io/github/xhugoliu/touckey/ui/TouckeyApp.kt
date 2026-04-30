package io.github.xhugoliu.touckey.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xhugoliu.touckey.app.AppContainer
import io.github.xhugoliu.touckey.feature.control.ControlConnectionAction
import io.github.xhugoliu.touckey.feature.control.ControlEnvironmentActionId
import io.github.xhugoliu.touckey.feature.control.ControlScreen
import kotlinx.coroutines.launch

@Composable
fun TouckeyApp(
    appContainer: AppContainer,
    onEnvironmentAction: (ControlEnvironmentActionId) -> String,
    onConnectionAction: (ControlConnectionAction) -> String,
) {
    val presenter = remember(appContainer) { appContainer.controlPresenter }
    val sessionSnapshot by appContainer.sessionController.snapshots.collectAsStateWithLifecycle()
    val surfaceProfileSet by appContainer.userSurfaceProfileStore.profileSet.collectAsStateWithLifecycle()
    val uiState = remember(sessionSnapshot, presenter) { presenter.buildUiState(sessionSnapshot = sessionSnapshot) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    ControlScreen(
        uiState = uiState,
        surfaceProfileSet = surfaceProfileSet,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        onInputAction = { action, shouldSurfaceResult ->
            val result = presenter.dispatch(action)
            if (!result.accepted || shouldSurfaceResult) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(result.message)
                }
            }
        },
        onEnvironmentActionTap = { actionId ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(onEnvironmentAction(actionId))
            }
        },
        onConnectionActionTap = { action ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(onConnectionAction(action))
            }
        },
        onSurfaceProfileSave = { rows ->
            val message = appContainer.userSurfaceProfileStore.saveKeyboardRows(rows)
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
            }
        },
        onSurfaceProfileReset = {
            val message = appContainer.userSurfaceProfileStore.resetKeyboardRows()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
            }
        },
    )
}
