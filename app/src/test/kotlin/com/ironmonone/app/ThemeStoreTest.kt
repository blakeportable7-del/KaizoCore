package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStoreTest {
    @Test
    fun `hex round trip, import and reset`() {
        assertEquals("FF222222", ThemeStore.hex(Color(0xFF222222)))
        assertEquals(Color(0xFF123456), ThemeStore.parse("#123456"))
        assertEquals(Color(0x80ABCDEF), ThemeStore.parse("80ABCDEF"))
        val before = ThemeStore.export()
        assertTrue(ThemeStore.import("FF111111,FF222222,FF333333,FF444444,FF555555,FF666666,FF777777,FF888888"))
        assertEquals(Color(0xFF111111), Pc.Page); assertEquals(Color(0xFF888888), Pc.Dim)
        assertFalse(ThemeStore.import("nonsense"))
        ThemeStore.reset()
        assertEquals(before, ThemeStore.export())
    }
}
