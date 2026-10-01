package com.ironmonone.app

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.security.MessageDigest

/**
 * The real sprite_core.h, compiled to WebAssembly and driven from a JVM test.
 *
 * The per-frame logic of "Play as your Pokemon" runs inside the emulator's video callback,
 * where no unit test can go, so it is written in one header with no Android in it
 * (libretrodroid/src/main/cpp/sprite_core.h) and built here, unchanged, for a second target:
 *
 *   NDK clang++ --target=wasm32 (the NDK ships the wasm32 backend)
 *     -> NDK ld.lld -flavor wasm
 *     -> Node runs it (libretrodroid/src/test/native/sprite_core_runner.js), and this class talks to
 *        that over stdin and stdout.
 *
 * Nothing is mocked: a scenario writes bytes where the game keeps its structs and reads back what
 * the core wrote. The build is cached in build/spriteisme-wasm keyed on the sources' hash, so a
 * changed header rebuilds itself (which is what lets a deliberately broken header turn a test red).
 *
 * Needs the Android NDK (found through local.properties' sdk.dir, ANDROID_HOME or ANDROID_SDK_ROOT)
 * and node on the PATH. Without them [start] returns null and prints why, and the tests that need
 * it skip themselves, as the ROM tests do.
 */
class WasmSpriteCore private constructor(private val process: Process) : AutoCloseable {
    private val writer: BufferedWriter = process.outputStream.bufferedWriter()
    private val reader: BufferedReader = process.inputStream.bufferedReader()

    init {
        val hello = reader.readLine()
        check(hello == "ready") { "the wasm runner said: $hello" }
    }

    private fun send(line: String): String {
        writer.write(line); writer.newLine(); writer.flush()
        val answer = reader.readLine() ?: error("the wasm runner ended")
        check(answer == "=" || answer.startsWith("= ")) { "wasm runner: $answer (for: ${line.take(80)})" }
        return answer.removePrefix("=").trim()
    }

    fun write(kind: Int, offset: Int, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        send("W $kind $offset " + bytes.joinToString("") { "%02x".format(it) })
    }

    fun read(kind: Int, offset: Int, length: Int): ByteArray {
        val hex = send("R $kind $offset $length")
        return ByteArray(length) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun fill(kind: Int, offset: Int, length: Int, value: Int) { send("F $kind $offset $length $value") }

    /** Call an export with numeric arguments (or "@kind:offset" pointers); the result as an unsigned number. */
    fun call(fn: String, vararg args: Any): Long =
        send("C $fn " + args.joinToString(" ") { if (it is Long) it.toString() else it.toString() }).toLong()

    // ---- game memory, by the addresses a Gen 3 game uses
    fun writeAddr(addr: Long, bytes: ByteArray) {
        when (addr ushr 24) {
            2L -> write(KIND_EWRAM, (addr - 0x02000000L).toInt(), bytes)
            3L -> write(KIND_IWRAM, (addr - 0x03000000L).toInt(), bytes)
            5L -> write(KIND_PLTT, (addr - 0x05000000L).toInt(), bytes)
            else -> error("no memory at %08X".format(addr))
        }
    }

    fun readAddr(addr: Long, length: Int): ByteArray = when (addr ushr 24) {
        2L -> read(KIND_EWRAM, (addr - 0x02000000L).toInt(), length)
        3L -> read(KIND_IWRAM, (addr - 0x03000000L).toInt(), length)
        5L -> read(KIND_PLTT, (addr - 0x05000000L).toInt(), length)
        else -> error("no memory at %08X".format(addr))
    }

    override fun close() {
        runCatching { writer.write("Q"); writer.newLine(); writer.flush() }
        runCatching { process.destroy() }
    }

    companion object {
        const val KIND_IWRAM = 0
        const val KIND_EWRAM = 1
        const val KIND_PLTT = 2
        const val KIND_FRAME = 3
        const val KIND_SPRITE = 4
        const val KIND_SCRATCH = 5

        private val cppDir = File("../libretrodroid/src/main/cpp")
        private val nativeTestDir = File("../libretrodroid/src/test/native")
        private val outDir = File("build/spriteisme-wasm")

        private fun sdkDir(): File? {
            val fromProps = File("../local.properties").takeIf { it.isFile }?.readLines()
                ?.firstOrNull { it.startsWith("sdk.dir=") }?.removePrefix("sdk.dir=")?.trim()?.replace("\\\\", "\\")?.replace("\\:", ":")
            return listOfNotNull(fromProps, System.getenv("ANDROID_HOME"), System.getenv("ANDROID_SDK_ROOT"))
                .map(::File).firstOrNull { it.isDirectory }
        }

        private fun ndkBin(): File? {
            val ndkRoot = sdkDir()?.let { File(it, "ndk") }?.takeIf { it.isDirectory } ?: return null
            val hosts = listOf("windows-x86_64", "linux-x86_64", "darwin-x86_64")
            val versions = ndkRoot.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name } ?: return null
            for (v in versions) for (h in hosts) {
                val bin = File(v, "toolchains/llvm/prebuilt/$h/bin")
                if (bin.isDirectory) return bin
            }
            return null
        }

        private fun exe(bin: File, name: String): File =
            listOf(File(bin, "$name.exe"), File(bin, name)).firstOrNull { it.isFile } ?: File(bin, name)

        private fun nodeWorks(): Boolean = runCatching {
            val p = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
            p.inputStream.readBytes(); p.waitFor() == 0
        }.getOrDefault(false)

        private fun sha(vararg files: File): String {
            val md = MessageDigest.getInstance("SHA-1")
            for (f in files) md.update(f.readText().replace("\r\n", "\n").toByteArray())
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        @Volatile private var shared: WasmSpriteCore? = null
        @Volatile private var triedShared = false
        @Volatile var skipReason: String? = null
            private set

        /**
         * The one core the tests share (each scenario calls h_reset first). Null, with [skipReason]
         * set and printed once, when the toolchain is not here. A compile error is NOT a skip: it throws.
         */
        @Synchronized
        fun shared(): WasmSpriteCore? {
            if (triedShared) return shared
            triedShared = true
            shared = runCatching { build() }.getOrElse {
                if (it is IllegalStateException && it.message?.startsWith("SKIP") == true) {
                    skipReason = it.message
                    println("SpriteCoreWasmTest skipped: ${it.message}")
                    null
                } else throw it
            }
            shared?.let { core -> Runtime.getRuntime().addShutdownHook(Thread { core.close() }) }
            return shared
        }

        private fun build(): WasmSpriteCore {
            val bin = ndkBin() ?: error("SKIP no Android NDK (set sdk.dir in local.properties or ANDROID_HOME)")
            if (!nodeWorks()) error("SKIP no node on the PATH")
            val clang = exe(bin, "clang++")
            val lld = exe(bin, "ld.lld")
            if (!clang.isFile || !lld.isFile) error("SKIP the NDK at $bin has no clang++ or ld.lld")

            val header = File(cppDir, "sprite_core.h")
            val harness = File(nativeTestDir, "sprite_core_wasm.cpp")
            val runner = File(nativeTestDir, "sprite_core_runner.js")
            check(header.isFile && harness.isFile && runner.isFile) { "sprite core sources not found from ${File(".").canonicalPath}" }
            outDir.mkdirs()
            val wasm = File(outDir, "core.wasm")
            val key = File(outDir, "core.key")
            val want = sha(header, harness)
            if (!wasm.isFile || !key.isFile || key.readText() != want) {
                val obj = File(outDir, "core.o")
                val compile = ProcessBuilder(
                    clang.absolutePath, "--target=wasm32", "-std=c++17", "-O2", "-Wall", "-Wextra", "-Werror",
                    "-ffreestanding", "-fno-exceptions", "-fno-rtti", "-fno-builtin", "-nostdlib",
                    "-c", harness.absolutePath, "-o", obj.absolutePath,
                ).redirectErrorStream(true).start()
                val compileOut = compile.inputStream.bufferedReader().readText()
                check(compile.waitFor() == 0) { "sprite_core.h does not compile for wasm:\n$compileOut" }
                val link = ProcessBuilder(
                    lld.absolutePath, "-flavor", "wasm", "--no-entry", "--export-all", "-o", wasm.absolutePath, obj.absolutePath,
                ).redirectErrorStream(true).start()
                val linkOut = link.inputStream.bufferedReader().readText()
                check(link.waitFor() == 0) { "sprite core does not link for wasm:\n$linkOut" }
                key.writeText(want)
            }
            val p = ProcessBuilder("node", runner.absolutePath, wasm.absolutePath).redirectError(ProcessBuilder.Redirect.INHERIT).start()
            println("SpriteCoreWasmTest ran: sprite_core.h ${want.take(10)} built for wasm32 and driven under node")
            return WasmSpriteCore(p)
        }
    }
}
