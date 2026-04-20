package io.github.xhugoliu.touckey.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.xhugoliu.touckey.app.AppContainer
import io.github.xhugoliu.touckey.feature.control.ControlScreen

@Composable
fun TouckeyApp(
    appContainer: AppContainer,
) {
    var lastDispatchMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val presenter = remember(appContainer) { appContainer.controlPresenter }
    val uiState = remember(lastDispatchMessage, presenter) {
        presenter.buildUiState(lastDispatchMessage)
    }

    ControlScreen(
        uiState = uiState,
        onQuickActionTap = { actionId ->
            lastDispatchMessage = presenter.dispatch(actionId).message
        },
    )
}
