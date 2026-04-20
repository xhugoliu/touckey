package io.github.xhugoliu.touckey.app

import android.content.Context
import io.github.xhugoliu.touckey.config.ConfigRepository
import io.github.xhugoliu.touckey.config.InMemoryConfigRepository
import io.github.xhugoliu.touckey.feature.control.ControlPresenter
import io.github.xhugoliu.touckey.gesture.DefaultGestureInterpreter
import io.github.xhugoliu.touckey.gesture.GestureInterpreter
import io.github.xhugoliu.touckey.hid.BluetoothHidController
import io.github.xhugoliu.touckey.hid.HidGateway
import io.github.xhugoliu.touckey.input.ActionDispatcher
import io.github.xhugoliu.touckey.input.MappingEngine
import io.github.xhugoliu.touckey.input.PresetMappingEngine
import io.github.xhugoliu.touckey.input.QueuedActionDispatcher
import io.github.xhugoliu.touckey.session.SessionController

class AppContainer(
    appContext: Context,
) {
    val configRepository: ConfigRepository = InMemoryConfigRepository()
    private val bluetoothHidController = BluetoothHidController(appContext)
    val hidGateway: HidGateway = bluetoothHidController
    val sessionController: SessionController = bluetoothHidController
    val mappingEngine: MappingEngine = PresetMappingEngine(configRepository)
    val gestureInterpreter: GestureInterpreter = DefaultGestureInterpreter(configRepository)
    val actionDispatcher: ActionDispatcher = QueuedActionDispatcher(mappingEngine, hidGateway, sessionController)
    val controlPresenter: ControlPresenter =
        ControlPresenter(
            mappingEngine = mappingEngine,
            gestureInterpreter = gestureInterpreter,
            actionDispatcher = actionDispatcher,
        )
}
