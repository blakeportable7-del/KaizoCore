package com.ironmonone.app.stream

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The address the app prints for the PC (rc32 audit P3 #79). It was the first IPv4 of any interface that was up, so with
 * Wi-Fi off it was a mobile data address (a carrier's 10.x, or a clat interface's 192.0.0.4) that no PC can open.
 */
class StreamAddressTest {
    private fun c(name: String, vararg ip: String, cellular: Boolean = false) = StreamHub.Candidate(name, ip.toList(), cellular)

    @Test
    fun `mobile data and its clat interface are never the address, Wi-Fi and a hotspot are`() {
        assertNull(StreamHub.pickAddress(listOf(c("rmnet_data0", "10.37.1.2", cellular = true), c("v4-rmnet_data0", "192.0.0.4"))),
            "Wi-Fi off: no address rather than the carrier's")
        assertEquals("192.168.43.1", StreamHub.pickAddress(listOf(c("rmnet_data0", "10.37.1.2", cellular = true), c("swlan0", "192.168.43.1"))),
            "the phone's hotspot, which the PC is on")
        assertEquals("192.168.1.20", StreamHub.pickAddress(listOf(c("rmnet_data0", "10.37.1.2", cellular = true), c("swlan0", "192.168.43.1"),
            c("wlan0", "192.168.1.20"))), "Wi-Fi first")
    }

    @Test
    fun `without the system's word the radios' own names leave mobile data out, and only a neighbour's address is taken`() {
        assertNull(StreamHub.pickAddress(listOf(c("rmnet_data1", "10.20.30.40"), c("ccmni0", "100.70.1.2"), c("clat4", "192.0.0.4"))),
            "Qualcomm's and MediaTek's radios, and a clat interface")
        assertNull(StreamHub.pickAddress(listOf(c("data0", "100.70.1.2", cellular = true))), "any name the system calls mobile data")
        assertEquals("100.101.2.3", StreamHub.pickAddress(listOf(c("tun0", "100.101.2.3"))), "a VPN such as Tailscale, which the server admits")
        assertEquals("192.168.43.1", StreamHub.pickAddress(listOf(c("tun0", "100.101.2.3"), c("ap0", "192.168.43.1"))), "a hotspot before a VPN")
        assertEquals("172.20.10.1", StreamHub.pickAddress(listOf(c("wlan1", "172.20.10.1"))))
        assertNull(StreamHub.pickAddress(listOf(c("wlan0", "203.0.113.5"))), "a public address: no PC beside it shares one")
        assertNull(StreamHub.pickAddress(emptyList()))
    }

    @Test
    fun `the system is asked which interfaces carry mobile data, with the permission that takes`() {
        val hub = File("src/main/kotlin/com/ironmonone/app/stream/StreamHub.kt").readText()
        assertTrue("hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)" in hub)
        assertTrue("com.ironmonone.app.stream.StreamHub.appContext = applicationContext" in File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText())
        assertTrue("android.permission.ACCESS_NETWORK_STATE" in File("src/main/AndroidManifest.xml").readText())
    }
}
