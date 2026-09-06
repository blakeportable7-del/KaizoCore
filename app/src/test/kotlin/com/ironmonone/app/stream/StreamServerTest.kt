package com.ironmonone.app.stream

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamServerTest {

    private fun get(port: Int, path: String): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return code to body
    }

    @Test
    fun `routes answer, the token gates everything, unknown paths are 404`() {
        var v = 1L
        val s = StreamServer("abcd", { "<h1>page</h1>" }, { """{"a":1}""" }, { v }, { "42" }, { "[]" })
        val port = s.start(0)
        try {
            assertEquals(200 to """{"a":1}""", get(port, "/state.json?k=abcd"))
            assertEquals(200 to "42", get(port, "/attempts?k=abcd"))
            assertEquals(200 to "<h1>page</h1>", get(port, "/tracker?k=abcd"))
            assertEquals(200 to "[]", get(port, "/dex.json?k=abcd"))
            assertEquals(403, get(port, "/state.json").first)
            assertEquals(403, get(port, "/state.json?k=nope").first)
            assertEquals(404, get(port, "/other?k=abcd").first)
            assertContains(get(port, "/?k=abcd").second, "/tracker?k=abcd")
        } finally { s.stop() }
    }

    @Test
    fun `events is a server-sent stream that emits on version change`() {
        var v = 1L
        var state = """{"n":1}"""
        val s = StreamServer("t", { "" }, { state }, { v }, { "0" }, { "[]" })
        val port = s.start(0)
        try {
            Socket("127.0.0.1", port).use { sock ->
                sock.soTimeout = 5000
                sock.getOutputStream().write("GET /events?k=t HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                sock.getOutputStream().flush()
                val r = BufferedReader(InputStreamReader(sock.getInputStream()))
                val head = generateSequence { r.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                assertTrue(head.first().startsWith("HTTP/1.1 200"), head.first())
                assertTrue(head.any { it.contains("text/event-stream") })
                assertEquals("event: state", r.readLine())
                assertEquals("data: {\"n\":1}", r.readLine())
                state = """{"n":2}"""; v = 2
                // skip the blank separator, then the next event
                val lines = generateSequence { r.readLine() }.filter { it.isNotEmpty() }.take(2).toList()
                assertEquals(listOf("event: state", "data: {\"n\":2}"), lines)
            }
        } finally { s.stop() }
    }

    @Test
    fun `json writer escapes and shapes`() {
        assertEquals("""{"a":"x\"y\n","b":[1,2.5,true,null],"c":{}}""",
            Json.write(linkedMapOf("a" to "x\"y\n", "b" to listOf(1, 2.5, true, null), "c" to emptyMap<String, Any>())))
    }
}
