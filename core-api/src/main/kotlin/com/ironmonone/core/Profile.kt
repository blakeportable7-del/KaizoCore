package com.ironmonone.core

/**
 * The single place where core, tracker, engine, ROM and settings are wired together.
 *
 * The four v1 profiles are hard-separated (brief section 5). Cross-wiring a vanilla
 * engine onto a NatDex ROM, or a vanilla .rnqs onto NatDex, is not a bug to catch at
 * runtime; [validate] makes it unrepresentable before anything spawns.
 */
data class Profile(
    val id: String,
    val displayName: String,
    val generation: Generation,
    val romKind: RomKind,
    val coreId: String,
    val trackerId: String,
    val engineId: String,
    val sourceRom: RomFile?,
    val recipe: Recipe?,
    val outputRomUri: String?,
    val previousAttemptUri: String?,
) {
    val isNatDex: Boolean get() = romKind.isNatDex

    /**
     * Every reason this profile cannot run, in plain English. Empty means go.
     * The UI shows these verbatim; silent failure is banned.
     */
    fun validate(engine: RandomizerEngine, settings: Settings?): List<String> {
        val problems = mutableListOf<String>()

        if (engine.id != engineId) {
            problems += "This profile runs ${engineId}, but ${engine.id} was supplied."
        }
        if (!engine.accepts(romKind)) {
            problems += "${engine.displayName} cannot randomize ${romKind.displayName}."
        }
        if (settings != null) {
            if (settings.natDex != isNatDex) {
                problems += if (isNatDex) {
                    "\"${settings.displayName}\" is a vanilla settings file and this is a " +
                        "Nat. Dex ROM. That combination crashes the intro. Use a Nat. Dex " +
                        "settings file instead."
                } else {
                    "\"${settings.displayName}\" is a Nat. Dex settings file and this is a " +
                        "vanilla ROM. Use the official ${settings.gameTag} settings instead."
                }
            }
            if (!engine.accepts(settings)) {
                problems += "${engine.displayName} cannot read \"${settings.displayName}\". " +
                    "The settings file was made for a different randomizer version."
            }
        }
        if (sourceRom != null && !sourceRom.crcVerified &&
            sourceRom.kind.expectedCrc != RomKind.CRC_UNKNOWN
        ) {
            problems += "Your ${romKind.displayName} dump is not the revision this needs."
        }

        return problems
    }

    /** New Run requires a prepared ROM, an engine and settings, all valid. */
    fun canNewRun(engine: RandomizerEngine, settings: Settings?): Boolean =
        recipe != null && sourceRom != null && settings != null &&
            validate(engine, settings).isEmpty()
}
