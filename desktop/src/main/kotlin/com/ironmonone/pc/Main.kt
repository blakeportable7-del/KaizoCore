package com.ironmonone.pc

import java.io.File

/*
 * KaizoCore PC. Commands:
 *   (none) or serve [--console] [--build ROM] [-s SETTINGS] [--pool natdex|vanilla] [--out DIR] [--tracker FOLDER]
 *         [--attempts DIR] [--combo "A, B, Start"] [--session DIR] [--ahead N] [--keep-logs N] [--no-update-check]
 *       the Heart & Soul session for kaizocore-hns.lua in BizHawk: New Run with runs made ahead, the RAM to the tracker.
 *       Opens the status window; --console (or a machine with no screen) prints to the console instead.
 *   cli -s <settings.rnqs> -i <rom> -o <out.gba> [-l] [--pool natdex|vanilla] [--seed N]
 *       besteon's New Run command line ("java -jar X cli -s S -i R -o O -l") for Heart & Soul: -i is the official 2.0.6
 *       (KaizoCore's comfort patch is applied first) or the KaizoCore build. Prints "Randomized successfully!".
 *   setup [--dir DIR]
 *       finds the player's Heart & Soul in the folder, or makes the 2.0.6 from their Emerald and the team's patch there.
 *   twitch [out]
 *       connects Twitch chat for KaizoCore PC's commands (the device code shown here; "out" signs out).
 *   version
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "cli" -> cli(args.drop(1))
        "serve" -> serve(args.drop(1))
        "setup" -> setupCommand(args.drop(1))
        "twitch" -> twitchCommand(args.drop(1))
        "version", "--version" -> println(PcVersion.line)
        null -> serve(emptyList())
        else -> {
            println(PcVersion.line)
            println("usage: serve [--console] [--build ROM] [-s SETTINGS] [--pool natdex|vanilla] [--out DIR] [--tracker FOLDER]")
            println("       cli -s S -i ROM -o OUT [-l] [--pool natdex|vanilla] [--seed N]")
            println("       setup [--dir DIR]")
            println("       twitch [out]")
            println("       version")
        }
    }
}

fun opt(a: List<String>, k: String): String? = a.indexOf(k).takeIf { it >= 0 && it + 1 < a.size }?.let { a[it + 1] }

/** The folder this jar is in (the KaizoCore PC folder, where kaizocore-hns.lua sits too). */
fun jarDir(): File = runCatching {
    File(PcHns::class.java.protectionDomain.codeSource.location.toURI()).parentFile
}.getOrNull() ?: File(".").absoluteFile

fun cli(a: List<String>) {
    val t0 = System.nanoTime()
    val settings = PcHns.settingsFile(opt(a, "-s") ?: PcHns.DEFAULT_PRESET)
    val input = opt(a, "-i") ?: run { System.err.println("cli needs -i <rom>"); kotlin.system.exitProcess(2) }
    val out = File(opt(a, "-o") ?: run { System.err.println("cli needs -o <out.gba>"); kotlin.system.exitProcess(2) })
    val pool = PcHns.pool(opt(a, "--pool"), settings)
    val seed = opt(a, "--seed")?.toLong() ?: java.security.SecureRandom().nextLong()
    val build = try { PcHns.build(File(input).readBytes()) } catch (e: IllegalArgumentException) {
        System.err.println(e.message); kotlin.system.exitProcess(2)
    }
    val r = PcHns.randomize(build, PcHns.options(settings), seed, pool)
    out.writeBytes(r.rom)
    if ("-l" in a) File(out.path + ".log").writeText(r.logText)
    println("Pool %s, seed %d, output CRC %08X, %.1f s".format(pool, seed, PcHns.crc(r.rom), (System.nanoTime() - t0) / 1e9))
    println("Randomized successfully!")
}

fun setupCommand(a: List<String>) {
    val dir = opt(a, "--dir")?.let { File(it) } ?: jarDir()
    when (val f = PcSetup.find(dir, File(dir, ".kaizocore-setup"), ::println)) {
        is PcSetup.Found.Build -> println("Heart & Soul: ${f.file.name} (${f.start}). KaizoCore PC is ready.")
        is PcSetup.Found.Missing -> { println(f.why); kotlin.system.exitProcess(2) }
    }
}

/**
 * besteon's tracker settings, read only: the New Run combo ([controls] Load_next_seed) and the folders New Run writes
 * to ([overrides] ROMs_and_Logs and Attempt_Counts, else the tracker's own folder), as Main.GenerateNextRom.
 */
class BesteonSettings(val folder: File) {
    private val ini: Map<String, Map<String, String>> = run {
        val f = File(folder, "Settings.ini")
        val out = LinkedHashMap<String, MutableMap<String, String>>()
        var section = ""
        if (f.isFile) f.readLines().forEach { raw ->
            val l = raw.trim()
            when {
                l.startsWith("[") && l.endsWith("]") -> section = l.substring(1, l.length - 1)
                '=' in l && !l.startsWith(";") -> out.getOrPut(section) { LinkedHashMap() }[l.substringBefore('=').trim()] = l.substringAfter('=').trim()
            }
        }
        out
    }
    val combo: String? get() = ini["controls"]?.get("Load_next_seed")?.takeIf { it.isNotBlank() }
    private fun override(key: String) = ini["overrides"]?.get(key)?.takeIf { it.isNotBlank() }?.let { File(it) }
    val romsDir: File get() = override("ROMs_and_Logs") ?: folder
    val attemptsDir: File get() = override("Attempt_Counts") ?: folder
}

/**
 * kaizocore-pc.ini beside the jar, "key=value" lines (# for notes): build, settings, pool, tracker, out, attempts,
 * combo, ahead, keeplogs, updates. A command-line flag of the same name wins.
 */
fun iniSettings(dir: File = jarDir()): Map<String, String> {
    val f = File(dir, "kaizocore-pc.ini")
    if (!f.isFile) return emptyMap()
    return f.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
        .associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=').trim().trim('"') }
        .filterValues { it.isNotEmpty() }
}

/** Whether kaizocore-pc.ini leaves Twitch chat on (twitch=off turns it off). */
fun twitchOn(dir: File = jarDir()): Boolean = iniSettings(dir)["twitch"]?.lowercase() != "off"

/**
 * Twitch chat for a KaizoCore PC run (PLAN row 9): one per process, started with the window or the console, given the
 * session once it runs. Null when the .ini turns it off.
 */
@Volatile var pcChat: PcChat? = null

fun startChat(dir: File, session: () -> Session?, say: (String) -> Unit): PcChat? {
    if (!twitchOn(dir)) return null
    return PcChat(dir, session, PcVersion.version, say).also { pcChat = it; it.start() }
}

/** `twitch`: sign in with Twitch's device code, printed here; `twitch out`: sign out. */
fun twitchCommand(a: List<String>) {
    val chat = PcChat(jarDir(), { null }, PcVersion.version, ::println)
    if (a.firstOrNull() == "out") {
        chat.link.signOut()
        Thread.sleep(1500)
        println("Signed out of Twitch.")
        return
    }
    chat.link.signIn()
    var shown = ""
    val end = System.currentTimeMillis() + 15 * 60_000
    while (System.currentTimeMillis() < end) {
        val s = chat.link.status.value
        val line = chat.statusLine()
        if (line != shown) { println(line); shown = line }
        if (s.phase == com.ironmonone.app.stream.twitch.TwitchLink.Phase.LISTENING || s.phase == com.ironmonone.app.stream.twitch.TwitchLink.Phase.CONNECTING) {
            println("Signed in as ${s.name}. Start KaizoCore PC and chat is answered."); break
        }
        if (s.phase == com.ironmonone.app.stream.twitch.TwitchLink.Phase.SIGNED_OUT && s.note.isNotEmpty()) break
        Thread.sleep(500)
    }
    chat.shutdown()
}

/** A session ready to run, or why it cannot start (no game yet, a game it cannot use). */
sealed class Start {
    class Ready(val session: Session, val buildName: String) : Start()
    class Problem(val why: String) : Start()
}

/** Everything [serve] decides before the session runs: the build, the settings, the folders, the combo. */
fun prepare(a: List<String>, dir: File = jarDir(), events: SessionEvents = SessionEvents.NONE, say: (String) -> Unit = ::println, window: TrackerWindow? = null): Start {
    val ini = iniSettings(dir)
    fun get(flag: String, key: String) = opt(a, flag) ?: ini[key]
    val buildFile = get("--build", "build")?.let { File(it) } ?: when (val f = PcSetup.find(dir, File(dir, ".kaizocore-setup"), say)) {
        is PcSetup.Found.Build -> f.file
        is PcSetup.Found.Missing -> return Start.Problem(f.why)
    }
    if (!buildFile.isFile) return Start.Problem("The game set as build= in kaizocore-pc.ini is not there: ${buildFile.path}")
    val build = try { PcHns.build(buildFile.readBytes()) } catch (e: IllegalArgumentException) {
        return Start.Problem("${buildFile.name}: ${e.message}")
    }
    val settings = try { PcHns.settingsFile(get("-s", "settings") ?: PcHns.DEFAULT_PRESET) } catch (e: IllegalArgumentException) {
        return Start.Problem(e.message ?: "The settings file cannot be read.")
    }
    val options = try { PcHns.options(settings) } catch (e: Exception) {
        return Start.Problem(e.message ?: "The settings file cannot be read.")
    }
    val pool = PcHns.pool(get("--pool", "pool"), settings)
    val besteon = get("--tracker", "tracker")?.let { BesteonSettings(File(it)) }
    val out = get("--out", "out")?.let { File(it) } ?: besteon?.romsDir ?: File(dir, "runs")
    val attemptsDir = get("--attempts", "attempts")?.let { File(it) } ?: besteon?.attemptsDir ?: out
    val combo = get("--combo", "combo") ?: besteon?.combo ?: "Start, A, B"
    val session = opt(a, "--session")?.let { File(it) } ?: File(dir, "session")
    val ahead = get("--ahead", "ahead")?.toIntOrNull() ?: 2
    val keepLogs = get("--keep-logs", "keeplogs")?.toIntOrNull() ?: 50
    val demo = when (opt(a, "--panel-demo")) {
        "party" -> com.ironmonone.pcui.Samples.party
        "enemy" -> com.ironmonone.pcui.Samples.enemy
        "wild" -> com.ironmonone.pcui.Samples.wild
        "revealed" -> com.ironmonone.pcui.Samples.revealed
        "over" -> com.ironmonone.pcui.Samples.over
        "won" -> com.ironmonone.pcui.Samples.won
        "lab" -> com.ironmonone.pcui.Samples.lab
        else -> null
    }
    return Start.Ready(Session(build, settings, options, pool, out, attemptsDir, session, combo, ahead, keepLogs, events, demo, home = dir, window = window), buildFile.name)
}

fun serve(a: List<String>) {
    val console = "--console" in a || java.awt.GraphicsEnvironment.isHeadless()
    val checkUpdates = "--no-update-check" !in a && iniSettings()["updates"]?.lowercase() != "off"
    if (!console) {
        // The card in its own window is drawn for a screen, not stretched by BizHawk: smooth text (read once, before
        // the first card is drawn). --card-in-bizhawk keeps the card inside BizHawk as before.
        if ("--card-in-bizhawk" !in a) System.setProperty("pcui.smooth", "1")
        StatusWindow.open(a, checkUpdates)
        return
    }
    println(PcVersion.line)
    if (checkUpdates) Thread({ Updates.check()?.let { println(it.message) } }, "kc-updates").apply { isDaemon = true }.start()
    when (val s = prepare(a)) {
        is Start.Problem -> { println(s.why); kotlin.system.exitProcess(2) }
        is Start.Ready -> {
            val chat = startChat(jarDir(), { s.session }, ::println)
            if (chat != null) {
                // The console hears each change of the chat's state.
                Thread({ var last = ""; while (true) { val l = chat.statusLine(); if (l != last) { println(l); last = l }; Thread.sleep(500) } }, "kc-chat-status").apply { isDaemon = true }.start()
            }
            if (!s.session.run()) kotlin.system.exitProcess(3)
            chat?.shutdown()
        }
    }
}
