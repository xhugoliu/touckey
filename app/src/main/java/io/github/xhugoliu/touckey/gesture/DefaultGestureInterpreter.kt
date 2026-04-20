package io.github.xhugoliu.touckey.gesture

import io.github.xhugoliu.touckey.config.ConfigRepository

class DefaultGestureInterpreter(
    private val configRepository: ConfigRepository,
) : GestureInterpreter {
    override fun supportedBindings(): List<GestureBinding> =
        configRepository.loadGesturePresets().map { preset ->
            GestureBinding(
                title = preset.kind.label,
                detail = preset.summary,
            )
        }
}
