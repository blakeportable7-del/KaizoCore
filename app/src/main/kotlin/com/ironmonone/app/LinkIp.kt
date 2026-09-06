package com.ironmonone.app

/**
 * Gambatte takes the link server's IPv4 address as twelve separate digit
 * options: three zero-padded digits per octet, keys
 * gambatte_gb_link_network_server_ip_1 .. _12, each "0".."9". This maps a
 * dotted address to those and back, so the settings page can show one
 * field while the core gets what it wants.
 */
object LinkIp {
    fun key(n: Int) = "gambatte_gb_link_network_server_ip_$n"

    /** "192.168.1.5" -> ["1","9","2","1","6","8","0","0","1","0","0","5"]; null when not an IPv4 address. */
    fun digits(ip: String): List<String>? {
        val parts = ip.trim().split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull()?.takeIf { v -> v in 0..255 } ?: return null }
        return octets.flatMap { o -> "%03d".format(o).map { it.toString() } }
    }

    /** The address the twelve stored digits spell. */
    fun join(values: Map<String, String>): String =
        (0 until 4).joinToString(".") { o ->
            (1..3).joinToString("") { d -> values[key(o * 3 + d)] ?: "0" }.toInt().toString()
        }
}
