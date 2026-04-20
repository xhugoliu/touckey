package io.github.xhugoliu.touckey.app

import io.github.xhugoliu.touckey.config.ConfigRepository
import io.github.xhugoliu.touckey.config.InMemoryConfigRepository
import io.github.xhugoliu.touckey.feature.control.ControlPresenter
import io.github.xhugoliu.touckey.gesture.DefaultGestureInterpreter
import io.github.xhugoliu.touckey.gesture.GestureInterpreter
import io.github.xhugoliu.touckey.hid.HidGateway
import io.github.xhugoliu.touckey.hid.NoopHidGateway
import io.github.xhugoliu.touckey.input.ActionDispatcher
import io.github.xhugoliu.touckey.input.MappingEngine
import io.github.xhugoliu.touckey.input.PresetMappingEngine
import io.github.xhugoliu.touckey.input.QueuedActionDispatcher
import io.github.xhugoliu.touckey.session.DefaultSessionController
import io.github.xhugoliu.touckey.session.SessionController

class AppContainer {
    val configRepository: ConfigRepository = InMemoryConfigRepository()
    val hidGateway: HidGateway = NoopHidGateway()
    val sessionController: SessionController = DefaultSessionController()
    val mappingEngine: MappingEngine = PresetMappingEngine(configRepository)
    val gestureInterpreter: GestureInterpreter = DefaultGestureInterpreter(configRepository)
    val actionDispatcher: ActionDispatcher = QueuedActionDispatcher(mappingEngine, hidGateway, sessionController)
    val controlPresenter: ControlPresenter =
        ControlPresenter(
            sessionController = sessionController,
            mappingEngine = mappingEngine,
            gestureInterpreter = gestureInterpreter,
            actionDispatcher = actionDispatcher,
        )
}
