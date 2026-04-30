package io.github.xhugoliu.touckey.app

import android.content.Context
import io.github.xhugoliu.touckey.feature.control.ControlPresenter
import io.github.xhugoliu.touckey.hid.BluetoothHidController
import io.github.xhugoliu.touckey.hid.HidGateway
import io.github.xhugoliu.touckey.input.ActionDispatcher
import io.github.xhugoliu.touckey.input.QueuedActionDispatcher
import io.github.xhugoliu.touckey.input.surface.UserSurfaceProfileStore
import io.github.xhugoliu.touckey.session.SessionController

class AppContainer(
    appContext: Context,
) {
    private val bluetoothHidController = BluetoothHidController(appContext)
    val hidGateway: HidGateway = bluetoothHidController
    val sessionController: SessionController = bluetoothHidController
    val userSurfaceProfileStore = UserSurfaceProfileStore(appContext)
    val actionDispatcher: ActionDispatcher = QueuedActionDispatcher(hidGateway, sessionController)
    val controlPresenter: ControlPresenter =
        ControlPresenter(
            actionDispatcher = actionDispatcher,
        )
}
