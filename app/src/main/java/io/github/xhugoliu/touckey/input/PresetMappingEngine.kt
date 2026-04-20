package io.github.xhugoliu.touckey.input

import io.github.xhugoliu.touckey.config.ConfigRepository

class PresetMappingEngine(
    private val configRepository: ConfigRepository,
) : MappingEngine {
    override fun quickActions(): List<QuickActionDefinition> = configRepository.loadQuickActions()

    override fun resolve(actionId: String): InputAction? =
        quickActions()
            .firstOrNull { it.id == actionId }
            ?.action
}
