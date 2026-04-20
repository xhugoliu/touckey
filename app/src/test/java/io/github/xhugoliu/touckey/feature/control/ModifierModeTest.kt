package io.github.xhugoliu.touckey.feature.control

import org.junit.Assert.assertEquals
import org.junit.Test

class ModifierModeTest {
    @Test
    fun `preset mode clears armed modifiers after key tap`() {
        val next =
            modifiersAfterKeyTap(
                mode = ModifierMode.Preset,
                armedModifiers = listOf("Shift", "Cmd"),
            )

        assertEquals(emptyList<String>(), next)
    }

    @Test
    fun `hold mode keeps armed modifiers after key tap`() {
        val next =
            modifiersAfterKeyTap(
                mode = ModifierMode.Hold,
                armedModifiers = listOf("Ctrl"),
            )

        assertEquals(listOf("Ctrl"), next)
    }

    @Test
    fun `tapping modifier toggles armed state`() {
        val armed = toggleArmedModifier(emptyList(), "Alt")
        val cleared = toggleArmedModifier(armed, "Alt")

        assertEquals(listOf("Alt"), armed)
        assertEquals(emptyList<String>(), cleared)
    }
}
