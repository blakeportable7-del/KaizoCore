package com.ironmonone.pc

import com.ironmonone.app.stream.twitch.TwitchTokenStore
import com.ironmonone.app.stream.twitch.TwitchTokens
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * The Twitch sign-in on the PC (PLAN row 9), the phone's KeystoreTokenStore here: the tokens sealed by Windows' own
 * Data Protection API for the signed-in Windows user (ProtectedData, CurrentUser scope), in one file beside the jar.
 * Another user, another PC or a copy of the file cannot open it; [load] then finds that and asks for a new sign-in.
 * DPAPI is reached through Windows PowerShell's ProtectedData class, with the bytes on its standard input and output
 * (never on a command line), so the jar needs no native library.
 */
class DpapiTokenStore(private val file: File) : TwitchTokenStore {
    companion object {
        const val FILE = "twitch-session.bin"
    }

    @Synchronized
    override fun load(): TwitchTokens? {
        val sealed = runCatching { file.takeIf { it.isFile }?.readBytes() }.getOrNull() ?: return null
        val plain = Dpapi.unprotect(sealed)
        val t = plain?.let { runCatching { TwitchTokens.decode(String(it, Charsets.UTF_8)) }.getOrNull() }
        // Not ours to open, or not a sign-in: it goes, and the sign-in is asked for.
        if (t == null) file.delete()
        return t
    }

    @Synchronized
    override fun save(t: TwitchTokens) {
        val sealed = Dpapi.protect(t.encode().toByteArray(Charsets.UTF_8)) ?: throw IOException("could not save the Twitch sign-in: Windows did not seal it")
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(sealed)
        if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) throw IOException("could not save the Twitch sign-in") }
    }

    @Synchronized
    override fun clear() { file.delete() }
}

/** Windows' ProtectedData for the current user, through Windows PowerShell. Null where it is not there (not Windows, no PowerShell). */
object Dpapi {
    val available: Boolean get() = System.getProperty("os.name", "").startsWith("Windows") && powershell().isFile

    private fun powershell(): File = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\WindowsPowerShell\\v1.0\\powershell.exe")

    fun protect(bytes: ByteArray): ByteArray? = run("Protect", bytes)
    fun unprotect(bytes: ByteArray): ByteArray? = run("Unprotect", bytes)

    private fun run(op: String, bytes: ByteArray): ByteArray? {
        if (!available) return null
        val script = "Add-Type -AssemblyName System.Security; " +
            "\$in = [Console]::In.ReadToEnd().Trim(); \$b = [Convert]::FromBase64String(\$in); " +
            "\$o = [System.Security.Cryptography.ProtectedData]::$op(\$b, \$null, 'CurrentUser'); " +
            "[Console]::Out.Write([Convert]::ToBase64String(\$o))"
        return runCatching {
            val p = ProcessBuilder(powershell().path, "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-Command", script)
                .redirectErrorStream(false).start()
            p.outputStream.use { it.write(Base64.getEncoder().encode(bytes)); it.write('\n'.code) }
            val out = p.inputStream.readBytes()
            p.errorStream.readBytes()
            if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroyForcibly(); return null }
            if (p.exitValue() != 0) return null
            Base64.getDecoder().decode(String(out, Charsets.US_ASCII).trim())
        }.getOrNull()
    }
}
