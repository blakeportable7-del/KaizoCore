package com.ironmonone.app

/**
 * The part of a native crash's tombstone that says where it crashed (rc32 audit P2 #18).
 *
 * From Android 12 the trace ApplicationExitInfo hands over for a native crash is the protobuf tombstone
 * (AOSP system/core/debuggerd/proto/tombstone.proto), not text. The report used to keep runs of printable bytes from
 * it, which kept the library paths of the memory map and lost the signal's name, the cause and the crashing thread's
 * frames: the report said which libraries were loaded, not where it crashed. This reads the few fields that say so and
 * writes them as the text tombstone does (tombstone_proto_to_text.cpp), so a report reads the same either way:
 *
 *     signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x000000000000006b
 *     Cause: null pointer dereference
 *     #00 pc 000000000006e2f4  /data/app/.../libmelonds_libretro_android.so (DMA::Run+412) (BuildId: 4f2a...)
 *
 * Only varints and length-delimited fields are read; every other field is stepped over by its wire type, so a newer
 * tombstone with fields this does not know still reads. Anything that is not a tombstone throws, and the caller falls
 * back to the printable runs. No Android in it, so the tests feed it a tombstone built by hand.
 */
internal object TombstoneProto {

    /** Frames of the crashing thread kept, from the top: where it crashed and what called it. */
    const val FRAMES = 12

    /** A string longer than this (an abort message can be anything) is cut, so one field cannot fill the report. */
    private const val TEXT_CAP = 300

    /**
     * The tombstone's lines: the signal, the abort message, the causes, the crashing thread and its first [frames]
     * frames. Empty when the bytes hold none of them. Throws when they are not a protobuf message.
     */
    fun lines(bytes: ByteArray, frames: Int = FRAMES): List<String> {
        var arch = 0
        var pid = 0L
        var tid = 0L
        var signal: Signal? = null
        var abort = ""
        val causes = ArrayList<String>()
        val threads = ArrayList<IntRange>()
        val r = Reader(bytes, 0, bytes.size)
        while (r.more()) {
            val key = r.varint()
            when (field(key)) {
                1 -> arch = r.varintOr(key).toInt()
                5 -> pid = r.varintOr(key)
                6 -> tid = r.varintOr(key)
                10 -> signal = r.messageOr(key)?.let { signal(bytes, it) }
                14 -> abort = r.stringOr(key) ?: abort
                15 -> r.messageOr(key)?.let { cause(bytes, it) }?.let { causes += it }
                16 -> r.messageOr(key)?.let { threads += it }
                else -> r.skip(wire(key))
            }
        }
        val out = ArrayList<String>()
        // tombstone.proto's Architecture: ARM32 0, ARM64 1, X86 2, X86_64 3, RISCV64 4. Pointers print at their own width.
        val width = if (arch == 1 || arch == 3 || arch == 4) 16 else 8
        signal?.let { s ->
            val addr = if (s.hasAddress) "0x" + hex(s.address, width) else "--------"
            out += "signal ${s.number} (${s.name}), code ${s.code} (${s.codeName}), fault addr $addr"
        }
        if (abort.isNotEmpty()) out += "Abort message: '${clean(abort)}'"
        for (c in causes) out += "Cause: $c"
        // threads is a map<uint32, Thread>: each entry a message of its key (1) and the thread (2). The one that crashed is tid's.
        for (entry in threads) {
            val e = Reader(bytes, entry.first, entry.last + 1)
            var id = -1L
            var value: IntRange? = null
            while (e.more()) {
                val key = e.varint()
                when (field(key)) {
                    1 -> id = e.varintOr(key)
                    2 -> value = e.messageOr(key)
                    else -> e.skip(wire(key))
                }
            }
            if (id != tid || value == null) continue
            out += thread(bytes, value, pid, tid, frames, width)
            break
        }
        return out
    }

    private class Signal(val number: Long, val name: String, val code: Long, val codeName: String, val hasAddress: Boolean, val address: Long)

    private fun signal(b: ByteArray, at: IntRange): Signal {
        val r = Reader(b, at.first, at.last + 1)
        var number = 0L; var name = ""; var code = 0L; var codeName = ""; var has = false; var addr = 0L
        while (r.more()) {
            val key = r.varint()
            when (field(key)) {
                1 -> number = r.varintOr(key).toInt().toLong()
                2 -> name = r.stringOr(key) ?: name
                3 -> code = r.varintOr(key).toInt().toLong()
                4 -> codeName = r.stringOr(key) ?: codeName
                8 -> has = r.varintOr(key) != 0L
                9 -> addr = r.varintOr(key)
                else -> r.skip(wire(key))
            }
        }
        return Signal(number, clean(name), code, clean(codeName), has, addr)
    }

    /** Cause.human_readable (field 1), the line the text tombstone prints after "Cause: ". */
    private fun cause(b: ByteArray, at: IntRange): String? {
        val r = Reader(b, at.first, at.last + 1)
        var text: String? = null
        while (r.more()) {
            val key = r.varint()
            if (field(key) == 1) text = r.stringOr(key) ?: text else r.skip(wire(key))
        }
        return text?.let(::clean)?.takeIf { it.isNotEmpty() }
    }

    /** The crashing thread: its name, then its current_backtrace (field 4), one line a frame. */
    private fun thread(b: ByteArray, at: IntRange, pid: Long, tid: Long, frames: Int, width: Int): List<String> {
        val r = Reader(b, at.first, at.last + 1)
        var name = ""
        val out = ArrayList<String>()
        while (r.more()) {
            val key = r.varint()
            when (field(key)) {
                2 -> name = r.stringOr(key) ?: name
                4 -> {
                    val f = r.messageOr(key)
                    if (f != null && out.size < frames) out += frame(b, f, out.size, width)
                }
                else -> r.skip(wire(key))
            }
        }
        return listOf("pid: $pid, tid: $tid, name: ${clean(name)}") + out
    }

    /** One BacktraceFrame: rel_pc (1), function_name (4), function_offset (5), file_name (6), file_map_offset (7), build_id (8). */
    private fun frame(b: ByteArray, at: IntRange, index: Int, width: Int): String {
        val r = Reader(b, at.first, at.last + 1)
        var relPc = 0L; var function = ""; var offset = 0L; var file = ""; var mapOffset = 0L; var buildId = ""
        while (r.more()) {
            val key = r.varint()
            when (field(key)) {
                1 -> relPc = r.varintOr(key)
                4 -> function = r.stringOr(key) ?: function
                5 -> offset = r.varintOr(key)
                6 -> file = r.stringOr(key) ?: file
                7 -> mapOffset = r.varintOr(key)
                8 -> buildId = r.stringOr(key) ?: buildId
                else -> r.skip(wire(key))
            }
        }
        val sb = StringBuilder(String.format(java.util.Locale.US, "#%02d pc %s  %s", index, hex(relPc, width), clean(file)))
        if (mapOffset != 0L) sb.append(" (offset 0x").append(java.lang.Long.toHexString(mapOffset)).append(')')
        if (function.isNotEmpty()) {
            sb.append(" (").append(clean(function))
            if (offset != 0L) sb.append('+').append(offset)
            sb.append(')')
        }
        if (buildId.isNotEmpty()) sb.append(" (BuildId: ").append(clean(buildId)).append(')')
        return sb.toString()
    }

    private fun field(key: Long): Int = (key ushr 3).toInt()
    private fun wire(key: Long): Int = (key and 7L).toInt()

    /** [v] as [width] hex digits, unsigned. */
    private fun hex(v: Long, width: Int): String = java.lang.Long.toHexString(v).padStart(width, '0')

    /** One line of a report: no line breaks or control characters, at most [TEXT_CAP] characters. */
    private fun clean(s: String): String = s.take(TEXT_CAP).map { if (it < ' ' || it == '\u007f') ' ' else it }.joinToString("").trim()

    /** A protobuf message between [pos] and [end] of [b]: the reading half of the wire format, and no more. */
    private class Reader(private val b: ByteArray, private var pos: Int, private val end: Int) {
        init { require(pos in 0..end && end <= b.size) { "range" } }

        fun more(): Boolean = pos < end

        fun varint(): Long {
            var shift = 0
            var out = 0L
            while (true) {
                require(pos < end) { "varint runs off the message" }
                val x = b[pos++].toInt() and 0xFF
                out = out or ((x and 0x7F).toLong() shl shift)
                if (x and 0x80 == 0) return out
                shift += 7
                require(shift < 70) { "varint longer than ten bytes" }
            }
        }

        /** A varint field's value; a field of another wire type is stepped over and reads as 0. */
        fun varintOr(key: Long): Long = if (wire(key) == 0) varint() else { skip(wire(key)); 0L }

        /** A length-delimited field's bytes, as a range of [b]; null (and stepped over) for another wire type. */
        fun messageOr(key: Long): IntRange? {
            if (wire(key) != 2) { skip(wire(key)); return null }
            val n = varint()
            require(n >= 0 && n <= (end - pos).toLong()) { "length past the message" }
            val start = pos
            pos += n.toInt()
            return start until pos
        }

        fun stringOr(key: Long): String? = messageOr(key)?.let { String(b, it.first, it.last - it.first + 1, Charsets.UTF_8) }

        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> { require(end - pos >= 8) { "fixed64 past the message" }; pos += 8 }
                2 -> { val n = varint(); require(n >= 0 && n <= (end - pos).toLong()) { "length past the message" }; pos += n.toInt() }
                5 -> { require(end - pos >= 4) { "fixed32 past the message" }; pos += 4 }
                else -> throw IllegalArgumentException("wire type $wire is not in a tombstone")
            }
        }
    }
}
