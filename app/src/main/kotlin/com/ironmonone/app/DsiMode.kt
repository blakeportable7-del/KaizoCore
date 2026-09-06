package com.ironmonone.app

/**
 * DSi mode on melonDS DS needs the player's own DSi dumps in the system
 * folder, under the names the core looks for, plus a handful of options
 * set together. This is that bundle: the file list, whether it is
 * complete, and the option values that switch DSi on or back off. Nothing
 * here supplies a file; every one comes from the player's console.
 *
 * Known limit of the core in DSi mode: no save states (it says so), so the
 * auto-save, rewind and the slots do nothing there. The core's rule.
 */
object DsiMode {
    /** file name to what it is. All seven are required. */
    val FILES: List<Pair<String, String>> = listOf(
        "bios7.bin" to "DS ARM7 BIOS",
        "bios9.bin" to "DS ARM9 BIOS",
        "firmware.bin" to "DS firmware",
        "dsi_bios7.bin" to "DSi ARM7 BIOS",
        "dsi_bios9.bin" to "DSi ARM9 BIOS",
        "dsi_firmware.bin" to "DSi firmware",
        "dsi_nand.bin" to "DSi NAND (your console's own image)",
    )

    fun missing(present: (String) -> Boolean): List<String> = FILES.map { it.first }.filterNot(present)
    fun ready(present: (String) -> Boolean): Boolean = missing(present).isEmpty()

    fun isOn(values: Map<String, String>): Boolean = values["melonds_console_mode"] == "dsi"

    /** The option values that turn DSi mode on. Applied together; the core reads them at the next boot. */
    val ON: Map<String, String> = mapOf(
        "melonds_console_mode" to "dsi",
        "melonds_sysfile_mode" to "native",
        "melonds_boot_mode" to "direct",
        "melonds_dsi_nand_path" to "dsi_nand.bin",
        "melonds_firmware_dsi_path" to "dsi_firmware.bin",
        "melonds_firmware_nds_path" to "firmware.bin",
        "melonds_dsi_sdcard" to "enabled",
    )

    /** Back to a plain DS with the built-in system files. */
    val OFF: Map<String, String> = mapOf(
        "melonds_console_mode" to "ds",
        "melonds_sysfile_mode" to "builtin",
        "melonds_dsi_sdcard" to "disabled",
    )
}
