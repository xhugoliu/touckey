package io.github.xhugoliu.touckey.input.surface

import android.content.Context
import io.github.xhugoliu.touckey.hid.HidCapabilityCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserSurfaceProfileStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val baseProfileSet = DefaultSurfaceProfiles.defaultKeyboard()
    private val mutableProfileSet = MutableStateFlow(loadProfileSet())

    val profileSet: StateFlow<DefaultSurfaceProfileSet> = mutableProfileSet.asStateFlow()

    fun saveKeyboardRows(rows: List<List<DefaultKeyboardKeySpec>>): String {
        val widthOverrides =
            UserSurfaceProfiles.keyboardWidthOverrides(
                baseProfileSet = baseProfileSet,
                rows = rows,
            )
        val bindingOverrides =
            UserSurfaceProfiles.keyboardBindingOverrides(
                baseProfileSet = baseProfileSet,
                rows = rows,
            )

        preferences.edit().apply {
            baseKeyboardRows().flatten().forEach { key ->
                remove(widthKey(key.zoneId))
                remove(bindingKey(key.zoneId))
            }
            widthOverrides.forEach { (zoneId, widthU) ->
                putFloat(widthKey(zoneId), widthU)
            }
            bindingOverrides.forEach { (zoneId, keyName) ->
                putString(bindingKey(zoneId), keyName)
            }
        }.apply()

        mutableProfileSet.value = loadProfileSet()
        return "Layout profile saved with ${widthOverrides.size} width edits and ${bindingOverrides.size} binding edits."
    }

    fun resetKeyboardRows(): String {
        preferences.edit().apply {
            baseKeyboardRows().flatten().forEach { key ->
                remove(widthKey(key.zoneId))
                remove(bindingKey(key.zoneId))
            }
        }.apply()

        mutableProfileSet.value = baseProfileSet
        return "Layout profile reset to the default keyboard."
    }

    private fun loadProfileSet(): DefaultSurfaceProfileSet {
        val rows =
            baseKeyboardRows().map { row ->
                row.map { key ->
                    key.copy(
                        keyName = preferences.keyboardBinding(key.zoneId, key.keyName),
                        weight = preferences.getFloat(widthKey(key.zoneId), key.weight),
                    )
                }
            }

        return UserSurfaceProfiles.keyboardProfileFromRows(
            baseProfileSet = baseProfileSet,
            rows = rows,
        )
    }

    private fun baseKeyboardRows(): List<List<DefaultKeyboardKeySpec>> =
        DefaultSurfaceProfiles.keyboardRows(
            layoutProfile = baseProfileSet.layoutProfile,
            keymapProfile = baseProfileSet.keymapProfile,
        )

    private fun widthKey(zoneId: String): String = "$WIDTH_PREFIX$zoneId"

    private fun bindingKey(zoneId: String): String = "$BINDING_PREFIX$zoneId"

    private fun android.content.SharedPreferences.keyboardBinding(
        zoneId: String,
        defaultKeyName: String,
    ): String {
        val savedKeyName = getString(bindingKey(zoneId), null) ?: return defaultKeyName
        return if (HidCapabilityCatalog.supportsKeyboardInput(savedKeyName)) {
            savedKeyName
        } else {
            defaultKeyName
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "touckey_surface_profiles"
        const val WIDTH_PREFIX = "keyboard.width."
        const val BINDING_PREFIX = "keyboard.binding."
    }
}
