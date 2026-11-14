package com.bnyro.wallpaper.sync.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SharedThemeValidationTest {
    @Test
    fun trimsThemeName() {
        assertEquals("Weekend", normalizedSharedThemeName("  Weekend  "))
    }

    @Test
    fun rejectsBlankThemeName() {
        assertThrows(IllegalArgumentException::class.java) { normalizedSharedThemeName("   ") }
    }

    @Test
    fun acceptsMaximumLengthThemeName() {
        assertEquals(80, normalizedSharedThemeName("N".repeat(80)).length)
    }

    @Test
    fun rejectsNamesLongerThanMaximum() {
        assertThrows(IllegalArgumentException::class.java) { normalizedSharedThemeName("N".repeat(81)) }
    }
}
