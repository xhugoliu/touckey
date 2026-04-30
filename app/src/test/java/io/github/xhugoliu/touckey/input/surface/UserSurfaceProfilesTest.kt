package io.github.xhugoliu.touckey.input.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSurfaceProfilesTest {
    @Test
    fun `keyboard profile builder writes edited key widths into schema frames`() {
        val base = DefaultSurfaceProfiles.defaultKeyboard()
        val rows =
            DefaultSurfaceProfiles
                .keyboardRows(base.layoutProfile, base.keymapProfile)
                .mapIndexed { rowIndex, row ->
                    if (rowIndex == 0) {
                        row.mapIndexed { keyIndex, key ->
                            if (keyIndex == 0) key.copy(weight = 2f) else key
                        }
                    } else {
                        row
                    }
                }

        val userProfile = UserSurfaceProfiles.keyboardProfileFromRows(base, rows)
        val page = userProfile.layoutProfile.pages.single()
        val variant = page.variants.single()
        val escape = variant.zones.first { zone -> zone.id == "key-escape" }
        val f1 = variant.zones.first { zone -> zone.id == "key-f1" }

        assertEquals(UserSurfaceProfiles.USER_LAYOUT_PROFILE_ID, userProfile.layoutProfile.id)
        assertEquals(UserSurfaceProfiles.USER_KEYMAP_PROFILE_ID, userProfile.keymapProfile.id)
        assertEquals(2f, escape.frame.widthU)
        assertEquals(2f, f1.frame.xU)
        assertEquals(19f, variant.grid.columnsU)
        assertTrue(
            SurfaceProfileValidator.validate(
                layoutProfile = userProfile.layoutProfile,
                keymapProfile = userProfile.keymapProfile,
            ).isValid,
        )
    }

    @Test
    fun `keyboard profile builder writes edited key bindings into the keymap`() {
        val base = DefaultSurfaceProfiles.defaultKeyboard()
        val rows =
            DefaultSurfaceProfiles
                .keyboardRows(base.layoutProfile, base.keymapProfile)
                .map { row ->
                    row.map { key ->
                        if (key.zoneId == "key-escape") key.copy(keyName = "A") else key
                    }
                }

        val userProfile = UserSurfaceProfiles.keyboardProfileFromRows(base, rows)

        assertEquals(
            BehaviorBinding.Key("A"),
            userProfile.keymapProfile.bindingFor(
                zoneId = "key-escape",
                pageId = DefaultSurfaceProfiles.KEYBOARD_PAGE_ID,
            ),
        )
        assertTrue(
            SurfaceProfileValidator.validate(
                layoutProfile = userProfile.layoutProfile,
                keymapProfile = userProfile.keymapProfile,
            ).isValid,
        )
    }

    @Test
    fun `width override extraction ignores keys that still match the default profile`() {
        val base = DefaultSurfaceProfiles.defaultKeyboard()
        val rows =
            DefaultSurfaceProfiles
                .keyboardRows(base.layoutProfile, base.keymapProfile)
                .map { row ->
                    row.map { key ->
                        if (key.zoneId == "key-space") key.copy(weight = 2f) else key
                    }
                }

        assertEquals(
            mapOf("key-space" to 2f),
            UserSurfaceProfiles.keyboardWidthOverrides(base, rows),
        )
    }

    @Test
    fun `binding override extraction ignores keys that still match the default profile`() {
        val base = DefaultSurfaceProfiles.defaultKeyboard()
        val rows =
            DefaultSurfaceProfiles
                .keyboardRows(base.layoutProfile, base.keymapProfile)
                .map { row ->
                    row.map { key ->
                        if (key.zoneId == "key-space") key.copy(keyName = "Enter") else key
                    }
                }

        assertEquals(
            mapOf("key-space" to "Enter"),
            UserSurfaceProfiles.keyboardBindingOverrides(base, rows),
        )
    }
}
