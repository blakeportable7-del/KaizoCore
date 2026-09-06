package com.ironmonone.app

import android.content.Context
import android.view.KeyEvent
import java.io.File

/**
 * Physical-input bindings: which key on a keyboard or controller drives which
 * console button.
 *
 * Kept as device-key -> console-key so a lookup during dispatch is one map hit.
 * The console side is expressed with the libretro/Android BUTTON_* codes the
 * core already understands, which is why the pad, the controller and the
 * keyboard all end up on the same path.
 */
class KeyBindings(private val file: File) {

    /** The console buttons a player can bind, in the order the screen lists them. */
    enum class Button(val label: String, val coreKey: Int) {
        UP("Up", KeyEvent.KEYCODE_DPAD_UP),
        DOWN("Down", KeyEvent.KEYCODE_DPAD_DOWN),
        LEFT("Left", KeyEvent.KEYCODE_DPAD_LEFT),
        RIGHT("Right", KeyEvent.KEYCODE_DPAD_RIGHT),
        A("A", KeyEvent.KEYCODE_BUTTON_A),
        B("B", KeyEvent.KEYCODE_BUTTON_B),
        L("L", KeyEvent.KEYCODE_BUTTON_L1),
        R("R", KeyEvent.KEYCODE_BUTTON_R1),
        START("Start", KeyEvent.KEYCODE_BUTTON_START),
        SELECT("Select", KeyEvent.KEYCODE_BUTTON_SELECT),
    }

    /**
     * Emulator actions a key or pad button can drive, beside the console
     * buttons: what Delta and Pizza Boy map to a controller. A key bound to
     * an action is taken BEFORE it reaches the core, so a pad button given
     * to Quick Save stops being a game button until unbound.
     */
    enum class Action(val label: String) {
        QUICK_SAVE("Quick save"), QUICK_LOAD("Quick load"), FAST_FORWARD("Fast forward (hold)"),
        SAVE_STATES("Open save states"), REWIND("Rewind (hold)"),
    }

    companion object {
        /** Process-wide snapshot of action bindings: device key -> action. */
        @Volatile var activeActions: Map<Int, Action> = emptyMap()
            private set

        /**
         * True only while the Play screen is showing and no text dialog is
         * open. dispatchKeyEvent consults this before eating a key for the
         * core; without it a hardware keyboard could not type anywhere in
         * the app because Z/X/arrows/Enter never reached the text fields.
         */
        @JvmStatic @Volatile var routeToGame: Boolean = false

        /**
         * Emulator-convention keyboard defaults. Also the fallback whenever a
         * button has no binding, so a fresh install is playable immediately and
         * a half-finished remap can never leave the game uncontrollable.
         */
        val DEFAULTS: Map<Button, Int> = mapOf(
            Button.UP to KeyEvent.KEYCODE_DPAD_UP,
            Button.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            Button.LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
            Button.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
            Button.A to KeyEvent.KEYCODE_X,
            Button.B to KeyEvent.KEYCODE_Z,
            Button.L to KeyEvent.KEYCODE_A,
            Button.R to KeyEvent.KEYCODE_S,
            Button.START to KeyEvent.KEYCODE_ENTER,
            Button.SELECT to KeyEvent.KEYCODE_SHIFT_RIGHT,
        )

        /** Controller buttons always work as themselves, remap or not. */
        val PAD_PASSTHROUGH: Set<Int> = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        )

        /** Readable key name for the remap screen. */
        fun keyName(code: Int): String = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> "Arrow Up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "Arrow Down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "Arrow Left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "Arrow Right"
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "Enter"
            KeyEvent.KEYCODE_SPACE -> "Space"
            KeyEvent.KEYCODE_SHIFT_LEFT -> "L-Shift"
            KeyEvent.KEYCODE_SHIFT_RIGHT -> "R-Shift"
            KeyEvent.KEYCODE_BACKSLASH -> "Backslash"
            KeyEvent.KEYCODE_TAB -> "Tab"
            else -> {
                val label = KeyEvent.keyCodeToString(code)
                    .removePrefix("KEYCODE_").replace('_', ' ')
                if (label.length == 1) label else label.lowercase()
                    .replaceFirstChar { it.uppercase() }
            }
        }

        /** Process-wide snapshot so dispatchKeyEvent needs no Context. */
        @Volatile
        var active: Map<Int, Int> = DEFAULTS.entries
            .associate { (button, key) -> key to button.coreKey }
            private set

        fun publish(map: Map<Int, Int>) { active = map }
    }

    private val bindings = LinkedHashMap<Button, Int>()
    private val actions = LinkedHashMap<Action, Int>()

    init { load() }

    private fun load() {
        bindings.clear()
        bindings.putAll(DEFAULTS)
        if (file.exists()) {
            runCatching {
                file.forEachLine { line ->
                    val parts = line.split('=')
                    if (parts.size == 2) {
                        val code = parts[1].trim().toIntOrNull()
                        if (parts[0].startsWith("ACTION_")) {
                            val a = Action.entries.firstOrNull { it.name == parts[0].removePrefix("ACTION_") }
                            if (a != null && code != null && code != 0) actions[a] = code
                        } else {
                            val button = Button.entries.firstOrNull { it.name == parts[0] }
                            if (button != null && code != null) bindings[button] = code
                        }
                    }
                }
            }
        }
        republish()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.bufferedWriter().use { w ->
                bindings.forEach { (b, code) -> w.write("${b.name}=$code"); w.newLine() }
                actions.forEach { (a, code) -> w.write("ACTION_${a.name}=$code"); w.newLine() }
            }
        }
        republish()
    }

    private fun republish() {
        // device key -> console key, which is the direction dispatch needs.
        publish(bindings.entries.associate { (button, key) -> key to button.coreKey })
        activeActions = actions.entries.associate { (a, key) -> key to a }
    }

    fun keyForAction(a: Action): Int? = actions[a]

    /** Bind [key] to an action. The key leaves any button or other action it drove. */
    fun bindAction(a: Action, key: Int) {
        bindings.entries.filter { it.value == key }.forEach { bindings[it.key] = 0 }
        actions.entries.filter { it.value == key && it.key != a }.map { it.key }.forEach { actions.remove(it) }
        actions[a] = key
        save()
    }

    fun unbindAction(a: Action) { actions.remove(a); save() }

    fun keyFor(button: Button): Int = bindings[button] ?: DEFAULTS.getValue(button)

    /**
     * Binds [key] to [button]. Any other button holding that key loses it, so a
     * key can never drive two buttons at once and quietly break the game.
     */
    fun bind(button: Button, key: Int) {
        bindings.entries.filter { it.value == key && it.key != button }
            .forEach { bindings[it.key] = 0 }
        actions.entries.filter { it.value == key }.map { it.key }.forEach { actions.remove(it) }
        bindings[button] = key
        save()
    }

    fun resetToDefaults() {
        bindings.clear()
        bindings.putAll(DEFAULTS)
        actions.clear()
        save()
    }

    fun all(): Map<Button, Int> = LinkedHashMap(bindings)
}
