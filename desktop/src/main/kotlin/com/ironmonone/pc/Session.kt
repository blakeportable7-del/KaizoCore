package com.ironmonone.pc

import com.ironmonone.app.engine.HnsEngine
import com.ironmonone.app.engine.hns.HnsOptions
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.TrackerState
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The JAR's side of a Heart & Soul session with BizHawk (kaizocore-hns.lua). Everything goes through plain files in
 * [dir], so BizHawk needs no launch flags:
 *
 * - `jar.txt`   JAR -> Lua, rewritten every 250 ms: alive (epoch seconds), the New Run combo, the poll interval the
 *               tracker wants, how many runs are ready, the attempt count.
 * - `lua.req`   Lua -> JAR, one request at a time (hello, newrun, bye), written to a temp name and renamed.
 * - `jar.resp`  JAR -> Lua, the answer, carrying the request's seq.
 * - `ram.bin`   Lua -> JAR, "KCRAM <gen>\n" then EWRAM (256 KB) and IWRAM (32 KB) as raw bytes. Lua writes one only
 *               when the last one was taken, so nothing piles up.
 * - `tracker.txt` JAR's reading of the party, for checks.
 * - `panel.txt` / `panel-N.png` JAR -> Lua, the panel picture (PanelOut); `input.req` Lua -> JAR, a click or a note.
 * - `jar.cmd`   JAR -> Lua, one thing for BizHawk to do (retry: load the state the battle began from; savestate: save
 *               one to a path), with a seq; the script deletes it once done.
 * - `lua.txt`   Lua -> JAR, rewritten when it changes: whether the script holds a battle-start state (battlestate).
 * - `overlay.txt` / `overlay-N.png` JAR -> Lua, the log viewer over the game area while it is open (OverlayOut);
 *               `input.req` carries its clicks (oclick), the wheel (wheel) and the search box's text (search).
 * - favorites (PLAN row 8): jar.cmd `favorites` opens the editor in BizHawk (a name a line, and one list of names);
 *               `lua.req` `suggest` asks what the list offers for what is typed; `input.req` `favorites` saves them.
 *               One standing list for every run (Blake's hand test, 2026-10-07): [favoritesFile] in [home], never a
 *               seed folder; it changes only by the editor (the start screen's box, the window's Favorites) or `!fav`.
 * - `jar.cmd` `newrun` (the window's New Run): the script opens the next run as the combo does, also with no game open.
 * - `lua.txt` also carries `alive` (the script's clock, about once a second) and `game` (none, other or hns), so the
 *   window knows BizHawk's script is running before any Heart & Soul game is.
 *
 * What is the run's own and what is not (Blake, 2026-10-07: "other things are not run data"): a seed's folder holds
 * only the run (its game and log, run.txt, the marks, notes and encounters, its play time, its Pokemon's GachaMon
 * stars when they joined, a saved attempt). The
 * favorites, the gear's options and the theme are one copy each in [home], the KaizoCore PC folder, for every run and
 * every preset; the Twitch sign-in and chat settings were already there (PcChat). The attempt count stays besteon's,
 * one file per ROM name ("<prefix> Attempts.txt"); the PC heal counts are kept per attempt ("<prefix> pc heals.txt").
 *
 * New Run follows besteon's Ironmon-Tracker naming (Main.GenerateNextRom, Main.SaveCurrentRom): the run is
 * "<prefix> AutoRandomized.gba" with its ".log", the one before it "<prefix> PreviousAttempt.gba", the count in
 * "<prefix> Attempts.txt". The prefix is "HnS " + the settings name, so these never collide with an Emerald Nat. Dex
 * run's files made from a .rnqs of the same name. The next runs are made ahead in [stageDir], as the phone's
 * NextRunJob does, so the combo only renames files.
 */
class Session(
    private val build: ByteArray,
    private val settings: File,
    private val options: HnsOptions,
    private val pool: HnsEngine.Pool,
    private val outDir: File,
    private val attemptsDir: File,
    val dir: File,
    private val combo: String,
    private val ahead: Int,
    private val keepLogs: Int = 50,
    /** What the status window shows; the console version leaves it empty. */
    private val events: SessionEvents = SessionEvents.NONE,
    /**
     * For the BizHawk checks only (serve --panel-demo party|enemy): the panel draws this staged state instead of what
     * the game's RAM says, and nothing is recorded into the run's folder from it.
     */
    private val panelDemo: TrackerState? = null,
    /** The KaizoCore PC folder (the jar's): the standing files every run shares (favorites, options, theme). */
    private val home: File = dir.absoluteFile.parentFile ?: dir,
    /** The card's own window (KaizoCore PC's window opens it); null draws the card inside BizHawk (the console version). */
    private val window: TrackerWindow? = null,
) {
    private val prefix = "HnS " + settings.nameWithoutExtension
    private val auto = File(outDir, "$prefix AutoRandomized.gba")
    private val prev = File(outDir, "$prefix PreviousAttempt.gba")
    private val attemptsFile = File(attemptsDir, "$prefix Attempts.txt")
    private val stageDir = File(outDir, ".kaizocore-next")

    private val log = File(dir, "jar.log")
    private val started = System.currentTimeMillis()
    @Volatile private var stopping = false

    fun say(s: String) {
        val line = "%tT.%<tL %s".format(System.currentTimeMillis(), s)
        println(line)
        runCatching { log.appendText(line + "\n") }
        events.log(line)
    }

    /** When BizHawk's script last sent anything (a request or the RAM): the window says whether it is connected. */
    @Volatile var lastContact = 0L
        private set

    /** The name of the game the tracker follows, for the window; null when none. */
    val tracking: String? get() = loaded?.let { File(it.path).name }

    /** How many runs are made ahead and ready for New Run. */
    val readyRuns: Int get() = synchronized(lock) { ready.size }

    /** The attempt count, as the attempts file has it. */
    val attempt: Int get() = attempts

    /** Writes the count for the tracker and a one-line file beside the program for a stream text source. */
    private fun writeAttempts() {
        runCatching { attemptsDir.mkdirs(); attemptsFile.writeText(attempts.toString()) }
        runCatching { File(home, "attempt.txt").writeText(attempts.toString()) }
    }

    /** The window's Reset button. The next New Run is attempt 1. Does not delete old run folders. */
    fun resetAttempts() {
        post {
            attempts = 0
            writeAttempts()
            events.changed()
            say("attempts reset to 0")
        }
    }

    /** Why the next run cannot be made, or null. */
    val makeError: String? get() = genError

    /** Asks the loop to stop (the window's Quit); [run] returns once it has cleaned up. */
    fun stop() {
        stopping = true
        synchronized(lock) { lock.notifyAll() }
    }

    // ---- runs made ahead -------------------------------------------------------------------------------------------

    /** A run made ahead: its files, and its bytes and hashes so the combo costs no read and no hashing. */
    private class Staged(val seed: Long, val rom: File, val log: File, val ms: Long, val bytes: ByteArray, val hashes: Set<String>)

    private val lock = Object()
    private val ready = ArrayDeque<Staged>()
    @Volatile private var genError: String? = null
    private val rng = SecureRandom()
    @Volatile private var lastTake = 0L
    private val SETTLE_MS = 1500L

    private fun make(): Staged {
        val t0 = System.nanoTime()
        val seed = rng.nextLong()
        val r = PcHns.randomize(build, options, seed, pool)
        stageDir.mkdirs()
        val rom = File(stageDir, "$seed.gba")
        val logF = File(stageDir, "$seed.gba.log")
        writeAtomic(rom, r.rom)
        writeAtomic(logF, r.logText.toByteArray(Charsets.UTF_8))
        val hashes = hashesOf(r.rom)
        val ms = (System.nanoTime() - t0) / 1_000_000
        return Staged(seed, rom, logF, ms, r.rom, hashes)
    }

    private val maker = Thread({
        Thread.currentThread().priority = Thread.MIN_PRIORITY
        while (!stopping) {
            synchronized(lock) { while (!stopping && ready.size >= ahead) lock.wait() }
            // BizHawk opens a 32 MB game right after a New Run: let it have the CPU first (the phone's NextRunJob
            // settles too). One run is still ready, so only a third press inside this pause waits for a run.
            while (!stopping && System.currentTimeMillis() < lastTake + SETTLE_MS) Thread.sleep(50)
            if (stopping) break
            try {
                val s = make()
                synchronized(lock) { ready.addLast(s); genError = null; lock.notifyAll() }
                say("made a run ahead: seed ${s.seed} in ${s.ms} ms (${ready.size} ready)")
                events.changed()
            } catch (t: Throwable) {
                genError = t.message ?: t.toString()
                say("could not make a run: $genError")
                events.changed()
                synchronized(lock) { lock.notifyAll(); lock.wait(5000) }
            }
        }
    }, "kc-next-run").apply { isDaemon = true }

    /** The next run made ahead; waits for the one being made when none is ready. */
    private fun take(timeoutMs: Long): Staged? {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (ready.isEmpty()) {
                val left = end - System.currentTimeMillis()
                // A maker that failed will fail again with the same build and settings: say why at once.
                if (left <= 0 || genError != null) return null
                lock.wait(minOf(left, 100))
            }
            lastTake = System.currentTimeMillis()
            return ready.removeFirst().also { lock.notifyAll() }
        }
    }

    // ---- the run BizHawk has loaded, and its tracker ---------------------------------------------------------------

    class Loaded(val gen: Int, val rom: ByteArray, val hashes: Set<String>, val path: String) {
        // GameMap.resolve and the tracker's set-up read the ROM: about 200 ms, done after New Run has answered.
        val reader = BridgeReader(rom)
        val map: GameMap = GameMap.resolve(reader)
        val tracker = GbaTracker(reader, map)
        /** The ROM's accuracy data and held items, for the card's real hit chance (PcReal); one instance, so a card compares equal. */
        val hitMove: (Int) -> com.ironmonone.tracker.HitChance.Move? = { id -> runCatching { com.ironmonone.tracker.HitChance.move(reader, id) }.getOrNull() }
        val hitHold: (Int) -> Pair<com.ironmonone.tracker.HitChance.Hold, Int> = { id ->
            runCatching { com.ironmonone.tracker.HitChance.item(reader, id) }.getOrDefault(com.ironmonone.tracker.HitChance.Hold.UNKNOWN to 0)
        }
        var reads = 0
        var lastParty = ""
        /** The run's own data (marks, notes, encounters), in its folder; null for a game with no run folder. */
        var run: RunData? = null
        /** The run's attempt number, from its folder's run.txt. */
        var attempt: Int = 0
        var state: TrackerState? = null
        /** The run's ending, kept up until Continue playing or Retry (PLAN row 6). */
        val latch = OverLatch()
        /** The run's log as the viewer reads it, once Inspect the log asked for it (PLAN row 7). */
        var logData: com.ironmonone.pcui.PcLogData? = null
        var logLoaded = false
        /** besteon's PokeBalls.chosenBall (0 left, 1 middle, 2 right): a coin toss per game, the die rolls again. */
        var ballPick = SecureRandom().nextInt(3)
        /** The three starter balls, read from the game once (GbaTracker.starters). */
        val starters: List<GbaTracker.BallOption> by lazy { runCatching { tracker.starters() }.getOrDefault(emptyList()) }
        /** GachaMon: each Pokemon's stars when it joined the run (the run's "gachamon stars.txt"), by its card key. */
        val gachaJoined = HashMap<Long, Int>()
        var gachaCache: Pair<List<Any>, Int>? = null
        /**
         * Battle.isViewingOwn: in battle, your Pokemon rather than the opponent. Set when a battle starts from "Auto swap
         * to enemy" (on: the opponent), flipped by the script's "Toggle view" button (Start, as besteon's).
         */
        var viewOwn = false
        var wasInBattle = false
        /** The bottom strip's rotation (TrackerScreen.getCurrentCarouselItem), turned on the session's clock. */
        val carousel = com.ironmonone.pcui.PcCarousel()
        /** The panel's lookups, made once so the panel's input compares equal while nothing changed. */
        val moveLevels: (Int) -> List<Int> = { sp -> tracker.learnset(sp).map { it.first } }
        val moveRowFor: (Int) -> com.ironmonone.tracker.MoveRow? = { id -> tracker.moveRowFor(id) }
        val noteFor: (Int) -> String = { sp -> run?.note(sp).orEmpty() }
        /** The last RAM ranges, and whether the next identical copy must still be decoded (a map or a battle opens on the second read). */
        var ramBody: ByteArray? = null
        var ramAgain = false
        /** Program.GameTimer: seconds of this run played while KaizoCore PC read the game (kept in the run's playtime.txt). */
        var playMillis = 0L
        var lastReadAt = 0L
        var playSavedAt = 0L
        /** Grade my notes, while its sheet is up; the prize card made for this run's ending. */
        var scoreSheet: com.ironmonone.app.ScoreSheet.Result? = null
        var prize: com.ironmonone.pcui.PcGachaMon? = null
        var prizeMade = false
    }

    /** The panel beside the game (PLAN row 4); made in [run], used on the session's thread only. */
    private val panel = PanelOut(dir, ::say).also { p ->
        // Followers are the build's own pictures, the same in every run, so the start screen has them before a game loads.
        val reader by lazy { BridgeReader(build) }
        val made = HashMap<Int, com.ironmonone.tracker.HnsPics.Follower?>()
        p.follower = { sp -> synchronized(made) { made.getOrPut(sp) { com.ironmonone.tracker.HnsPics.follower(reader, sp) } } }
    }

    /** Play as your Pokemon (Sprite Is Me): what the script draws over the player, written when it changes. */
    private val playAs = PlayAsOut(dir, home, ::say)

    /** The lead to walk as (SpriteLead.lead: the first Pokemon not an egg, its form and shiny as the game draws it). */
    private fun updatePlayAs(l: Loaded) {
        val st = l.state
        val mon = st?.party?.firstOrNull { !it.mon.isEgg && it.mon.species > 0 }?.mon
        val lead = mon?.let { runCatching { com.ironmonone.app.SpriteLead.lead(it, com.ironmonone.app.WalkingPals.Dex.HNS, l.map) }.getOrNull() }
        playAs.update(lead, com.ironmonone.tracker.Overworld.HEARTSOUL_KAIZO)
    }

    /** The log viewer over the game area (PLAN row 7), open only from the game over card. */
    private val overlay = OverlayOut(dir, ::say)

    /** This settings file's IronMON mode, for the ban marks. */
    private fun rulesMode(): String {
        val n = settings.nameWithoutExtension.lowercase()
        return when {
            "kaizo doubles" in n || "kaizodoubles" in n -> "kaizodoubles"
            "super" in n && "kaizo" in n -> "superkaizo"
            "chaos" in n -> "chaoskaizo"
            "evo" in n && "kaizo" in n -> "evokaizo"
            "survival revival" in n -> "survivalrevival"
            "journey" in n -> "ironmonjourney"
            "ultimate" in n -> "ultimate"
            "survival" in n -> "survival"
            "standard" in n -> "standard"
            else -> "kaizo"
        }
    }

    /** The info card while a starter ball is open ("Do you choose this POKEMON?"). */
    private fun starterFacts(l: Loaded, st: TrackerState?): com.ironmonone.pcui.PcPanel.StarterFacts? {
        val id = st?.starterOffered?.takeIf { st.partyCount == 0 } ?: return null
        return infoFacts(l, id, st.starterBase)
    }

    /** InfoScreen POKEMON_INFO's lines for [id]: the lab's card, and the card a click opens for any Pokemon. */
    private fun infoFacts(l: Loaded, id: Int, known: com.ironmonone.tracker.BaseStats? = null): com.ironmonone.pcui.PcPanel.StarterFacts? {
        val base = known ?: l.tracker.baseStats(id) ?: return null
        val types = listOf(base.type1, base.type2).distinct().map { com.ironmonone.tracker.Gen3Types.name(it) }
        val weak = l.tracker.effectivenessAgainst(id).filterKeys { it > 1.0 }.entries
            .sortedByDescending { it.key }.flatMap { (m, names) -> names.map { it to m } }
        val evo = com.ironmonone.tracker.EvoText.detailed(l.tracker.evolution(id), l.tracker.friendshipRequired()).firstOrNull() ?: "-"
        return com.ironmonone.pcui.PcPanel.StarterFacts(
            id, l.tracker.speciesName(id), types, base.bst.toString(),
            (l.tracker.expYield(id) ?: 0).toString(), l.tracker.weight(id)?.let { "$it kg" } ?: "---",
            evo, l.moveLevels(id), weak, l.run?.note(id).orEmpty(),
        )
    }

    /** Draws the panel for what [l] shows now. */
    private fun showPanel(l: Loaded, force: Boolean = false) {
        val st = l.state
        val run = l.run
        val inBattle = st?.inBattle == true
        if (inBattle && !l.wasInBattle) l.viewOwn = !com.ironmonone.app.TrackerOptions.autoSwapToEnemy(false)
        l.wasInBattle = inBattle
        val shown = when {
            st == null -> 0
            st.inBattle && st.enemy != null && !l.viewOwn -> st.enemy!!.species
            else -> (st.lead ?: st.party.firstOrNull())?.mon?.species ?: 0
        }
        // Auto Pokemon Themes: the colours of the Pokemon on the card (Blake, 2026-10-11: "my theme is locked in blue, it
        // doesn't change when i switch pokemon and doesn't change on the ememy card"). Your active Pokemon on your card, the
        // foe on its card; it was party slot 1 on every card. Off, or a Pokemon with none, gives the chosen theme back.
        val themeWas = com.ironmonone.app.AutoTheme.current
        com.ironmonone.app.AutoTheme.heartSoul = true
        val ownEgg = st?.let { (it.lead ?: it.party.firstOrNull())?.mon?.isEgg } == true
        val onCard = if (shown > 0) listOf(shown to (shown != st?.enemy?.species && ownEgg)) else emptyList()
        com.ironmonone.app.AutoTheme.onGba(onCard, com.ironmonone.app.TrackerOptions.autoPokemonThemes)
        val repaint = force || com.ironmonone.app.AutoTheme.current != themeWas
        val note = if (shown > 0) run?.note(shown).orEmpty() else ""
        // GachaMonStars: your Pokemon on view, its card now against the card it joined with.
        val own = st?.let { it.lead ?: it.party.firstOrNull() }
        val gacha = own?.let { p -> l.gachaJoined[com.ironmonone.pcui.PcGachaStars.key(p)]?.let { made -> gachaNow(l, p)?.let { now -> now to made } } }
            ?.takeIf { maxOf(it.first, it.second) >= 1 }
        val over = l.latch.ending?.let { e ->
            com.ironmonone.pcui.PcOver(
                won = e.won, team = e.team, teamIndex = e.teamIndex, quote = e.quote,
                canRetry = luaState["battlestate"] == "1" && !e.won, retryConfirm = e.retryConfirm, save = e.save,
                logAvailable = logFile(l) != null,
                playTime = (l.playMillis / 1000).takeIf { it > 0 }?.let { sec ->
                    val h = sec / 3600; val m = sec / 60 % 60; val x = sec % 60
                    if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%02d:%02d".format(m, x)
                },
                prizeOffered = prizeOffered(l),
            )
        }
        panel.show(
            com.ironmonone.pcui.PcPanel.Input(st, l.attempt, run?.marks, routeName = st?.routeName, title = l.map.name, version = run?.version ?: 0, note = note, over = over,
                favorites = com.ironmonone.pcui.PcFavorites.icons(favorites, natDex), showOwn = l.viewOwn, ballPicker = ballPicker(l, st), gachaStars = gacha,
                moveLevels = l.moveLevels, moveRowFor = l.moveRowFor,
                log = overlay.shared.takeIf { overlay.isOpen && overlay.shared.data != null }, noteFor = l.noteFor,
                scoreSheet = l.scoreSheet.takeIf { l.latch.ending != null }, strip = st?.let { strip(l, it) },
                rulesMode = rulesMode(), starter = starterFacts(l, st), battle = battleFacts(l, st)),
            note = note,
            force = repaint,
            battle = st?.inBattle == true,
        )
    }

    /**
     * The bottom strip's item now: what the phone's Play screen hands its PcCarousel (TrackerPanel's call), from this
     * read and the run's own records. The route line counts the Pokemon seen in the route's area (the run's marks) against
     * the area's total in the game's own tables: Walking out of battle, the battle's area in a wild one.
     */
    private fun strip(l: Loaded, st: TrackerState): com.ironmonone.pcui.PcStrip {
        val viewingOwn = !st.inBattle || st.enemy == null || l.viewOwn
        val m = st.mapId
        val areas = if (m != null) runCatching { l.tracker.routeEncounters(m) }.getOrDefault(emptyMap()) else emptyMap()
        val battleArea = st.encounterArea?.takeIf { st.isWildBattle && it in areas }
        val area = battleArea ?: "Walking".takeIf { it in areas }
        val seen = if (m != null && area != null) l.run?.marks?.seenOnRouteArea(m, area)?.size ?: 0 else 0
        val total = if (area != null) areas[area].orEmpty().size else 0
        val ownOnField = st.lead?.mon?.curHp
        val facts = com.ironmonone.pcui.PcCarousel.Facts(
            inBattle = st.inBattle, viewingOwn = viewingOwn, isWild = st.isWildBattle, leadLevel = st.lead?.mon?.level ?: 0,
            routeArea = area, routeSeen = seen, routeTotal = total,
            routeTrainers = st.routeTrainers.size, trainersDefeated = st.routeTrainersDefeated, trainersTotal = st.routeTrainersTotal,
            lastAttack = st.lastAttackMove?.takeIf { com.ironmonone.app.TrackerOptions.showLastDamage }?.let { mv ->
                com.ironmonone.tracker.LastAttack.text(mv, st.lastAttackDamage, st.lastAttackTeams) },
            lethal = com.ironmonone.tracker.LastAttack.lethal(st.lastAttackDamage, ownOnField),
            battleDetail = com.ironmonone.pcui.PcBattleLine.line(st.battleSummaries, viewingOwn),
            totalSteps = st.steps, pedometerAllowed = st.mapId != null && st.gameOver == null,
        )
        return l.carousel.pick(System.currentTimeMillis(), facts)
    }

    /** "Always use"'s names (SpriteIsMeLogic.choices): every Pokemon with a walking sprite, ranked as the favorites are. */
    private fun alwaysSuggest(typed: String): List<String> {
        val index = com.ironmonone.app.WalkingPals.index(com.ironmonone.app.pcDesktopCtx)
        return com.ironmonone.app.Favorites.suggestIds(typed, limit = 20)
            .filter { (id, _) -> index.find(id, com.ironmonone.app.WalkingPals.Dex.NAT_DEX) != null }.take(5).map { it.second }
    }

    /**
     * A gear row that needs KaizoCore PC: a file chooser (it opens in front, beside BizHawk; the session keeps running
     * while it is open) or the panel's keyboard. The files chosen are copied into the KaizoCore PC folder.
     */
    private fun settingsAction(a: com.ironmonone.pcui.PcSettingsScreen.Action) {
        val ui = panel.settings
        when (a) {
            com.ironmonone.pcui.PcSettingsScreen.Action.PLAY_AS_ALWAYS -> {
                panel.typing.openAlways(ui.alwaysName)
                return
            }
            com.ironmonone.pcui.PcSettingsScreen.Action.ADD_PICTURES -> choose("Choose pictures named after their Pokemon (Gengar.png)", multi = true) { files ->
                val (n, skipped) = com.ironmonone.pcui.PcPictures.add(files)
                if (n > 0) { com.ironmonone.pcui.PcPictures.own = true; com.ironmonone.pcui.PcPictures.save() }
                ui.message = "$n added" + if (skipped.isNotEmpty()) ", not a Pokemon: " + skipped.joinToString(", ") else ""
                say("pictures: ${ui.message}")
            }
            com.ironmonone.pcui.PcSettingsScreen.Action.PLAY_AS_PICTURE -> choose("Choose a picture to walk as", multi = false) { files ->
                val f = files.firstOrNull() ?: return@choose
                if (javax.imageio.ImageIO.read(f) == null) { ui.message = "${f.name} is not a picture"; return@choose }
                playAs.ownDir.mkdirs()
                File(playAs.ownDir, "sheet").deleteRecursively()
                f.copyTo(playAs.pictureFile, overwrite = true)
                com.ironmonone.app.SpriteIsMeSettings.own = com.ironmonone.app.SpriteIsMeSettings.Own.PICTURE
                com.ironmonone.app.SpriteIsMeSettings.who = com.ironmonone.app.SpriteIsMeSettings.Who.OWN
                com.ironmonone.app.SpriteIsMeSettings.save()
                ui.message = "walking as ${f.name}"
            }
            com.ironmonone.pcui.PcSettingsScreen.Action.PLAY_AS_REMOVE -> {
                playAs.pictureFile.delete()
                File(playAs.ownDir, "sheet").deleteRecursively()
                com.ironmonone.app.SpriteIsMeSettings.own = com.ironmonone.app.SpriteIsMeSettings.Own.NONE
                if (com.ironmonone.app.SpriteIsMeSettings.who == com.ironmonone.app.SpriteIsMeSettings.Who.OWN)
                    com.ironmonone.app.SpriteIsMeSettings.who = com.ironmonone.app.SpriteIsMeSettings.Who.LEAD
                com.ironmonone.app.SpriteIsMeSettings.artVersion++
                com.ironmonone.app.SpriteIsMeSettings.save()
                ui.message = "sprite removed"
                say("play as: sprite removed")
            }
            com.ironmonone.pcui.PcSettingsScreen.Action.PLAY_AS_SHEETS -> choose("Choose the idle, walk, sleep and faint sheets", multi = true) { files ->
                val picked = files.mapNotNull { f -> com.ironmonone.app.SheetSet.classify(f.name)?.let { it to f } }.toMap()
                if (picked.isEmpty()) { ui.message = "name them idle, walk, sleep, faint"; return@choose }
                playAs.pictureFile.delete()
                File(playAs.ownDir, "sheet").apply { deleteRecursively(); mkdirs() }
                for ((anim, f) in picked) f.copyTo(playAs.sheetFile(anim), overwrite = true)
                com.ironmonone.app.SpriteIsMeSettings.own = com.ironmonone.app.SpriteIsMeSettings.Own.SHEET
                com.ironmonone.app.SpriteIsMeSettings.who = com.ironmonone.app.SpriteIsMeSettings.Who.OWN
                com.ironmonone.app.SpriteIsMeSettings.save()
                ui.message = "sheets: " + picked.keys.joinToString(", ") { it.key }
            }
        }
    }

    /** A Swing file chooser on its own thread; [done] runs on the session's thread with what was chosen. */
    private fun choose(title: String, multi: Boolean, done: (List<File>) -> Unit) {
        javax.swing.SwingUtilities.invokeLater {
            val fc = javax.swing.JFileChooser().apply {
                dialogTitle = title
                isMultiSelectionEnabled = multi
                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("PNG pictures", "png")
            }
            val frame = javax.swing.JFrame().apply { isAlwaysOnTop = true; isUndecorated = true; setLocationRelativeTo(null); isVisible = true }
            val ok = fc.showOpenDialog(frame) == javax.swing.JFileChooser.APPROVE_OPTION
            frame.dispose()
            if (!ok) return@invokeLater
            val files = if (multi) fc.selectedFiles.toList() else listOfNotNull(fc.selectedFile)
            post {
                runCatching { done(files) }.onFailure { say("files: $it"); panel.settings.message = "could not copy: ${it.message}" }
                loaded?.let { updatePlayAs(it); showPanel(it, force = true) }
            }
        }
    }

    // ---- favorites (PLAN row 8): one standing list ---------------------------------------------------------------

    /** Whether the run counts the Nat. Dex rules' nine favorites, or a Vanilla pool's three (HnsPool.favoritesScope). */
    private val natDex: Boolean get() = pool == HnsEngine.Pool.NATDEX

    /** The favorites, besteon's Settings.ini "Startup favorites" here: nine names, one file for every run. */
    private val favoritesFile = File(home, FAVORITES)

    /** The favorites as the file has them; read in [run] (after [migrate]), changed only by [setFavorites]. */
    @Volatile private var favorites: List<String> = List(com.ironmonone.app.Favorites.NAT_DEX_SLOTS) { "" }

    private fun readFavorites(): List<String> = com.ironmonone.pcui.PcFavorites.slots(runCatching { favoritesFile.readText() }.getOrDefault(""))

    /** The favorites as they are now, for the window's editor. */
    fun favoritesNow(): List<String> = favorites

    /** Whether this session's runs count nine favorites or three, for the window's editor. */
    val favoritesShown: Int get() = com.ironmonone.pcui.PcFavorites.scope(natDex).shown

    /** `!favorites`: each box this run counts, numbered, the empty ones said so. Any thread. */
    fun favoritesLine(): String =
        "Favorites > " + (0 until favoritesShown).joinToString(", ") { "#${it + 1} ${favorites.getOrElse(it) { "" }.ifBlank { "(none)" }}" }

    /** What the drop-down offers for [typed] (Favorites.suggest), for the window's editor. Any thread. */
    fun suggestFavorites(typed: String): List<String> = com.ironmonone.pcui.PcFavorites.suggest(typed, natDex)

    /** Whether [typed] settles to a Pokemon of this run's pool, as Save settles it. Any thread. */
    fun isFavoriteName(typed: String): Boolean = com.ironmonone.pcui.PcFavorites.resolve(typed, natDex) != null

    /** Saves [slots] as the favorites (the one file) and draws the start screen again. On the session's thread. */
    private fun setFavorites(slots: List<String>, by: String) {
        val nine = com.ironmonone.pcui.PcFavorites.slots(com.ironmonone.pcui.PcFavorites.text(slots)).map { it.trim().take(40) }
        logFavorites(favorites, nine, by)
        favorites = nine
        runCatching { home.mkdirs(); favoritesFile.writeText(com.ironmonone.pcui.PcFavorites.text(nine)) }.onFailure { say("favorites: $it") }
        say("favorites: " + nine.filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "(none)" })
        loaded?.let { showPanel(it, force = true) }
        events.changed()
    }

    /**
     * The editor's boxes (BizHawk's window or KaizoCore PC's), each settled as the drop-down would settle it. A box
     * that names no Pokemon keeps what it held, so a typo never lands in the file. The names not taken. Any thread.
     */
    fun saveFavorites(typed: List<String>): List<String> {
        val refused = ArrayList<String>()
        val boxes = com.ironmonone.pcui.PcFavorites.slots(com.ironmonone.pcui.PcFavorites.text(typed))
        post {
            val before = favorites
            setFavorites(boxes.mapIndexed { i, t ->
                if (t.isBlank()) "" else com.ironmonone.pcui.PcFavorites.resolve(t, natDex) ?: before.getOrElse(i) { "" }
            }, "the editor")
        }
        boxes.filter { it.isNotBlank() && com.ironmonone.pcui.PcFavorites.resolve(it, natDex) == null }.forEach { refused += it.trim() }
        if (refused.isNotEmpty()) say("favorites: not a Pokemon in this run, kept the box as it was: " + refused.joinToString(", "))
        return refused
    }

    /**
     * `!fav <slot> <name>`: the box [slot] set to [typed] if it names one Pokemon (PcFavorites.strict), with or
     * without a run loaded; [by] is who asked, for the history. The line answered in chat.
     */
    fun setFavorite(slot: Int, typed: String, by: String = ""): String {
        val shown = com.ironmonone.pcui.PcFavorites.scope(natDex).shown
        if (slot !in 1..shown) return "The slot must be 1 to $shown."
        val m = com.ironmonone.pcui.PcFavorites.strict(typed, natDex)
        val name = m.name ?: return if (m.offers.isEmpty()) "No Pokemon called \"${typed.take(40)}\" in this run."
            else "Which one? ${m.offers.joinToString(", ")}. Say !fav $slot and the full name."
        val was = favorites.getOrElse(slot - 1) { "" }.ifBlank { "(none)" }
        // The file and the panel on the session's thread: chat asks from its own.
        post { setFavorites(favorites.toMutableList().also { it[slot - 1] = name }, by.ifBlank { "chat" }) }
        return "Favorite #$slot changed from $was to $name."
    }

    /** One line per change in "favorites history.txt": when, who, and each box that changed. */
    private fun logFavorites(before: List<String>, after: List<String>, by: String) {
        val changed = after.indices.filter { before.getOrElse(it) { "" } != after[it] }
        if (changed.isEmpty()) return
        val now = java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ')
        val what = changed.joinToString("; ") { "#${it + 1} ${before.getOrElse(it) { "" }.ifBlank { "(none)" }} -> ${after[it].ifBlank { "(none)" }}" }
        runCatching { File(home, FAVORITES_HISTORY).appendText("$now\t${by.replace('\t', ' ').take(40)}\t$what\n") }.onFailure { say("favorites history: $it") }
    }

    /**
     * The start screen's favorites clicked: the panel's own favorites screen (PcTypeScreen), the slots as they are, typed
     * on the on-screen keyboard; a name clicked from the list sets its slot in the one standing list at once. Until
     * 2026-10-07 this opened a BizHawk forms window.
     */
    private fun editFavorites() {
        panel.typing.openFavorites(favorites, com.ironmonone.pcui.PcFavorites.scope(natDex).shown)
    }

    /**
     * "(Leave a note)" clicked, or the panel right-clicked: the note on the Pokemon the panel shows, typed on the panel's
     * keyboard and shown as it is typed; Save keeps it in the run's folder. Until 2026-10-07 a BizHawk forms window.
     */
    private fun editNote(l: Loaded, species: Int = panel.shownSpecies) {
        val sp = species.takeIf { it > 0 } ?: return
        val st = l.state
        val name = st?.let { s -> (listOfNotNull(s.enemy?.takeIf { it.species == sp }?.let { it.speciesName }) +
            s.party.filter { it.mon.species == sp }.map { it.speciesName }).firstOrNull() } ?: ""
        panel.typing.openNote(sp, name.ifBlank { runCatching { l.tracker.speciesName(sp) }.getOrNull() ?: panel.shownName }, l.run?.note(sp).orEmpty())
    }

    /** The keyboard's answers: a note saved, a favorite set, the names offered. Called on the session's thread (a click). */
    /** What besteon's screens over the card read (PcScreens), from the run loaded when they are drawn. Session thread. */
    private fun wireScreens() {
        panel.screens.source = object : com.ironmonone.pcui.PcScreens.Source {
            private val l get() = loaded
            override fun info(species: Int) = l?.let { infoFacts(it, species) }
            override fun learnset(species: Int) = l?.tracker?.learnset(species).orEmpty()
            override fun moveName(id: Int) = l?.tracker?.moveName(id) ?: "#$id"
            override fun moveRow(id: Int) = l?.tracker?.moveRowFor(id)
            override fun moveText(id: Int) = l?.tracker?.moveDescription(id)
            override fun heals() = l?.let { it.tracker.healsInBag(it.state?.let { s -> s.lead ?: s.party.firstOrNull() }) }.orEmpty()
            override fun catchRates(hpAdjust: Int) = l?.tracker?.catchRates(hpAdjust)
            override fun battleDetails() = l?.tracker?.battleDetails()
            override fun routeName(mapId: Int) = l?.tracker?.routeInfo(mapId)?.first ?: l?.state?.routeName ?: ""
            override fun routeAreas(mapId: Int) = l?.tracker?.routeEncounters(mapId).orEmpty()
            override fun routeSeen(mapId: Int) = l?.run?.marks?.seenOnRoute(mapId).orEmpty()
            override val runTables: Boolean get() = l?.map?.hns == true
            override fun trainer(id: Int) = l?.tracker?.trainer(id)
            override fun trainersOnRoute(mapId: Int) = l?.tracker?.trainersOnRoute(mapId).orEmpty()
            override fun itemName(id: Int) = l?.tracker?.itemName(id) ?: "#$id"
            override fun itemText(id: Int) = l?.tracker?.itemDescription(id)
            override fun sprite(species: Int) = com.ironmonone.pcui.PcPanel.sprite(species, com.ironmonone.pcui.PcPanel.hnsOf(l?.state))
        }
    }

    private fun wireTyping() {
        val t = panel.typing
        t.suggest = { typed ->
            if (t.kind == com.ironmonone.pcui.PcTypeScreen.Kind.ALWAYS) alwaysSuggest(typed)
            else com.ironmonone.pcui.PcFavorites.suggest(typed, natDex, limit = 5)
        }
        t.onAlways = { name ->
            com.ironmonone.app.Favorites.idOf(name)?.let { id ->
                com.ironmonone.app.SpriteIsMeSettings.always = id
                com.ironmonone.app.SpriteIsMeSettings.who = com.ironmonone.app.SpriteIsMeSettings.Who.ALWAYS
                com.ironmonone.app.SpriteIsMeSettings.save()
                panel.settings.alwaysName = name
                loaded?.let { updatePlayAs(it) }
            }
        }
        panel.settings.onAction = { a -> settingsAction(a) }
        panel.settings.alwaysName = com.ironmonone.app.SpriteIsMeSettings.always.takeIf { it > 0 }
            ?.let { com.ironmonone.app.Favorites.namesInOrder.firstOrNull { (id, _) -> id == it }?.second }.orEmpty()
        t.onNote = { sp, text -> loaded?.run?.setNote(sp, text.take(500)) }
        t.onFavorite = { slot, typed ->
            val name = if (typed.isBlank()) "" else com.ironmonone.pcui.PcFavorites.resolve(typed, natDex)
            if (name != null) setFavorites(favorites.toMutableList().also { while (it.size < 9) it += ""; if (slot in it.indices) it[slot] = name }, "the tracker")
        }
    }

    // ---- the starter balls (besteon's RerollBallPicker) -------------------------------------------------------------

    /** The run's mode ("kaizo", "survival") from its settings file, as the phone's FavoriteBall.modeOf reads it. */
    private val mode: String? by lazy { com.ironmonone.pcui.PcFavorites.mode(settings) }

    /**
     * TrackerScreen.canShowBallPicker (in the lab with no Pokemon, "Show random ball picker" on, not a Journey run),
     * and the balls holding a favorite this mode lets you take (the phone's FavoriteBall); null when not shown.
     */
    private fun ballPicker(l: Loaded, st: TrackerState?): com.ironmonone.pcui.PcBallPick? {
        if (st == null || st.partyCount != 0 || !st.inLab || !com.ironmonone.app.TrackerOptions.showBallPicker) return null
        if (mode == com.ironmonone.pcui.PcFavorites.JOURNEY) return null
        val marked = com.ironmonone.pcui.PcFavorites.favoriteBalls(favorites, natDex, mode, l.starters) { sp -> l.tracker.baseStats(sp)?.bst }
        return com.ironmonone.pcui.PcBallPick(l.ballPick, marked)
    }

    /** The die (RerollBallPicker.onClick): another coin toss. */
    private fun rerollBall(l: Loaded) {
        // A repeat of the ball already marked looks like the click did nothing.
        l.ballPick = (l.ballPick + 1 + SecureRandom().nextInt(2)) % 3
        say("ball picker: rolled ${listOf("left", "middle", "right")[l.ballPick]}")
    }

    // ---- GachaMon's stars and the PC heals (TrackerScreen's heals box) ------------------------------------------------

    /** GachaMonData.autoDetermineIronmonRuleset for this session's settings. */
    private val gachaRuleset: String by lazy { com.ironmonone.pcui.PcGachaStars.ruleset(settings.nameWithoutExtension, natDex) }

    private fun gachaNow(l: Loaded, p: com.ironmonone.tracker.TrackedMon): Int? {
        val m = p.mon
        val key = listOf(m.pid, m.species, m.level, m.moves, m.maxHp, m.atk, m.spAtk)
        l.gachaCache?.takeIf { it.first == key }?.let { return it.second }
        val n = runCatching { com.ironmonone.pcui.PcGachaStars.stars(l.tracker, p, gachaRuleset) }.getOrNull() ?: return null
        l.gachaCache = key to n
        return n
    }

    /** A Pokemon seen in the party for the first time this run gets its card's stars, kept in the run's folder. */
    private fun gachaJoin(l: Loaded, st: TrackerState) {
        var added = false
        for (p in st.party) {
            val k = com.ironmonone.pcui.PcGachaStars.key(p)
            if (k in l.gachaJoined) continue
            val n = runCatching { com.ironmonone.pcui.PcGachaStars.stars(l.tracker, p, gachaRuleset) }.getOrNull() ?: continue
            l.gachaJoined[k] = n
            added = true
        }
        if (added) l.run?.folder?.let { d -> runCatching { File(d, GACHA).writeText(l.gachaJoined.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }) } }
    }

    /**
     * The phone's PcHeals.arm: a Survival or Survival Revival run turns "Track PC Heals" on by itself, counting down
     * from its limit (so that run shows the PC heals in the heals box, and the others GachaMon's stars), with the 8th
     * badge's bonus and the Kanto heals, and the game's heal statistic counted when auto tracking is on.
     */
    private val healLimit: com.ironmonone.app.PcHeals.Limit? by lazy {
        com.ironmonone.app.PcHeals.limitFor(settings.name) ?: when (mode) {
            "survivalrevival" -> com.ironmonone.app.PcHeals.Limit.REVIVAL
            "survival" -> com.ironmonone.app.PcHeals.Limit.SURVIVAL
            else -> null
        }
    }

    private fun observeHeals(l: Loaded, st: TrackerState) {
        val badges = Integer.bitCount(st.badges)
        com.ironmonone.app.PcHeals.arm(l.attempt, healLimit, badges, st.leagueBeaten)
        com.ironmonone.app.PcHeals.observe(l.attempt, st.centerHealsStat)
        com.ironmonone.app.PcHeals.observeBadges(l.attempt, badges, healLimit)
        com.ironmonone.app.PcHeals.observeLeague(l.attempt, st.leagueBeaten, healLimit)
    }

    // ---- standing files: one copy for every run -----------------------------------------------------------------------

    /**
     * Until 2026-10-07 the favorites, the gear's options and the theme were kept per preset beside the runs
     * ("<prefix> favorites.txt" and so on) and each seed's folder had its own favorites.txt. The standing files take the
     * first of those found, once, when they do not exist yet: the preset's file, else any preset's newest, else (the
     * favorites) the newest seed folder's list that names one.
     */
    private fun migrate() {
        fun adopt(target: File, suffix: String, extra: () -> File? = { null }) {
            if (target.isFile) return
            val from = File(outDir, "$prefix $suffix").takeIf { it.isFile }
                ?: outDir.listFiles { f -> f.isFile && f.name.endsWith(" $suffix") }?.maxByOrNull { it.lastModified() }
                ?: extra()
                ?: return
            runCatching { home.mkdirs(); Files.copy(from.toPath(), target.toPath()) }
                .onSuccess { say("kept ${from.name} as ${target.name} for every run") }.onFailure { say("could not keep ${from.name}: $it") }
        }
        adopt(favoritesFile, "favorites.txt") {
            seedsDir.listFiles { f -> f.isDirectory }?.sortedByDescending { it.name }?.map { File(it, "favorites.txt") }
                ?.firstOrNull { f -> f.isFile && com.ironmonone.pcui.PcFavorites.slots(f.readText()).any { it.isNotBlank() } }
        }
        adopt(File(home, OPTIONS), "tracker options.txt")
        adopt(File(home, THEME), "theme.txt")
    }

    /** When kaizocore-hns.lua last said it is alive (lua.txt), with or without a game open. */
    @Volatile private var lastScript = 0L

    /** Whether BizHawk's script is running (it says so about once a second, with or without a game open). */
    val scriptRunning: Boolean get() = System.currentTimeMillis() - lastScript < 4_000

    /** What the script says is open: "none", "other" or "hns" (lua.txt). */
    val scriptGame: String? get() = luaState["game"]

    /** The window's New Run: the script opens the next run as the combo would, with or without a game open. */
    fun requestNewRun(): String {
        if (!scriptRunning) return "BizHawk's script is not running. In BizHawk open Tools, then Lua Console, and open kaizocore-hns.lua."
        post { cmd("cmd=newrun", "at=${System.currentTimeMillis() / 1000}") }
        return "Opening the next run in BizHawk."
    }

    /** The attempt whose game is still on disk, so Continue can open that seed again. Null when there is none. */
    val attemptToContinue: Int? get() = attempts.takeIf { it > 0 && auto.isFile }

    /**
     * Opens the attempt already on disk. The seed is not rolled again and the attempt count stays.
     * BizHawk loads the save that belongs to that file, so an in-game save comes back at the title screen.
     */
    fun requestContinue(): String {
        val n = attemptToContinue ?: return "There is no attempt to continue. New Run starts one."
        if (!scriptRunning) return "Open a game in BizHawk, then Tools, Lua Console, and kaizocore-hns.lua. Then press Continue Attempt $n."
        val path = auto.absolutePath
        post { cmd("cmd=continue", "at=${System.currentTimeMillis() / 1000}", "path=$path", "attempt=$n") }
        return "Opening attempt $n on the same seed. Continue from the title screen."
    }

    // ---- the game over card (PLAN row 6) ----------------------------------------------------------------------------

    /** What kaizocore-hns.lua last said in lua.txt (battlestate=1 while it holds the state a battle began from). */
    @Volatile private var luaState: Map<String, String> = emptyMap()
    private var luaStateStamp = 0L

    private fun readLuaState() {
        val f = File(dir, "lua.txt")
        val stamp = if (f.isFile) f.lastModified() else 0L
        if (stamp == luaStateStamp) return
        luaStateStamp = stamp
        val was = luaState["battlestate"]
        luaState = if (stamp == 0L) emptyMap() else runCatching { parse(f.readText()) }.getOrDefault(emptyMap())
        // The script's own clock (about once a second): it is running, with or without a game open.
        val alive = luaState["alive"]?.toLongOrNull()
        if (alive != null && alive >= System.currentTimeMillis() / 1000 - 3) { lastScript = System.currentTimeMillis(); lastContact = lastScript }
        if (luaState["battlestate"] != was) loaded?.let { if (it.latch.ending != null) showPanel(it, force = true) }
    }

    private var cmdSeq = 0

    /** One thing for BizHawk's script to do (jar.cmd); the script deletes it once done. */
    private fun cmd(vararg lines: String) {
        cmdSeq++
        writeAtomic(File(dir, "jar.cmd"), (listOf("seq=$cmdSeq") + lines).joinToString("\n", postfix = "\n").toByteArray())
    }

    /** The log of the run [l] plays: the one in its folder, else beside its file (besteon's <rom>.log). */
    fun logFile(l: Loaded): File? =
        l.run?.folder?.listFiles { f -> f.name.endsWith(".log") }?.firstOrNull()
            ?: File(l.path + ".log").takeIf { it.isFile }

    /**
     * Inspect the log (LogOverlay.viewLogFile): the run's log read once, then the viewer over the game area, on the
     * lead's page when the party has one. The game keeps running under it, as it does under besteon's.
     */
    private fun openLog(l: Loaded) {
        if (!l.logLoaded) {
            l.logLoaded = true
            val f = logFile(l)
            val t = System.nanoTime()
            l.logData = f?.let { runCatching { com.ironmonone.pcui.PcLogData.load(it, l.tracker) }.onFailure { e -> say("log: $e") }.getOrNull() }
            say("log ${f?.name ?: "(none)"} read in %.0f ms: ${l.logData?.log?.pokemon?.size ?: 0} Pokemon, ${l.logData?.log?.trainers?.size ?: 0} trainers, ${l.logData?.routes?.size ?: 0} routes".format((System.nanoTime() - t) / 1e6))
        }
        // Your team's IVs and EVs (Show IVs on a Pokemon's page) and levels (the learn-level boxes), in HP, ATK, DEF, SPA, SPD, SPE order.
        val party = l.state?.party.orEmpty()
        fun order(v: List<Int>) = listOf(0, 1, 2, 4, 5, 3).map { v.getOrElse(it) { 0 } }
        overlay.shared.team = party.associate { it.mon.species to (order(it.mon.ivs) to order(it.mon.evs)) }
        overlay.shared.teamLevels = party.associate { it.mon.species to it.mon.level }
        overlay.shared.movesSeen = { sp -> l.run?.marks?.movesSeenFor(sp).orEmpty() }
        overlay.open(l.logData, (l.state?.lead ?: l.state?.party?.firstOrNull())?.mon?.species)
    }

    /** A click on the game over card (PcGameOverCard), on the session's thread; the caller draws the panel again. */
    private fun onOver(l: Loaded, a: com.ironmonone.pcui.PcOverAction) {
        val e = l.latch.ending ?: return
        when (a) {
            com.ironmonone.pcui.PcOverAction.CYCLE_TEAM -> {
                // nextTeamPokemon + randomizeAnnouncerQuote.
                if (e.team.isNotEmpty()) e.teamIndex = (e.teamIndex + 1) % e.team.size
                e.quote = com.ironmonone.pcui.PcPanel.rerollQuote(l.attempt)
            }
            com.ironmonone.pcui.PcOverAction.CONTINUE -> { l.latch.dismiss(); overlay.close(); say("game over card: continue playing") }
            com.ironmonone.pcui.PcOverAction.RETRY -> {
                if (!e.retryConfirm) { e.retryConfirm = true; return }
                // GameOverScreen.loadTempSaveState: the script loads the state it took when the battle began.
                cmd("cmd=retry")
                l.latch.retried()
                overlay.close()
                say("game over card: retry the battle")
            }
            com.ironmonone.pcui.PcOverAction.SAVE -> {
                if (e.save != com.ironmonone.pcui.PcSaveStatus.NOT_CLICKED) return
                e.save = if (saveAttempt(l)) com.ironmonone.pcui.PcSaveStatus.SUCCESS else com.ironmonone.pcui.PcSaveStatus.FAILED
            }
            com.ironmonone.pcui.PcOverAction.INSPECT_LOG -> openLog(l)
            // Grade my notes: StatMarkingScoreSheet on the panel, graded by the app's ScoreSheet.
            com.ironmonone.pcui.PcOverAction.GRADE -> l.run?.marks?.let { m ->
                l.scoreSheet = com.ironmonone.app.ScoreSheet.build(m, { sp -> l.tracker.baseStats(sp) }, { sp -> l.tracker.speciesName(sp) })
            }
            com.ironmonone.pcui.PcOverAction.GRADE_CLOSE -> l.scoreSheet = null
            // The prize card: made once a run, shown on GachaMonOverlay's View tab over the game area.
            com.ironmonone.pcui.PcOverAction.PRIZE -> {
                if (!l.prizeMade) {
                    l.prizeMade = true
                    l.prize = runCatching {
                        com.ironmonone.pcui.PcPrize.make(l.tracker, l.state, l.attempt)
                            // The staged ending beat nobody: the checks get a card made the same way from the staged opponent.
                            ?: panelDemo?.enemy?.let { e -> com.ironmonone.pcui.PcPrize.demo(l.tracker, l.state, l.attempt, e.species, e.level, 1) }
                    }.onFailure { say("prize card: $it") }.getOrNull()
                }
                val g = l.prize ?: return
                overlay.openPrize(g, com.ironmonone.pcui.PcPanel.sprite(g.card.pokemonId, com.ironmonone.pcui.PcPanel.hnsOf(l.state)))
                say("prize card: ${g.trainerName ?: ""} ${g.name}, ${g.card.stars()} stars")
            }
        }
    }

    /** GameOverScreen.numDefeatedTrainers >= 2 (and the demo's staged ending, which beat nobody, offers it for the checks). */
    private fun prizeOffered(l: Loaded): Boolean = panelDemo != null || com.ironmonone.pcui.PcPrize.defeated(l.tracker, l.state) >= 2

    /**
     * Save this attempt (GameOverScreen.saveCurrentGameFiles): the run's folder keeps its game, its log and a BizHawk
     * save state for good (a "keep" file, which the pruning never touches), so the attempt loads days later from that
     * folder with its marks and notes. The reference copies the files to saved_games; here the folder already holds them.
     */
    private fun saveAttempt(l: Loaded): Boolean {
        val d = l.run?.folder ?: run { say("save this attempt: this game has no run folder"); return false }
        return runCatching {
            val name = d.listFiles { f -> f.name.endsWith(".gba") }?.firstOrNull()?.nameWithoutExtension
                ?: "$prefix ${d.name}".also { n ->
                    // Its game was pruned (only the newest KEEP_ROMS keep theirs): put it back from the file in play.
                    val from = File(l.path)
                    if (from.isFile) runCatching { Files.createLink(File(d, "$n.gba").toPath(), from.toPath()) }
                        .onFailure { Files.copy(from.toPath(), File(d, "$n.gba").toPath(), StandardCopyOption.REPLACE_EXISTING) }
                }
            if (d.listFiles { f -> f.name.endsWith(".log") }.isNullOrEmpty()) {
                File(l.path + ".log").takeIf { it.isFile }?.let { Files.copy(it.toPath(), File(d, "$name.gba.log").toPath(), StandardCopyOption.REPLACE_EXISTING) }
            }
            File(d, "keep").writeText("saved=${java.time.LocalDateTime.now().withNano(0)}\nattempt=${l.attempt}\n")
            cmd("cmd=savestate", "path=" + File(d, "$name.State").absolutePath)
            say("save this attempt: kept in \"${d.name}\"")
            true
        }.onFailure { say("save this attempt: $it") }.getOrDefault(false)
    }

    @Volatile private var loaded: Loaded? = null
    private var gen = 0
    @Volatile private var pollMs = 700L
    @Volatile private var attempts = readAttempts()
    /** Hashes of runs this session made, to the file holding each. */
    private val known = LinkedHashMap<String, File>()

    private fun readAttempts(): Int = runCatching { attemptsFile.readText().trim().toInt() }.getOrDefault(0)

    private fun hashesOf(b: ByteArray): Set<String> {
        val md5 = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02X".format(it) }
        return setOf(PcHns.sha1(b), md5, "%08X".format(PcHns.crc(b)))
    }

    /** BizHawk's hash may carry a "SHA1:" style prefix; compare the hex only. */
    private fun normHash(h: String) = h.substringAfter(':').trim().uppercase()

    private fun adopt(rom: ByteArray, path: String, hashes: Set<String> = hashesOf(rom), number: Int = ++gen): Loaded {
        val l = Loaded(number, rom, hashes, path)
        overlay.close()
        panel.newGame()
        val runFolder = hashes.firstNotNullOfOrNull { seedIndex[it] }
        l.run = runFolder?.let { RunData(it) }
        // The staged checks (--panel-demo): marks to grade on the staged ending.
        if (panelDemo != null) l.run?.marks?.let { m -> com.ironmonone.pcui.Samples.demoMarks.forEach { (sp, v) ->
            if (m.of(sp).all { it == 0 }) v.forEachIndexed { i, n -> repeat(n) { m.cycle(sp, i) } } } }
        runFolder?.let { d -> runCatching { File(d, GACHA).readLines().forEach { line ->
            line.substringBefore('=').toLongOrNull()?.let { k -> line.substringAfter('=').trim().toIntOrNull()?.let { l.gachaJoined[k] = it } } } } }
        l.playMillis = 1000L * (runFolder?.let { runCatching { File(it, "playtime.txt").readText().trim().toLong() }.getOrNull() } ?: 0L)
        l.attempt = runFolder?.let { runCatching { parse(File(it, "run.txt").readText())["attempt"]?.toInt() }.getOrNull() } ?: attempts
        loaded = l
        fxFor(l)
        showPanel(l)
        pollMs = 500
        val folder = hashes.firstNotNullOfOrNull { seedIndex[it] }
        say("tracking ${File(path).name}: ${l.map.name}, gen ${l.gen}" + (folder?.let { ", run folder \"${it.name}\"" } ?: ", no run folder"))
        events.changed()
        return l
    }

    private fun hello(req: Map<String, String>): List<String> {
        val hash = normHash(req["hash"].orEmpty())
        val path = req["path"].orEmpty()
        loaded?.let { if (hash in it.hashes) return listOf("ok=1", "gen=${it.gen}", "attempt=$attempts") }
        // A run this session made, or any file BizHawk says it loaded.
        val file = known.entries.firstOrNull { (k, _) -> k == hash }?.value
            ?: seedIndex[hash]?.let { seedRom(it) }
            ?: File(path).takeIf { path.isNotEmpty() && it.isFile }
            ?: return listOf("ok=0", "why=KaizoCore PC cannot find this game's file. Load it with New Run.")
        val rom = file.readBytes()
        val hs = hashesOf(rom)
        if (hash.isNotEmpty() && hash !in hs) return listOf("ok=0", "why=The file on disk is not the game BizHawk has loaded. Load it again.")
        return try {
            val l = adopt(rom, file.path)
            listOf("ok=1", "gen=${l.gen}", "attempt=$attempts")
        } catch (e: Exception) {
            listOf("ok=0", "why=" + (e.message ?: "This is not a KaizoCore Heart & Soul game.").replace('\n', ' '))
        }
    }

    private fun newRun(): List<String> {
        val t0 = System.nanoTime()
        val s = take(30_000) ?: return listOf("ok=0", "why=" + (genError ?: "The next run is not ready yet."))
        val tTake = System.nanoTime()
        // besteon's SaveCurrentRom keeps the last run as PreviousAttempt; a rename does it without a 32 MB copy.
        val autoLog = File(auto.path + ".log")
        val prevLog = File(prev.path + ".log")
        runCatching {
            if (auto.isFile) { Files.move(auto.toPath(), prev.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            if (autoLog.isFile) { Files.move(autoLog.toPath(), prevLog.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }.onFailure { say("could not keep the last run as PreviousAttempt: $it") }
        var target = auto
        try {
            move(s.rom, auto)
            move(s.log, autoLog)
        } catch (e: Exception) {
            // The AutoRandomized file is held by something: play the staged file where it is.
            say("could not rename to ${auto.name}: $e; using ${s.rom.name}")
            target = s.rom
        }
        attempts += 1
        writeAttempts()
        events.changed()
        runCatching { keepSeed(target, File(target.path + ".log"), s) }.onFailure { say("could not keep the run's folder: $it") }
        val tFiles = System.nanoTime()
        s.hashes.forEach { known[it] = target }
        while (known.size > 30) known.remove(known.keys.first())
        // The tracker for the new game is set up once the answer is out (afterAnswer), so BizHawk opens it at once.
        val number = ++gen
        val path = target.path
        afterAnswer = {
            val t = System.nanoTime()
            try { adopt(s.bytes, path, s.hashes, number) } catch (e: Exception) { say("tracker: $e") }
            say("tracker ready for attempt $attempts in %.1f ms".format((System.nanoTime() - t) / 1e6))
        }
        say("new run: attempt $attempts, seed ${s.seed}, take %.1f ms, files %.1f ms".format(
            (tTake - t0) / 1e6, (tFiles - tTake) / 1e6))
        return listOf("ok=1", "path=${target.absolutePath}", "attempt=$attempts", "seed=${s.seed}",
            "gen=$number", "sha1=${s.hashes.first()}")
    }

    // ---- one folder per seed --------------------------------------------------------------------------------------

    /**
     * Every run gets a folder of its own, "<prefix> seeds/attempt 00123 seed <seed>", that never changes name: run.txt
     * (seed, settings, pool, attempt, the ROM's SHA-1 as BizHawk reports it, CRC, build), the log, and for the newest
     * [KEEP_ROMS] runs the ROM itself. ROM and log are hard links (same disk, no copy), so the next New Run renaming
     * AutoRandomized does not touch them. This is where the run's tracker data (stat marks, notes, play time) lives:
     * BizHawk's hash of whatever game is loaded finds its folder ([seedIndex]), so a run comes back with its notes after
     * other games or seeds were played in between. The newest [keepLogs] folders are kept, and never a folder holding
     * a file named "keep" (Save this attempt, a run played long enough) or "tracker.json".
     */
    private val seedsDir = File(outDir, "$prefix seeds")
    private val KEEP_ROMS = 10
    private val NL = "\n"
    /** SHA-1 (BizHawk's gameinfo.getromhash) to the run's folder, read from every run.txt at start. */
    private val seedIndex = HashMap<String, File>()

    private fun loadSeedIndex() {
        seedsDir.listFiles { f -> f.isDirectory }?.forEach { d ->
            val sha = runCatching { parse(File(d, "run.txt").readText())["sha1"] }.getOrNull()
            if (!sha.isNullOrEmpty()) seedIndex[sha.uppercase()] = d
        }
    }

    /** The ROM of a run's folder, when it is still kept. */
    private fun seedRom(d: File): File? = d.listFiles { f -> f.name.endsWith(".gba") }?.firstOrNull()

    private fun keepSeed(rom: File, log: File, s: Staged) {
        val d = File(seedsDir, "attempt %05d seed %d".format(attempts, s.seed)).apply { mkdirs() }
        val name = "$prefix attempt %05d".format(attempts)
        fun link(from: File, to: File) {
            if (!from.isFile) return
            runCatching { Files.createLink(to.toPath(), from.toPath()) }.onFailure {
                runCatching { Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            }
        }
        link(rom, File(d, "$name.gba"))
        link(log, File(d, "$name.gba.log"))
        File(d, "run.txt").writeText(listOf(
            "seed=${s.seed}", "settings=${settings.nameWithoutExtension}", "pool=$pool", "attempt=$attempts",
            "sha1=${s.hashes.first()}", "crc=%08X".format(PcHns.crc(s.bytes)), "build=%08X".format(PcHns.crc(build)),
            "made=${java.time.LocalDateTime.now().withNano(0)}",
        ).joinToString(NL, postfix = NL))
        seedIndex[s.hashes.first()] = d
        val all = seedsDir.listFiles { f -> f.isDirectory && f.name.startsWith("attempt ") }?.sortedByDescending { it.name } ?: return
        fun kept(f: File) = File(f, "keep").exists() || File(f, "tracker.json").exists()
        all.drop(KEEP_ROMS).filterNot(::kept).forEach { f -> seedRom(f)?.delete() }
        all.drop(keepLogs).filterNot(::kept).forEach { f ->
            f.listFiles()?.forEach { x -> x.delete() }
            f.delete()
            seedIndex.values.remove(f)
        }
    }

    /** Work New Run leaves for after its answer is written. */
    private var afterAnswer: (() -> Unit)? = null

    /** Work from other threads (Twitch chat's !fav), run on the session's thread, which alone draws the panel. */
    private val posted = java.util.concurrent.LinkedBlockingQueue<() -> Unit>()
    private fun post(work: () -> Unit) { posted.add(work) }

    /** The stream snapshot for the chat commands (PcSnapshot): your party as the panel shows it. Any thread. */
    fun snapshotJson(): String {
        val l = loaded
        return com.ironmonone.pcui.PcSnapshot.json(l?.state, l?.attempt ?: attempts, l?.map?.name ?: "Heart & Soul", l?.run?.marks)
    }

    private fun move(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Time played while snapshots arrive. A paused emulator sends none, so the clock stops. An unchanged read still counts. */
    private fun notePlay(l: Loaded) {
        val now = System.currentTimeMillis()
        val gap = now - l.lastReadAt
        if (l.lastReadAt > 0 && gap in 1..5000 && (l.latch.ending == null || panelDemo != null)) l.playMillis += gap
        l.lastReadAt = now
        if (now - l.playSavedAt > 15_000) { l.playSavedAt = now; l.run?.folder?.let { d -> runCatching { File(d, "playtime.txt").writeText("${l.playMillis / 1000}") } } }
    }

    // ---- RAM ------------------------------------------------------------------------------------------------------

    private fun takeRam(f: File) {
        // Take the file off the name Lua writes, so a stuck read cannot freeze the next snapshot.
        val take = File(f.path + ".take")
        take.delete()
        val bytes = try {
            if (!f.renameTo(take)) {
                val b = f.readBytes()
                f.delete()
                b
            } else {
                take.readBytes()
            }
        } catch (e: Exception) {
            runCatching { f.delete() }
            runCatching { take.delete() }
            return
        }
        take.delete()
        val nl = bytes.indexOf('\n'.code.toByte()).takeIf { it in 1..40 } ?: return say("RAM file without a header")
        val head = String(bytes, 0, nl, Charsets.ISO_8859_1)
        val g = head.removePrefix("KCRAM ").trim().toIntOrNull() ?: return say("RAM file header '$head'")
        val l = loaded ?: return
        if (g != l.gen) return
        val start = nl + 1
        val prev = l.ramBody
        val same = prev != null && java.util.Arrays.equals(bytes, start, bytes.size, prev, 0, prev.size)
        if (same) {
            if (!l.ramAgain) { notePlay(l); return }
            l.ramAgain = false
        } else {
            l.ramBody = bytes.copyOfRange(start, bytes.size)
            l.ramAgain = true
        }
        val ew = l.reader.ewram
        val iw = l.reader.iwram
        val bodyLen = bytes.size - start
        if (bodyLen == 0x40000 + 0x8000) {
            java.lang.System.arraycopy(bytes, start, ew, 0, 0x40000)
            java.lang.System.arraycopy(bytes, start + 0x40000, iw, 0, 0x8000)
        } else {
            ew.fill(0); iw.fill(0)
            if (!placeRam(bytes, start, ew, iw)) return say("RAM file of $bodyLen bytes is not a list of ranges")
        }
        val t = System.nanoTime()
        val st = try { l.tracker.read() } catch (e: Exception) { say("tracker.read: $e"); return }
        val ms = (System.nanoTime() - t) / 1e6
        l.reads++
        // The phone's pace (TrackerPoll: 100 ms settling, 250 in battle, 700 otherwise), in real time at any speed.
        pollMs = when { st.settling -> 100L; st.inBattle -> 250L; else -> 700L }
        val party = describe(st)
        if (party != l.lastParty) {
            l.lastParty = party
            say("tracker (read ${l.reads}, %.1f ms): %s".format(ms, party.replace("\n", " | ")))
            writeAtomic(File(dir, "tracker.txt"), ("gen=${l.gen}\nreads=${l.reads}\nread_ms=%.1f\n".format(ms) + party + "\n").toByteArray())
        }
        // A staged check's state is recorded as a read would be (the encounter it is, the ability it revealed).
        l.run?.observe(panelDemo ?: st, l.tracker)
        notePlay(l)
        val shown = panelDemo ?: st
        gachaJoin(l, shown)
        if (panelDemo == null) observeHeals(l, st)
        l.state = shown
        if (l.latch.onRead(shown.gameOver, shown.inBattle) { shown.party }) {
            l.latch.ending?.quote = com.ironmonone.pcui.PcPanel.quote(l.attempt)
            say("game over: ${shown.gameOver} on attempt ${l.attempt}")
            events.changed()
        }
        if (panelDemo == null) updatePlayAs(l)
        val details = if (panelDemo == null && st.inBattle) runCatching { l.tracker.battleDetails() }.getOrNull() else null
        panel.fxDetails = details
        showPanel(l)
        window?.showEffects(if (panelDemo == null) battleEffects(st, details) else null)
    }

    /**
     * What the card's real hit chance and move power need from this battle (PcReal): the names on each side's own list
     * in the game's battle details (the read's, panel.fxDetails), and the ROM's data. Null out of battle.
     */
    private fun battleFacts(l: Loaded, st: TrackerState?): com.ironmonone.pcui.PcBattleFacts? {
        if (st?.inBattle != true) return null
        val d = panel.fxDetails
        fun names(list: List<com.ironmonone.tracker.GbaTracker.BattleDetail>) = list.mapNotNull { x ->
            when {
                x.moveId == 199 -> "Lock-On"; x.moveId == 193 -> "Foresight"; x.moveId == 107 -> "Minimize"; x.moveId == 268 -> "Charge"
                x.text.startsWith("Confused", true) -> "Confused"
                x.text.startsWith("Mud Sport", true) -> "Mud Sport"; x.text.startsWith("Water Sport", true) -> "Water Sport"
                else -> null
            }
        }.toSet()
        return com.ironmonone.pcui.PcBattleFacts(
            own = d?.let { names(it.mons.getOrElse(0) { emptyList() } + it.sides.getOrElse(0) { emptyList() }) }.orEmpty(),
            foe = d?.let { names(it.mons.getOrElse(1) { emptyList() } + it.sides.getOrElse(1) { emptyList() }) }.orEmpty(),
            field = d?.let { names(it.field) }.orEmpty(),
            move = l.hitMove, hold = l.hitHold,
        )
    }

    /**
     * Developer mode (kaizocore-dev.txt beside the script, the file the script's own developer kit reads): the battle
     * and progress effects play on the tracker window, with this game's own art when the ROM is the build's (FxRomArt).
     * Off, the card draws its own sprite as it always has.
     */
    private fun fxFor(l: Loaded) {
        val w = window ?: return
        val driver = if (!File(home, "kaizocore-dev.txt").isFile) null
            else runCatching { com.ironmonone.pc.fx.FxDriver(w.font, com.ironmonone.pc.fx.FxRomArt.forBuild(l.rom), l.tracker) }
                .onFailure { say("effects: $it") }.getOrNull()
        com.ironmonone.pcui.PcPictures.cardDrawsWalking = driver == null
        w.setFx(driver)
        if (driver != null) say("effects on (developer mode), ROM art: ${if (driver.romArt) "read from the game" else "drawn in code"}")
    }

    /**
     * The tracker window's battle effects strip (GbaTracker.battleDetails): your side (battlers 0 and 2 and your
     * side's screens and hazards), the foe's (1 and 3 and theirs), and the field. Null out of battle.
     */
    private fun battleEffects(st: TrackerState, details: com.ironmonone.tracker.GbaTracker.BattleDetails?): TrackerWindow.Effects? {
        if (!st.inBattle) return null
        val d = details ?: return null
        fun side(i: Int) = (d.mons.getOrElse(i) { emptyList() } + d.mons.getOrElse(i + 2) { emptyList() } + d.sides.getOrElse(i) { emptyList() })
            .map { it.text }.distinct()
        return TrackerWindow.Effects(side(0), side(1), d.field.map { it.text })
    }

    /** The ability pop-up the script caught (ability.req): the enemy card keeps that ability for the run. */
    private fun takeAbility(f: File) {
        val text = runCatching { f.readText(Charsets.UTF_8) }.getOrNull() ?: return
        f.delete()
        val m = parse(text)
        val l = loaded ?: return
        val species = m["species"]?.toIntOrNull() ?: return
        val id = m["ability"]?.toIntOrNull() ?: return
        if (species <= 0 || id <= 0) return
        val name = l.tracker.abilityName(id)
        if (name.isBlank() || name == "?") return
        // Which Pokemon showed it (its personality value, from the script), so it counts for that one in this battle only.
        l.tracker.notePopup(species, id, m["pid"]?.toLongOrNull())
        if (l.run?.marks?.revealAbility(species, name) == true) showPanel(l, force = true)
    }

    /** A click or a note from the script (input.req), answered by drawing the panel again. */
    private fun takeInput(f: File) {
        val text = runCatching { f.readText(Charsets.UTF_8) }.getOrNull() ?: return
        f.delete()
        val m = parse(text)
        // The favorites editor's Save (PLAN row 8): the one list, with or without a run loaded.
        if (m["cmd"] == "favorites") { saveFavorites(com.ironmonone.pcui.PcFavorites.slots(PanelOut.unescape(m["slots"].orEmpty()))); return }
        val l = loaded ?: return
        when (m["cmd"]) {
            "click" -> {
                val x = m["x"]?.toIntOrNull() ?: return
                val y = m["y"]?.toIntOrNull() ?: return
                panel.click(x, y)
                showPanel(l, force = true)
                if (overlay.isOpen) overlay.draw()
            }
            // A right click on the panel: the note screen, as "(Leave a note)" opens it.
            "rnote" -> { if (!panel.typing.open) panel.back(); showPanel(l, force = true) }
            "note" -> {
                val sp = m["species"]?.toIntOrNull()?.takeIf { it > 0 } ?: return
                l.run?.setNote(sp, PanelOut.unescape(m["text"].orEmpty()).take(500))
                showPanel(l, force = true)
            }
            // besteon's "Toggle view" (Battle.togglePokemonViewed): only in battle.
            "toggle" -> if (l.state?.inBattle == true) {
                l.viewOwn = !l.viewOwn
                showPanel(l, force = true)
            }
            // The log viewer over the game area (PLAN row 7).
            "oclick" -> {
                val x = m["x"]?.toIntOrNull() ?: return
                val y = m["y"]?.toIntOrNull() ?: return
                overlay.click(x, y)
                showPanel(l, force = true)
            }
            // BizHawk sends the change since the last frame, positive up; the same gate as the window's wheel.
            "wheel" -> scriptWheel.feed(-(m["delta"]?.toIntOrNull() ?: return) / 100.0, System.nanoTime() / 1_000_000)
                ?.let { overlay.wheel(-it * 100) }
            // The start screen's favorites list. The log, when it is open, takes the wheel instead.
            "favwheel" -> {
                val dir = scriptWheel.feed(-(m["delta"]?.toIntOrNull() ?: return) / 100.0, System.nanoTime() / 1_000_000) ?: return
                panel.favScroll.step(dir)
                showPanel(l, force = true)
            }
            "search" -> overlay.search(PanelOut.unescape(m["text"].orEmpty()))
            // The script draws each letter itself. A draft refreshes suggestions or the log's results.
            // A note has no suggestion list, so it is not drafted: Save is the one picture.
            "draft" -> {
                val text = PanelOut.unescape(m["text"].orEmpty())
                val t = panel.typing
                if (t.open && t.kind != com.ironmonone.pcui.PcTypeScreen.Kind.NOTE) {
                    t.text = if (t.kind == com.ironmonone.pcui.PcTypeScreen.Kind.GOAL) text.filter { it.isDigit() }.take(6)
                        else text.take(com.ironmonone.pcui.PcTypeScreen.NAME_MAX)
                    showPanel(l, force = true)
                } else if (!t.open && overlay.isOpen) {
                    overlay.shared.setQuery(text.take(10))
                    overlay.draw()
                }
            }
            "commit" -> {
                val text = PanelOut.unescape(m["text"].orEmpty())
                val t = panel.typing
                if (t.open) {
                    t.text = text
                    commitTyping()
                    showPanel(l, force = true)
                } else if (overlay.isOpen) {
                    overlay.shared.setQuery(text.take(10))
                    overlay.draw()
                }
            }
            "typecancel" -> {
                if (panel.typing.open) panel.typing.close()
                showPanel(l, force = true)
            }
            "logclose" -> {
                overlay.close()
                showPanel(l, force = true)
            }
        }
    }

    /** Save on the typing screen: the note, the favorite or the Always Pokemon, then the screen closes. */
    private fun commitTyping() {
        val t = panel.typing
        when (t.kind) {
            com.ironmonone.pcui.PcTypeScreen.Kind.NOTE -> t.onNote(t.species, t.text.trim())
            com.ironmonone.pcui.PcTypeScreen.Kind.FAVORITES -> {
                // A name that is no Pokemon stays on the screen with the list's offers under it, instead of being saved.
                if (t.text.isNotBlank() && com.ironmonone.pcui.PcFavorites.resolve(t.text, natDex) == null) return
                t.onFavorite(t.slot, t.text.trim())
            }
            com.ironmonone.pcui.PcTypeScreen.Kind.ALWAYS -> t.onAlways(t.text.trim())
            com.ironmonone.pcui.PcTypeScreen.Kind.GOAL -> { t.saveGoal(); return }
            null -> {}
        }
        t.close()
    }

    // ---- the tracker window (session thread, posted from Swing's) -------------------------------------------------

    private fun windowClick(x: Float, y: Float, right: Boolean) {
        val l = loaded ?: return
        // The battle effects strip under the card: a left click there opens Battle Details, where nothing is cut short.
        if (y >= com.ironmonone.pcui.PcPanel.HEIGHT) {
            if (!right && l.state?.inBattle == true && panel.screens.top !is com.ironmonone.pcui.PcScreens.Screen.BattleDetails) {
                panel.screens.push(com.ironmonone.pcui.PcScreens.Screen.BattleDetails)
                showPanel(l, force = true)
            }
            return
        }
        // A right click goes back a card, and only that (Blake, 2026-10-11): it no longer opens the note keyboard, which took
        // the keys from BizHawk mid-battle, and never throws away a note being typed. Notes open from "(Leave a note)".
        if (right) { if (!panel.typing.open) panel.back() } else panel.clickAt(x, y)
        showPanel(l, force = true)
        if (overlay.isOpen) overlay.draw()
    }

    /** The mouse over the tracker window: the hand cursor over anything clickable; the strip holds still under it. */
    private fun windowMove(x: Float, y: Float) {
        val l = loaded ?: return
        val h = com.ironmonone.pcui.PcPanel.HEIGHT
        // The bottom strip (y 139 to 152): it does not turn to the next item while the mouse is on it.
        l.carousel.paused = y in 138f..h.toFloat() && panel.screens.top == null && !panel.typing.open && !panel.settings.open
        val hand = if (y >= h) l.state?.inBattle == true else panel.hoverAt(x, y)
        window?.setHand(hand)
    }

    private val scriptWheel = WheelGate()

    private fun windowWheel(delta: Int) {
        val l = loaded ?: return
        if (delta == 0) return
        if (overlay.logOpen) { overlay.wheel(delta); showPanel(l, force = true); return }
        // A card with pages turns them (moves, heals, trainers, catch rates, the route's Pokemon...).
        if (panel.screens.wheel(down = delta < 0)) { showPanel(l, force = true); return }
        panel.favScroll.step(if (delta < 0) 1 else -1)
        showPanel(l, force = true)
    }

    /** A key typed in the tracker window, which has the keyboard only while something is typed on the card. */
    private fun windowKey(c: Char) {
        val l = loaded ?: return
        val t = panel.typing
        if (t.open) {
            when (c) {
                '\n' -> commitTyping()
                '\u001b' -> t.close()
                else -> t.key(c)
            }
            showPanel(l, force = true)
            return
        }
        if (overlay.isOpen && panel.searching()) {
            val q = overlay.shared.view.query
            when (c) {
                '\u001b' -> { overlay.close(); showPanel(l, force = true); return }
                '\n' -> return
                '\b' -> overlay.shared.setQuery(q.dropLast(1))
                else -> overlay.shared.setQuery((q + c).take(10))
            }
            overlay.draw()
            showPanel(l, force = true)
        }
    }

    private fun describe(st: TrackerState): String {
        if (st.partyCount == 0) return "party: none" + if (st.inBattle) " (in battle)" else ""
        return "party ${st.partyCount}" + (if (st.inBattle) " (in battle)" else "") + "\n" + st.party.joinToString("\n") { m ->
            "${m.speciesName} Lv ${m.mon.level} HP ${m.mon.curHp}/${m.mon.maxHp} ${m.abilityName} [${m.moveNames.joinToString(", ")}]"
        }
    }

    // ---- the loop ---------------------------------------------------------------------------------------------------

    private fun parse(text: String): Map<String, String> =
        text.lines().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it).trim() to l.substring(it + 1).trim() } }.toMap()

    private fun writeAtomic(f: File, bytes: ByteArray) {
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes)
        for (i in 0 until 20) {
            try { move(tmp, f); return } catch (e: java.nio.file.AccessDeniedException) { Thread.sleep(2) }
            catch (e: java.nio.file.FileSystemException) { Thread.sleep(2) }
        }
        tmp.delete()
    }

    private var lastHeartbeat = ""

    /**
     * jar.lock, held for as long as this KaizoCore PC runs: the script reads one byte of it, and a read Windows refuses
     * means KaizoCore PC is alive. Until 2026-10-09 jar.txt said so with the time, rewritten every second, and the
     * script's first open of each new jar.txt waited on Windows Defender's scan (about 20 ms, on BizHawk's emulation
     * thread): a hitch in the game every second. jar.txt now changes only when what it says changes.
     */
    private var jarLock: java.nio.channels.FileLock? = null
    private var jarLockFile: java.io.RandomAccessFile? = null

    /** Takes jar.lock; false when another KaizoCore PC holds it. */
    private fun takeJarLock(): Boolean {
        val raf = java.io.RandomAccessFile(File(dir, JAR_LOCK), "rw")
        if (raf.length() < 1) raf.write('1'.code)
        val l = runCatching { raf.channel.tryLock() }.getOrNull()
        if (l == null) { raf.close(); return false }
        jarLockFile = raf; jarLock = l
        return true
    }

    private fun releaseJarLock() {
        runCatching { jarLock?.release() }; runCatching { jarLockFile?.close() }
        jarLock = null; jarLockFile = null
        File(dir, JAR_LOCK).delete()
    }

    private fun heartbeat() {
        if (stopping) return
        val n = synchronized(lock) { ready.size }
        val text = listOf(
            "lock=1",
            "start=$started",
            "backup=${File(outDir, "$prefix PreviousAttempt BackupSave.State").absolutePath}",
            "combo=$combo",
            "poll=$pollMs",
            "ready=$n",
            "attempts=$attempts",
            "gen=${loaded?.gen ?: 0}",
            "error=${genError.orEmpty().replace('\n', ' ')}",
        ).joinToString("\n", postfix = "\n")
        if (text == lastHeartbeat) return
        lastHeartbeat = text
        // alive: when this was written, for a reader that does not know jar.lock. The script reads the lock.
        writeAtomic(File(dir, "jar.txt"), ("alive=${System.currentTimeMillis() / 1000}\n" + text).toByteArray())
    }

    private fun clean() {
        dir.listFiles()?.forEach { if (it.isFile && it.name != "jar.log" && it.name != JAR_LOCK) it.delete() }
    }

    @Volatile private var cleaned = false

    /** Stops making runs and leaves nothing behind but jar.log and the runs themselves. */
    private fun shutdown() {
        if (cleaned) return
        cleaned = true
        stopping = true
        synchronized(lock) { lock.notifyAll() }
        runCatching { maker.join(5000) }
        // The run's marks and notes are written by the app's DiskWriter thread: let it finish first.
        runCatching { com.ironmonone.app.DiskWriter.drain() }
        overlay.close()
        panel.close()
        window?.showWaiting()
        releaseJarLock()
        clean()
        stageDir.listFiles()?.forEach { it.delete() }
        stageDir.delete()
    }

    /** Runs the session until [stop], a "quit" file, or the end of the process. False when another one already runs here. */
    fun run(): Boolean {
        dir.mkdirs(); outDir.mkdirs()
        // The tracker's settings and theme (the gear's settings screen), and the PC heal counts, kept beside the runs.
        // The favorites, the options and the theme: one copy each in the KaizoCore PC folder, for every run.
        migrate()
        favorites = readFavorites()
        com.ironmonone.app.TrackerOptions.load(File(home, OPTIONS))
        com.ironmonone.app.ThemeStore.load(File(home, THEME))
        com.ironmonone.app.SpriteIsMeSettings.load(File(home, PLAY_AS))
        com.ironmonone.pcui.PcPictures.load(File(home, PICTURES), File(home, "pictures"))
        say("pictures: ${com.ironmonone.pcui.PcPictures.set.key}${if (com.ironmonone.pcui.PcPictures.own) ", your own from " + File(home, "pictures").path else ""}; play as ${if (com.ironmonone.app.SpriteIsMeSettings.on) com.ironmonone.app.SpriteIsMeSettings.who.key else "off"}")
        com.ironmonone.app.PcHeals.load(File(outDir, "$prefix pc heals.txt"))
        // One KaizoCore PC per folder: a second one would answer the same requests and rename the same runs.
        val other = runCatching { parse(File(dir, "jar.txt").readText())["alive"]?.toLong() }.getOrNull()
        if (other != null && other >= System.currentTimeMillis() / 1000 - 3) {
            println(ALREADY_RUNNING)
            return false
        }
        if (!takeJarLock()) {
            println(ALREADY_RUNNING)
            return false
        }
        clean()
        stageDir.listFiles()?.forEach { it.delete() }
        log.writeText("")
        loadSeedIndex()
        say("KaizoCore PC: Heart & Soul, ${settings.nameWithoutExtension}, pool $pool, build %08X".format(PcHns.crc(build)))
        say("runs go to ${outDir.absolutePath} as \"$prefix AutoRandomized.gba\"; session ${dir.absolutePath}")
        say("New Run combo: $combo; attempts so far: $attempts")
        writeAttempts()
        Runtime.getRuntime().addShutdownHook(Thread { shutdown() })
        maker.start()
        // The favorites' name table loads on its first use (about a second): load it now, so the first box typed in
        // BizHawk's favorites window is answered at once instead of holding BizHawk while it loads.
        Thread({ runCatching { com.ironmonone.pcui.PcFavorites.suggest("a", natDex) } }, "kc-names").apply { isDaemon = true }.start()
        Thread({
            while (!stopping) { runCatching { heartbeat() }; Thread.sleep(250) }
        }, "kc-heartbeat").apply { isDaemon = true }.start()

        val req = File(dir, "lua.req")
        val ram = File(dir, "ram.bin")
        val quit = File(dir, "quit")
        val input = File(dir, "input.req")
        val want = File(dir, "panel.want")
        panel.onCycleMark = { sp, i -> loaded?.run?.cycleMark(sp, i) }
        panel.onOver = { a -> loaded?.let { onOver(it, a) } }
        panel.onEditFavorites = { editFavorites() }
        panel.onReroll = { loaded?.let { rerollBall(it) } }
        panel.onSettingsChanged = {
            loaded?.let { l ->
                // A theme or Auto Pokemon Themes changed: showPanel puts the card's colours on again at once.
                updatePlayAs(l)
                showPanel(l, force = true)
            }
        }
        panel.onPcHeal = { d -> loaded?.let { com.ironmonone.app.PcHeals.add(it.attempt, d); showPanel(it, force = true) } }
        // A left click on "(Leave a note)": the script opens the note window, as for a right click.
        panel.onNote = { loaded?.let { editNote(it) } }
        panel.screens.onNoteFor = { sp -> loaded?.let { editNote(it, sp) } }
        panel.onMove = { mv ->
            if (!mv.blank && mv.id > 0) {
                val name = mv.name.removeSuffix("*").trim()
                val text = loaded?.tracker?.moveDescription(mv.id) ?: "No description for this move."
                panel.inspect.show(name, text)
            }
        }
        panel.onAbility = { line ->
            val name = line.removeSuffix(" /").trim()
            if (name.isNotEmpty() && name != "?" && name != "---") {
                val id = loaded?.tracker?.abilityIdOf(name)
                val text = id?.let { loaded?.tracker?.abilityDescription(it) } ?: "No description for this ability."
                panel.inspect.show(name, text)
            }
        }
        panel.onGacha = {
            val l = loaded
            val p = l?.state?.let { it.lead ?: it.party.firstOrNull() }
            if (overlay.prizeOpen) {
                overlay.closePrize()
                say("gachamon card closed by the stars")
            } else if (l != null && p != null) {
                val g = com.ironmonone.pcui.PcGachaStars.viewCard(l.tracker, p, l.attempt)
                if (g != null) {
                    overlay.openPrize(g, com.ironmonone.pcui.PcPanel.sprite(p.mon.species, com.ironmonone.pcui.PcPanel.hnsOf(l.state)))
                    say("gachamon: ${g.name}, ${g.card.stars()} stars")
                }
            }
        }
        wireTyping()
        wireScreens()
        overlay.onClose = { overlay.close(); say("log viewer closed") }
        window?.let { w ->
            w.sheetDir = dir
            panel.window = w
            panel.setWindowScale(w.renderScale)
            w.input = object : TrackerWindow.Input {
                override fun click(x: Float, y: Float, right: Boolean) = post { windowClick(x, y, right) }
                override fun wheel(delta: Int) = post { windowWheel(delta) }
                override fun key(c: Char) = post { windowKey(c) }
                override fun rescaled() = post { if (panel.setWindowScale(w.renderScale)) loaded?.let { showPanel(it, force = true) } }
                override fun move(x: Float, y: Float) = post { windowMove(x, y) }
                override fun left() = post { loaded?.carousel?.paused = false; window?.setHand(false) }
            }
            // A change made while the session started (a size picked, a move to another screen) is not missed.
            post { panel.setWindowScale(w.renderScale) }
        }
        panel.warmUp()
        var lastLuaLook = 0L
        var lastTick = 0L
        var picturesSeen = com.ironmonone.pcui.PcPictures.ownStamp()
        while (!stopping) {
            var idle = true
            if (req.isFile) {
                idle = false
                lastContact = System.currentTimeMillis()
                val text = runCatching { req.readText() }.getOrNull()
                if (text != null) {
                    req.delete()
                    val m = parse(text)
                    val seq = m["seq"].orEmpty()
                    val answer = try {
                        when (m["cmd"]) {
                            "hello" -> hello(m)
                            "newrun" -> newRun()
                            // The favorites editor's drop-down: what the typed text offers.
                            "suggest" -> listOf("ok=1", "names=" + com.ironmonone.pcui.PcFavorites.suggest(PanelOut.unescape(m["text"].orEmpty()), natDex).joinToString("|"))
                            "bye" -> { loaded = null; panel.clear(); playAs.clear(); overlay.close(); say("BizHawk script closed"); events.changed(); listOf("ok=1") }
                            else -> listOf("ok=0", "why=unknown request")
                        }
                    } catch (e: Exception) {
                        say("request ${m["cmd"]}: $e")
                        listOf("ok=0", "why=" + (e.message ?: e.toString()).replace('\n', ' '))
                    }
                    if ((m["cmd"] != "hello" && m["cmd"] != "suggest") || answer.first() != "ok=1" || m["quiet"] != "1") say("${m["cmd"]} -> ${answer.joinToString(" ")}")
                    writeAtomic(File(dir, "jar.resp"), (listOf("seq=$seq") + answer).joinToString("\n", postfix = "\n").toByteArray())
                    afterAnswer?.let { afterAnswer = null; it() }
                }
            }
            if (ram.isFile) {
                idle = false; lastContact = System.currentTimeMillis()
                runCatching { takeRam(ram) }.onFailure { e ->
                    say("ram: $e")
                    runCatching { ram.delete() }
                    runCatching { File(ram.path + ".take").delete() }
                }
            }
            if (input.isFile) {
                idle = false; lastContact = System.currentTimeMillis()
                runCatching { takeInput(input) }.onFailure { say("input: $it"); runCatching { input.delete() } }
            }
            val abilityReq = File(dir, "ability.req")
            if (abilityReq.isFile) {
                idle = false; lastContact = System.currentTimeMillis()
                runCatching { takeAbility(abilityReq) }.onFailure { say("ability: $it"); runCatching { abilityReq.delete() } }
            }
            val more = File(dir, "input.req2")
            if (more.isFile) {
                idle = false; lastContact = System.currentTimeMillis()
                runCatching { takeInput(more) }.onFailure { say("input: $it"); runCatching { more.delete() } }
            }
            if (want.isFile) {
                // The script's window scale: the panel is drawn at it, 1:1 on BizHawk's client surface.
                val s = runCatching { parse(want.readText())["scale"]?.toFloat() }.getOrNull()
                want.delete()
                if (s != null && window != null) {
                    // The card has its own window: the scale is the game's, for the log viewer over it (2 at most).
                    overlay.setScale(s.coerceIn(1f, 2f))
                } else if (s != null && panel.setScale(s)) { overlay.setScale(panel.scale); loaded?.let { showPanel(it, force = true) } }
            }
            if (quit.isFile) { say("asked to stop"); break }
            while (true) { val w = posted.poll() ?: break; idle = false; runCatching { w() }.onFailure { say("posted work: $it") } }
            if (System.currentTimeMillis() - lastLuaLook > 250) { lastLuaLook = System.currentTimeMillis(); readLuaState() }
            // The strip turns on the clock, not on a read: ask the panel ten times a second; it draws only when what it
            // shows changed (PanelOut compares the input), so a still screen writes no new picture.
            if (System.currentTimeMillis() - lastTick > 1000) {
                lastTick = System.currentTimeMillis()
                // A picture put in or taken out of the pictures folder (by hand, in Explorer) shows within a second.
                val pics = com.ironmonone.pcui.PcPictures.ownStamp()
                if (pics != picturesSeen) { picturesSeen = pics; loaded?.let { showPanel(it, force = true) } }
                else loaded?.let { if (it.state != null) showPanel(it) }
            }
            panel.flush()
            // A click in the tracker window wakes the loop at once instead of waiting out the sleep.
            posted.poll(if (idle) 15L else 4L, java.util.concurrent.TimeUnit.MILLISECONDS)?.let { w -> runCatching { w() }.onFailure { say("posted work: $it") } }
        }
        shutdown()
        say("stopped")
        return true
    }

    companion object {
        /** Held by the running KaizoCore PC (takeJarLock); the script's way of knowing it is alive. */
        const val JAR_LOCK = "jar.lock"
        const val ALREADY_RUNNING = "KaizoCore PC is already running for this folder. Close the other window first."
        /** The standing files in the KaizoCore PC folder, one each for every run. */
        const val FAVORITES = "favorites.txt"
        const val OPTIONS = "tracker options.txt"
        const val THEME = "theme.txt"
        const val FAVORITES_HISTORY = "favorites history.txt"
        /** Play as your Pokemon's choices (the phone's prep/sprite-is-me.txt format) and the panel's pictures. */
        const val PLAY_AS = "play as.txt"
        const val PICTURES = "pictures.txt"
        /** In a run's folder: each Pokemon's GachaMon stars when it joined the run. */
        const val GACHA = "gachamon stars.txt"
    }
}

/** What a [Session] tells the status window. Both are called from the session's threads. */
interface SessionEvents {
    /** Something the window shows changed: the attempt, the runs ready, the game tracked. */
    fun changed() {}
    /** A line of the session's log. */
    fun log(line: String) {}

    companion object {
        val NONE = object : SessionEvents {}
    }
}

/** Copies the script's range list into EWRAM and IWRAM. Each range is a GBA address, a length, and the bytes. A zero address ends it. */
private fun placeRam(bytes: ByteArray, start: Int, ew: ByteArray, iw: ByteArray): Boolean {
    var i = start
    var any = false
    while (i + 4 <= bytes.size) {
        var addr = 0L
        for (b in 0 until 4) addr = addr or ((bytes[i + b].toLong() and 0xFF) shl (8 * b))
        i += 4
        if (addr == 0L) return any
        if (i + 2 > bytes.size) return false
        val len = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
        i += 2
        if (len < 0 || i + len > bytes.size) return false
        val dest = when (addr) {
            in 0x02000000L..0x0203FFFFL -> ew to (addr - 0x02000000L).toInt()
            in 0x03000000L..0x03007FFFL -> iw to (addr - 0x03000000L).toInt()
            else -> null
        }
        if (dest != null && dest.second >= 0 && dest.second + len <= dest.first.size) bytes.copyInto(dest.first, dest.second, i, i + len)
        any = true
        i += len
    }
    return any
}

/** ROM from the file, EWRAM/IWRAM from the latest snapshot. */
class BridgeReader(private val rom: ByteArray) : MemoryReader {
    @Volatile var ewram = ByteArray(0x40000)
    @Volatile var iwram = ByteArray(0x8000)
    override fun read(address: Long, length: Int): ByteArray {
        val (b, o) = when (address) {
            in 0x08000000L..0x09FFFFFFL -> rom to (address - 0x08000000L).toInt()
            in 0x02000000L..0x0203FFFFL -> ewram to (address - 0x02000000L).toInt()
            in 0x03000000L..0x03007FFFL -> iwram to (address - 0x03000000L).toInt()
            else -> return ByteArray(0)
        }
        if (o < 0 || o >= b.size) return ByteArray(0)
        return b.copyOfRange(o, minOf(b.size, o + length))
    }
}
