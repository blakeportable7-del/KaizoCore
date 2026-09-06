package com.ironmonone.app.stream

/**
 * The smallest JSON writer that is correct. org.json is stubbed out on the
 * JVM test classpath and a serialization plugin is a build-step's worth of
 * machinery for six object shapes, so this writes maps, lists, strings,
 * numbers, booleans and null and nothing else.
 */
object Json {
    fun write(v: Any?): String = StringBuilder().also { put(it, v) }.toString()

    private fun put(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> sb.append(if (v.isFinite()) v.toString() else "null")
            is Float -> sb.append(if (v.isFinite()) v.toString() else "null")
            is Map<*, *> -> {
                sb.append('{'); var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(','); first = false
                    str(sb, k.toString()); sb.append(':'); put(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('['); var first = true
                for (x in v) { if (!first) sb.append(','); first = false; put(sb, x) }
                sb.append(']')
            }
            is IntArray -> put(sb, v.toList())
            is Enum<*> -> str(sb, v.name)
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }
}
