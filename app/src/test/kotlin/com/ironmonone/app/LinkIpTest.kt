package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkIpTest {
    @Test
    fun `address to twelve digits and back`() {
        val d = LinkIp.digits("192.168.1.5")!!
        assertEquals(listOf("1","9","2","1","6","8","0","0","1","0","0","5"), d)
        val stored = d.mapIndexed { i, v -> LinkIp.key(i + 1) to v }.toMap()
        assertEquals("192.168.1.5", LinkIp.join(stored))
        assertEquals("0.0.0.0", LinkIp.join(emptyMap()))
        assertNull(LinkIp.digits("192.168.1")); assertNull(LinkIp.digits("300.1.1.1")); assertNull(LinkIp.digits("phone"))
    }
}
