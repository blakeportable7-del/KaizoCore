package com.ironmonone.app

/**
 * Small helpers the settings editor and RUN share: the settings string as the
 * desktop writes it, preset names that keep the tokens the Run tab pairs on,
 * and plain sentences for failures.
 */
object PresetStrings {

    /**
     * The game the editor was opened for, set by RUN just before it opens the
     * editor: the family tag ("FRLG") and whether it is a Nat. Dex build. A
     * preset with no tags in its own name gets these in its suggested name
     * and sidecar, so it pairs with the game it was made on.
     */
    @Volatile var targetFamily: String? = null
    @Volatile var targetNatDex: Boolean? = null

    /** True for the Nat. Dex fork's Settings class, false for ZX. */
    fun isNatDexClass(cls: Class<*>): Boolean = cls == com.dabomstew.pkrandom.Settings::class.java

    /**
     * The settings string the way the desktop randomizer copies it: its
     * three-digit VERSION, then the base64 body (NewRandomizerGUI ~1318,
     * `Version.VERSION + settings.toString()`). COPY put the bare body on the
     * clipboard, which our own PASTE and the desktop both refuse
     * (2026-09-27, audit). The version is the one of the engine that READ the
     * file: 908 for the Nat. Dex fork, 322 for ZX.
     */
    fun copyString(settings: Any, cls: Class<*>): String {
        val version = if (isNatDexClass(cls)) com.dabomstew.pkrandom.Version.VERSION
            else com.dabomstew.pkrandomzx.Version.VERSION
        return "$version$settings"
    }

    /**
     * A name for a preset derived from [base]: the game tag, "NatDex" and the
     * ruleset stay in front, because the Run tab reads them from the name
     * (RnqsInfo), and the player's part goes in brackets after them.
     * "FRLG NatDex v1.2 Kaizo" -> "FRLG NatDex v1.2 Kaizo (my edit)".
     */
    fun suggestedName(base: RnqsInfo?, natDex: Boolean, suffix: String, family: String? = null): String {
        val stem = base?.fileName?.removeSuffix(".rnqs")?.removeSuffix(".RNQS")
        // The file's own name already carries the tokens: keep all of it.
        if (stem != null && RnqsInfo.parse(base.fileName).gameTag != null) return "$stem ($suffix)"
        val tag = base?.gameTag ?: family ?: targetFamily
        val parts = listOfNotNull(
            tag,
            "NatDex".takeIf { natDex },
            base?.ruleset?.let { RnqsInfo.rulesetLabel(it) },
        )
        return if (parts.isEmpty()) suffix.replaceFirstChar { it.uppercase() }
        else parts.joinToString(" ") + " ($suffix)"
    }

    /**
     * A failure as a sentence a player can act on. Raw exception text
     * ("Save failed: null", "Malformed input") told nobody anything
     * (2026-09-27, audit); the detail goes to the log instead.
     */
    fun plain(t: Throwable, whatFailed: String): String {
        runCatching { android.util.Log.w("IronMonOne", whatFailed, t) }
        val msg = generateSequence(t) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return when {
            "newer randomizer" in msg -> "$whatFailed: it is from a newer randomizer than this app carries."
            "ENOSPC" in msg || "No space" in msg -> "$whatFailed: the phone is out of storage."
            "did not read back" in msg -> "$whatFailed: the saved file did not read back the same, so it was not kept."
            t is IllegalArgumentException && t.message != null && t.message!!.endsWith(".") -> t.message!!
            else -> "$whatFailed. Try again."
        }
    }
}
