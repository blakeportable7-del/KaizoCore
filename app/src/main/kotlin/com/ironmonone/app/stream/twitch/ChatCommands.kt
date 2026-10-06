package com.ironmonone.app.stream.twitch

/**
 * The chat commands and their answers, after the PC tracker's Stream Connect (besteon/Ironmon-Tracker,
 * network/EventHandler.lua and data/EventData.lua, read 2026-10-05). Its names are kept where it has the command
 * (!pokemon, !gachamon, !progress, !heals, !about, !help), and so is its answer shape: "Header > item | item | item",
 * at most a chat line long. !moves and !attempts are KaizoCore's, asked for by Blake; !mon and !commands are short names.
 *
 * Every answer is built from the stream page's own snapshot (StreamSnapshot, what StreamHub.state carries), which
 * already holds only what the phone's tracker shows: your Pokemon as the card draws it, with "Hide stats until summary
 * shown" applied, and never the opponent's real numbers. The answers add one rule of their own: they read your party
 * only. Nothing about the opponent, the seed, the log or the randomized game's data is ever looked up from chat.
 */
enum class ChatCommand(val word: String, val aliases: List<String>, val help: String) {
    POKEMON("!pokemon", listOf("!mon"), "[name] > Your lead Pokemon, or one in your party: level, types, ability and stats as the tracker shows them."),
    MOVES("!moves", emptyList(), "[name] > The moves of your lead Pokemon, or one in your party, as the tracker shows them."),
    ATTEMPTS("!attempts", emptyList(), "> The attempt number of this Kaizo IronMON run."),
    GACHAMON("!gachamon", emptyList(), "[name] > The GachaMon card of your lead Pokemon this run, or of a named one."),
    PROGRESS("!progress", emptyList(), "> Gym badges so far."),
    HEALS("!heals", emptyList(), "> The healing in the bag, as the tracker counts it."),
    ABOUT("!about", emptyList(), "> KaizoCore and the game being played."),
    HELP("!help", listOf("!commands"), "[command] > The list of commands, or what one does.");

    /** The short line the settings screen shows under the switch. */
    val label: String get() = (listOf(word) + aliases).joinToString(" or ")

    companion object {
        fun of(word: String): ChatCommand? {
            val w = word.lowercase().let { if (it.startsWith("!")) it else "!$it" }
            return entries.firstOrNull { it.word == w || w in it.aliases }
        }
    }
}

/** A command a chat line asked for, and what came after it (trimmed, at most [MAX_ARGS] characters). */
data class ChatAsk(val command: ChatCommand, val args: String) {
    companion object {
        const val MAX_ARGS = 40

        /** The command at the start of [text], or null when the line is not one of ours. */
        fun parse(text: String): ChatAsk? {
            val t = text.trim()
            if (!t.startsWith("!") || t.length > 200) return null
            val word = t.substringBefore(' ').substringBefore('\t')
            val cmd = ChatCommand.of(word) ?: return null
            val args = t.substring(word.length).trim().take(MAX_ARGS)
            return ChatAsk(cmd, args)
        }
    }
}

/**
 * Who may answer and when. Each command waits [cooldownMs] before it answers again (anyone asking), and all answers
 * together stay under [maxPerWindow] per [windowMs]. Twitch lets a broadcaster send 100 messages in 30 seconds and
 * anyone else 20; 8 in 30 keeps a busy chat from pushing the streamer's account anywhere near either.
 */
class ChatGate(
    private val clock: () -> Long = System::currentTimeMillis,
    val cooldownMs: Long = 10_000,
    val maxPerWindow: Int = 8,
    val windowMs: Long = 30_000,
) {
    private val last = HashMap<ChatCommand, Long>()
    private val sent = ArrayDeque<Long>()

    /** True and counted when [c] may answer now; false (and nothing counted) when it must stay quiet. */
    @Synchronized
    fun admit(c: ChatCommand): Boolean {
        val now = clock()
        while (sent.isNotEmpty() && now - sent.first() >= windowMs) sent.removeFirst()
        val prev = last[c]
        if (prev != null && now - prev < cooldownMs) return false
        if (sent.size >= maxPerWindow) return false
        last[c] = now
        sent.addLast(now)
        return true
    }
}

/** A GachaMon card as !gachamon prints it (the PC tracker's EventData.getGachaMon). */
class GachaFacts(
    val name: String,
    val ability: String,
    val stars: Int,
    val points: Int,
    val battlePower: Int,
    val level: Int,
    /** HP, Atk, Def, SpA, SpD, Spe. */
    val stats: List<Int>,
    val moves: List<String>,
    val shiny: Boolean,
)

/** What !gachamon found. */
sealed class GachaLookup {
    /** The card pack is on screen: the PC tracker answers "Please wait" until it is opened. */
    object PackOpening : GachaLookup()
    /** GachaMon cards are made only in a Kaizo IronMON run on a Game Boy Advance game. */
    object NotHere : GachaLookup()
    object NoCard : GachaLookup()
    class Card(val facts: GachaFacts) : GachaLookup()
}

/** The facts an answer needs beyond the snapshot. The app's reads GachaMon and the version; a test hands in its own. */
interface ChatFacts {
    /** The card for the lead (blank [name]) or for a Pokemon of this run named [name]. */
    fun gacha(name: String): GachaLookup
    val version: String
    fun enabled(c: ChatCommand): Boolean
}

object ChatAnswers {
    /** Twitch's limit for one chat message (Send Chat Message, 500 characters). */
    const val MAX_LENGTH = 500
    private const val NO_INFO = "No info found."
    private const val HIDDEN = "Shows once a summary has been opened this attempt."

    /** The answer to [ask] from [state], the stream snapshot's JSON. Never null, never longer than [MAX_LENGTH]. */
    fun answer(ask: ChatAsk, state: String, facts: ChatFacts): String {
        val s = JsonIn.obj(state) ?: emptyMap()
        val text = runCatching { build(ask, s, facts) }.getOrElse { "${title(ask.command)} > $NO_INFO" }
        return fit(text)
    }

    /** Cut to a chat line, and never starting with "!", so an answer is never read back as a command. */
    internal fun fit(text: String): String {
        val one = text.replace('\n', ' ').replace('\r', ' ')
        val safe = if (one.startsWith("!")) "Command: $one" else one
        return if (safe.length <= MAX_LENGTH) safe else safe.take(MAX_LENGTH - 3).trimEnd() + "..."
    }

    private fun title(c: ChatCommand) = when (c) {
        ChatCommand.POKEMON -> "Pokemon"
        ChatCommand.MOVES -> "Moves"
        ChatCommand.ATTEMPTS -> "Attempts"
        ChatCommand.GACHAMON -> "GachaMon"
        ChatCommand.PROGRESS -> "Progress"
        ChatCommand.HEALS -> "Heals"
        ChatCommand.ABOUT -> "KaizoCore"
        ChatCommand.HELP -> "Tracker Commands"
    }

    private fun build(ask: ChatAsk, s: Map<String, Any?>, facts: ChatFacts): String {
        val c = ask.command
        return when (c) {
            ChatCommand.HELP -> help(ask.args, facts)
            ChatCommand.ABOUT -> about(s, facts)
            ChatCommand.ATTEMPTS -> attempts(s)
            ChatCommand.PROGRESS -> progress(s)
            ChatCommand.HEALS -> heals(s)
            ChatCommand.POKEMON -> pokemon(s, ask.args)
            ChatCommand.MOVES -> moves(s, ask.args)
            ChatCommand.GACHAMON -> gacha(s, ask.args, facts)
        }
    }

    // ------------------------------------------------------------------ the snapshot

    private fun playing(s: Map<String, Any?>) = s["app"] == "KaizoCore"
    private fun isRun(s: Map<String, Any?>) = s["run"] == true

    @Suppress("UNCHECKED_CAST")
    private fun party(s: Map<String, Any?>): List<Map<String, Any?>> = (s["party"] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

    /** The Pokemon on the tracker's card: in a double battle the one the phone shows ("own"), else the party's first. */
    private fun lead(s: Map<String, Any?>): Map<String, Any?>? = JsonIn.asObj(s["own"]) ?: party(s).firstOrNull()

    /** "Hide stats until summary shown", as the snapshot carries it: the lead's stats are withheld. */
    private fun hidden(s: Map<String, Any?>): Boolean {
        val l = lead(s) ?: return false
        return l.containsKey("stats") && l["stats"] == null
    }

    private fun squash(t: String) = t.lowercase().filter { it.isLetterOrDigit() }

    /** The lead, or the party member called [name]; null when the name is not in the party. */
    private fun pick(s: Map<String, Any?>, name: String): Map<String, Any?>? {
        if (name.isBlank()) return lead(s)
        val want = squash(name)
        if (want.isEmpty()) return lead(s)
        return party(s).firstOrNull { squash(it["name"]?.toString().orEmpty()) == want }
    }

    private fun num(v: Any?): Long? = when (v) { is Long -> v; is Int -> v.toLong(); is Double -> v.toLong(); is String -> v.toLongOrNull(); else -> null }

    private fun types(m: Map<String, Any?>): String =
        (m["types"] as? List<*>)?.mapNotNull { it?.toString()?.takeIf { t -> t.isNotBlank() } }?.joinToString("/").orEmpty()

    private fun header(m: Map<String, Any?>): String {
        val t = types(m)
        return "${m["name"]} Lv.${num(m["level"]) ?: "?"}" + if (t.isNotEmpty()) " ($t)" else ""
    }

    private fun nothingPlaying(c: ChatCommand) = "${title(c)} > Nothing is being played right now."

    // ------------------------------------------------------------------ the commands

    private fun pokemon(s: Map<String, Any?>, name: String): String {
        if (!playing(s)) return nothingPlaying(ChatCommand.POKEMON)
        if (party(s).isEmpty()) return "Pokemon > No Pokemon on the tracker yet."
        val m = pick(s, name) ?: return "Pokemon > Only your own party can be looked up."
        val info = ArrayList<String>()
        if (hidden(s)) {
            num(m["bst"])?.takeIf { it > 0 }?.let { info += "BST: $it" }
            (m["evolution"] as? String)?.takeIf { it.isNotBlank() }?.let { info += "Evo: $it" }
            info += "Stats, ability and moves: $HIDDEN"
            return "${header(m)} > " + info.joinToString(" | ")
        }
        val hp = num(m["hp"]); val maxHp = num(m["maxHp"])
        if (hp != null && maxHp != null) info += "HP: $hp/$maxHp"
        (m["ability"] as? String)?.takeIf { it.isNotBlank() }?.let { info += "Ability: $it" }
        (m["item"] as? String)?.takeIf { it.isNotBlank() }?.let { info += "Item: $it" }
        JsonIn.asObj(m["stats"])?.let { st ->
            val parts = listOf("HP" to "hp", "Atk" to "atk", "Def" to "def", "SpA" to "spa", "SpD" to "spd", "Spe" to "spe")
                .mapNotNull { (label, k) -> num(st[k])?.let { "$label $it" } }
            if (parts.isNotEmpty()) info += "Stats: " + parts.joinToString(", ")
        }
        num(m["bst"])?.takeIf { it > 0 }?.let { info += "BST: $it" }
        (m["status"] as? String)?.takeIf { it.isNotBlank() }?.let { info += "Status: $it" }
        (m["evolution"] as? String)?.takeIf { it.isNotBlank() }?.let { info += "Evo: $it" }
        return "${header(m)} > " + info.ifEmpty { listOf(NO_INFO) }.joinToString(" | ")
    }

    private fun moves(s: Map<String, Any?>, name: String): String {
        if (!playing(s)) return nothingPlaying(ChatCommand.MOVES)
        if (party(s).isEmpty()) return "Moves > No Pokemon on the tracker yet."
        val m = pick(s, name) ?: return "Moves > Only your own party can be looked up."
        val head = "${m["name"]} moves >"
        if (hidden(s)) return "$head $HIDDEN"
        val rows = (m["moves"] as? List<*>)?.mapNotNull { JsonIn.asObj(it) }.orEmpty()
        if (rows.isEmpty()) return "$head None known."
        val list = rows.map { r ->
            val bits = ArrayList<String>()
            (r["type"] as? String)?.takeIf { it.isNotBlank() && it != "?" }?.let { bits += it }
            (r["category"] as? String)?.takeIf { it.isNotBlank() }?.let { bits += it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            val power = shown(r["power"]); val acc = shown(r["acc"])
            val pp = num(r["pp"]); val ppMax = num(r["ppMax"])
            val numbers = listOf("Power $power", "Acc $acc") + (if (pp != null) listOf("PP " + (if (ppMax != null) "$pp/$ppMax" else "$pp")) else emptyList())
            "${r["name"]}" + (if (bits.isNotEmpty()) " [${bits.joinToString(", ")}]" else "") + " " + numbers.joinToString(", ")
        }.toMutableList()
        num(m["nextMoveLevel"])?.takeIf { it > 0 }?.let { list += "Next move at Lv.$it" }
        return "$head " + list.joinToString(" | ")
    }

    /** A power or accuracy as the card prints it: "-" for none. */
    private fun shown(v: Any?): String {
        val t = v?.toString()?.trim().orEmpty()
        return if (t.isEmpty() || t == "0" || t == "null") "-" else t
    }

    private fun attempts(s: Map<String, Any?>): String {
        if (!playing(s) || !isRun(s)) return "Attempts > Not in a Kaizo IronMON run right now."
        return "Attempts > ${num(s["attempt"]) ?: "?"}" + ((s["title"] as? String)?.takeIf { it.isNotBlank() }?.let { " | Game: $it" } ?: "")
    }

    private fun progress(s: Map<String, Any?>): String {
        if (!playing(s)) return nothingPlaying(ChatCommand.PROGRESS)
        val bits = Integer.bitCount((num(s["badges"]) ?: 0L).toInt() and 0xFF)
        return "Progress > Gym badges: $bits/8"
    }

    private fun heals(s: Map<String, Any?>): String {
        if (!playing(s)) return nothingPlaying(ChatCommand.HEALS)
        if (hidden(s)) return "Heals > $HIDDEN"
        val h = JsonIn.asObj(s["heals"]) ?: return "Heals > $NO_INFO"
        val pct = num(h["percent"]) ?: 0L; val count = num(h["count"]) ?: 0L
        return "Heals > $pct% HP in the bag | $count healing item${if (count == 1L) "" else "s"}"
    }

    private fun about(s: Map<String, Any?>, facts: ChatFacts): String {
        val info = ArrayList<String>()
        info += "Version: ${facts.version}"
        if (playing(s)) (s["title"] as? String)?.takeIf { it.isNotBlank() }?.let { info += "Game: $it" }
        if (playing(s) && isRun(s)) num(s["attempt"])?.let { info += "Attempts: $it" }
        return "KaizoCore > " + info.joinToString(" | ")
    }

    private fun help(args: String, facts: ChatFacts): String {
        val on = ChatCommand.entries.filter { facts.enabled(it) }
        if (args.isNotBlank()) {
            val c = ChatCommand.of(args.substringBefore(' ')) ?: return "Command: > $NO_INFO"
            if (!facts.enabled(c)) return "Command: > That one is turned off."
            return "Command: > ${c.word} ${c.help}"
        }
        return "Tracker Commands > " + on.joinToString(", ") { it.word }
    }

    private fun gacha(s: Map<String, Any?>, name: String, facts: ChatFacts): String {
        // A card holds its Pokemon's real stats, ability and moves: hidden on the tracker, hidden here.
        if (hidden(s)) return "GachaMon > $HIDDEN"
        return when (val g = facts.gacha(name)) {
            GachaLookup.PackOpening -> "GachaMon > Please wait until the GachaMon card pack is opened."
            GachaLookup.NotHere -> "GachaMon > Cards are made in a Kaizo IronMON run on a Game Boy Advance game."
            GachaLookup.NoCard -> "GachaMon > " + if (name.isBlank()) "No card for the lead this run yet." else NO_INFO
            is GachaLookup.Card -> {
                val f = g.facts
                val shown = if (f.shiny) "* ${f.name} *" else f.name
                val info = listOf(
                    "$shown - ${f.ability}",
                    "${if (f.stars > 5) "5+" else f.stars.toString()} Stars (${f.points} Points)",
                    "${f.battlePower} BP",
                    "Lv.${f.level} Stats: " + f.stats.joinToString("/"),
                ) + listOfNotNull(f.moves.filter { it.isNotBlank() && it != "---" }.joinToString(", ").takeIf { it.isNotEmpty() })
                "GachaMon > " + info.joinToString(" | ")
            }
        }
    }
}
