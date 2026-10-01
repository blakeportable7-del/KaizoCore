package com.ironmonone.app

import java.io.File

/**
 * A run's mode in the words the rules keyed by name read (LossCondition.forSettingsName, PcHeals.limitFor, the IronMON
 * Journey ball picker): the game's family and the mode RnqsInfo reads from the settings file and its sidecar, as
 * "RBY Survival" or "RSE Kaizo Doubles" (2026-10-01, rules check; Blake: "base it on whatever game is doing a run").
 *
 * Those rules read the file's name alone, so a preset saved from the editor under a plain name, which keeps its mode
 * only in the sidecar, got the lead's game over and no heal limit, and Red, Blue and Yellow Survival's own rule held
 * only when "RBY" was in the name. A mode nothing can read leaves the name as it was.
 */
internal object RunModeName {
    fun of(settingsFile: File, family: String?): String {
        val info = runCatching { RnqsInfo.of(settingsFile) }.getOrNull()
        val mode = info?.ruleset ?: return settingsFile.name
        return listOfNotNull(family ?: info.gameTag, RnqsInfo.rulesetLabel(mode)).joinToString(" ")
    }

    /** The same for a settings file in [prepDir]'s settings folder, on the game whose id is [kindId] (lastrun.txt's first line). */
    fun ofPrep(prepDir: File, kindId: String?, settingsName: String): String =
        of(File(File(prepDir, "settings"), settingsName), kindId?.let { com.ironmonone.core.RomKind.byId(it) }?.family)
}
