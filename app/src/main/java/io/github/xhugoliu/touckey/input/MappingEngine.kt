package io.github.xhugoliu.touckey.input

data class QuickActionDefinition(
    val id: String,
    val label: String,
    val detail: String,
    val action: InputAction,
)

interface MappingEngine {
    fun quickActions(): List<QuickActionDefinition>
    fun resolve(actionId: String): InputAction?
}
