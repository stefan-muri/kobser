package com.kobser.app

import com.kobser.app.auto.ConnectionEntry
import com.kobser.app.auto.appendCapped
import com.kobser.app.auto.controllerLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoConnectionsTest {
    @Test
    fun `names the controllers that matter`() {
        assertEquals("Android Auto", controllerLabel("com.google.android.projection.gearhead"))
        assertEquals("System media controls", controllerLabel("com.android.systemui"))
        assertEquals("com.example.player", controllerLabel("com.example.player"))
    }

    @Test
    fun `newest first and capped`() {
        var list = emptyList<ConnectionEntry>()
        (1..35).forEach { list = appendCapped(list, ConnectionEntry(it.toLong(), "p", "connected")) }
        assertEquals(30, list.size)
        assertEquals(35L, list.first().timeMillis)
        assertEquals(6L, list.last().timeMillis)
    }
}
