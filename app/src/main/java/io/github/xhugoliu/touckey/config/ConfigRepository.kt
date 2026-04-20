package io.github.xhugoliu.touckey.config

import io.github.xhugoliu.touckey.gesture.GesturePreset
import io.github.xhugoliu.touckey.input.QuickActionDefinition

interface ConfigRepository {
    fun loadQuickActions(): List<QuickActionDefinition>
    fun loadGesturePresets(): List<GesturePreset>
}
