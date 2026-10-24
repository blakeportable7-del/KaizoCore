package com.ironmonone.pc

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JOptionPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants

/**
 * KaizoCore PC's small window, in place of the prototype's console: what it is doing, the attempt, Open folder and Quit,
 * and the update notice when a newer release is out. Favorites sets the one list every run uses, before any run too;
 * New Run asks BizHawk's script to open the next run, which works with no game open in BizHawk (where the buttons of
 * the combo cannot be read). Closing it stops KaizoCore PC (BizHawk keeps running and says so).
 * The session runs on its own thread; the window reads its state twice a second and changes nothing in it but Quit.
 */
class StatusWindow private constructor(private val args: List<String>, private val dir: File) {
    companion object {
        fun open(args: List<String>, checkUpdates: Boolean, dir: File = jarDir()) {
            SwingUtilities.invokeLater {
                runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
                val w = StatusWindow(args, dir)
                w.show()
                w.start()
                if (checkUpdates) Thread({ Updates.check()?.let { n -> SwingUtilities.invokeLater { w.showUpdate(n) } } }, "kc-updates")
                    .apply { isDaemon = true }.start()
            }
        }

        const val WAITING = "Waiting for BizHawk. In BizHawk open Tools, then Lua Console, and open kaizocore-hns.lua from this folder."
        const val MAKING = "Making the first runs. This takes a few seconds."
        const val STARTING = "Starting."

        const val NO_GAME = "BizHawk is connected and no Heart & Soul game is open. Press New Run here to open the next run."

        /** The status line for what the session reports; [game] is what BizHawk's script says is open (none, other, hns). */
        fun statusLine(ready: Int, error: String?, connected: Boolean, tracking: String?, game: String? = null): String {
            val runs = when {
                error != null -> "The next run could not be made: $error"
                ready == 0 -> MAKING
                ready == 1 -> "Ready. 1 run is made ahead."
                else -> "Ready. $ready runs are made ahead."
            }
            val bizhawk = when {
                connected && tracking != null -> "BizHawk is connected and playing $tracking. A new seed is Start + A + B."
                connected && game != null && game != "hns" -> NO_GAME
                connected -> "BizHawk is connected. Load a Heart & Soul run, or press your New Run buttons."
                else -> WAITING
            }
            return "$runs\n$bizhawk"
        }
    }

    private val frame = JFrame("KaizoCore PC")
    private val status = JTextArea(STARTING, 4, 36)
    private val attempt = JLabel(" ")
    private val update = JLabel(" ")
    private val updateButton = JButton("Open page")
    private val twitch = JTextArea(" ", 2, 36)
    private val twitchButton = JButton("Connect Twitch")
    @Volatile private var chat: PcChat? = null
    private val retry = JButton("Try again")
    private val newRun = JButton("New Run")
    private val resetAttempts = JButton("Reset attempts to 0")
    private val continueAttempt = JButton("Continue Attempt")
    private val favorites = JButton("Favorites")
    private val note = JLabel(" ")
    @Volatile private var session: Session? = null
    @Volatile private var sessionThread: Thread? = null
    private var timer: Timer? = null
    /** The tracker card's own window, open from the start (it says it is waiting until BizHawk's script connects). */
    private var card: TrackerWindow? = null

    private fun show() {
        if ("--card-in-bizhawk" !in args) card = runCatching { TrackerWindow(dir) }.getOrNull()
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.addWindowListener(object : WindowAdapter() { override fun windowClosing(e: WindowEvent) = quit() })
        val body = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(12, 14, 8, 14)
        }
        val title = JLabel("KaizoCore PC: Heart & Soul in BizHawk").apply { font = font.deriveFont(Font.BOLD, 14f) }
        val version = JLabel("${PcVersion.version} (${PcVersion.commit})").apply { foreground = Color.GRAY }
        attempt.font = attempt.font.deriveFont(Font.BOLD, 20f)
        status.isEditable = false
        status.lineWrap = true
        status.wrapStyleWord = true
        status.isOpaque = false
        status.border = BorderFactory.createEmptyBorder(8, 0, 8, 0)
        status.font = JLabel().font.deriveFont(13f)
        update.foreground = Color(0x1D, 0x5F, 0x9E)
        updateButton.isVisible = false
        retry.isVisible = false
        twitch.isEditable = false
        twitch.lineWrap = true
        twitch.wrapStyleWord = true
        twitch.isOpaque = false
        twitch.font = JLabel().font.deriveFont(12f)
        twitch.isVisible = false
        twitchButton.isVisible = false
        twitchButton.addActionListener {
            val c = chat ?: return@addActionListener
            if (c.signedIn) c.link.signOut() else c.link.signIn()
        }
        newRun.isEnabled = false
        resetAttempts.isEnabled = false
        favorites.isEnabled = false
        continueAttempt.isEnabled = false
        continueAttempt.font = continueAttempt.font.deriveFont(Font.BOLD, 18f)
        continueAttempt.alignmentX = 0f
        continueAttempt.maximumSize = Dimension(2000, 52)
        continueAttempt.addActionListener {
            val s = session ?: return@addActionListener
            note.text = s.requestContinue()
            frame.pack()
        }
        newRun.addActionListener {
            val s = session ?: return@addActionListener
            val ok = JOptionPane.showConfirmDialog(
                frame,
                "This ends the current attempt and opens the next run.",
                "New Run",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
            if (ok != JOptionPane.OK_OPTION) return@addActionListener
            note.text = s.requestNewRun()
            frame.pack()
        }
        resetAttempts.addActionListener {
            val s = session ?: return@addActionListener
            val ok = JOptionPane.showConfirmDialog(
                frame,
                "Set the attempt count back to zero? The next New Run will be attempt 1. Past run folders stay where they are.",
                "Reset attempts",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
            if (ok != JOptionPane.OK_OPTION) return@addActionListener
            s.resetAttempts()
        }
        favorites.addActionListener { session?.let { FavoritesDialog(frame, it).isVisible = true } }
        for (c in listOf(title, version, attempt, status, note, update, twitch)) { (c as javax.swing.JComponent).alignmentX = 0f; body.add(c) }
        body.add(Box.createVerticalStrut(10))
        body.add(continueAttempt)
        val newSeed = JLabel("New seed in BizHawk: Start + A + B").apply {
            font = font.deriveFont(Font.BOLD, 14f)
            alignmentX = 0f
        }
        body.add(Box.createVerticalStrut(6))
        body.add(newSeed)
        body.add(Box.createVerticalStrut(4))
        card?.let { w ->
            val sizes = arrayOf("Small", "Medium", "Large", "Extra large")
            val pick = javax.swing.JComboBox(sizes).apply {
                selectedIndex = (w.size - TrackerWindow.MIN_SIZE).coerceIn(0, sizes.size - 1)
                maximumSize = preferredSize
                addActionListener { w.setSize(selectedIndex + TrackerWindow.MIN_SIZE) }
            }
            val showCard = JButton("Show tracker").apply { addActionListener { w.bringBack() } }
            // On top of BizHawk and every other window, unless this is unticked (kept in tracker window.txt).
            val onTop = javax.swing.JCheckBox("Keep tracker on top", w.pinned).apply { addActionListener { w.setPinned(isSelected) } }
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                alignmentX = 0f
                add(JLabel("Tracker size:")); add(pick); add(showCard); add(onTop)
                maximumSize = Dimension(2000, preferredSize.height)
            }
            body.add(Box.createVerticalStrut(6))
            body.add(row)
            body.add(JLabel("For OBS, add a Window Capture of \"${TrackerWindow.TITLE}\".").apply { foreground = Color.GRAY; alignmentX = 0f })
            body.add(Box.createVerticalStrut(4))
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4))
        val openFolder = JButton("Open folder")
        val quit = JButton("Quit")
        openFolder.addActionListener { runCatching { Desktop.getDesktop().open(dir) } }
        quit.addActionListener { quit() }
        retry.addActionListener { retry.isVisible = false; start() }
        buttons.add(newRun); buttons.add(resetAttempts); buttons.add(favorites); buttons.add(updateButton); buttons.add(twitchButton); buttons.add(retry); buttons.add(openFolder); buttons.add(quit)
        frame.contentPane.add(body, BorderLayout.CENTER)
        frame.contentPane.add(buttons, BorderLayout.SOUTH)
        frame.minimumSize = Dimension(460, 250)
        frame.pack()
        frame.setLocationByPlatform(true)
        frame.isVisible = true
    }

    /** Prepares the session off the Swing thread (finding the game, patching it the first time) and runs it. */
    private fun start() {
        status.text = STARTING
        sessionThread = Thread({
            when (val s = prepare(args, dir, events = object : SessionEvents {
                override fun changed() = SwingUtilities.invokeLater { refresh() }
            }, say = { line -> SwingUtilities.invokeLater { status.text = line } }, window = card)) {
                is Start.Problem -> SwingUtilities.invokeLater { status.text = s.why; retry.isVisible = true; frame.pack() }
                is Start.Ready -> {
                    session = s.session
                    if (chat == null) chat = startChat(dir, { session }, { line -> SwingUtilities.invokeLater { status.text = line } })
                    SwingUtilities.invokeLater {
                        twitch.isVisible = chat != null
                        twitchButton.isVisible = chat != null
                        newRun.isEnabled = true
                        resetAttempts.isEnabled = true
                        favorites.isEnabled = true
                        refresh()
                        frame.pack()
                        timer = Timer(500) { refresh() }.apply { start() }
                    }
                    val ran = s.session.run()
                    if (!ran) SwingUtilities.invokeLater { status.text = Session.ALREADY_RUNNING; timer?.stop() }
                }
            }
        }, "kc-session").apply { isDaemon = true; start() }
    }

    private fun refresh() {
        val s = session ?: return
        val connected = System.currentTimeMillis() - s.lastContact < 5_000
        attempt.text = "Attempt: ${s.attempt}"
        val cont = s.attemptToContinue
        continueAttempt.text = if (cont != null) "Continue Attempt $cont" else "Continue Attempt"
        continueAttempt.isEnabled = cont != null
        status.text = statusLine(s.readyRuns, s.makeError, connected, s.tracking, s.scriptGame.takeIf { s.scriptRunning })
        chat?.let { c ->
            val line = c.statusLine()
            if (twitch.text != line) { twitch.text = line; frame.pack() }
            val want = if (c.signedIn) "Sign out of Twitch" else "Connect Twitch"
            if (twitchButton.text != want) twitchButton.text = want
        }
    }

    private fun showUpdate(n: Updates.Notice) {
        update.text = "KaizoCore ${n.version} is out."
        updateButton.isVisible = true
        updateButton.addActionListener { runCatching { Desktop.getDesktop().browse(URI(n.page)) } }
        frame.pack()
    }

    private fun quit() {
        timer?.stop()
        val s = session
        frame.isVisible = false
        card?.close()
        Thread({
            chat?.shutdown()
            s?.stop()
            // The session's loop notices within a few milliseconds and cleans its folder (the run being made may take a
            // second to finish); then KaizoCore PC exits.
            runCatching { sessionThread?.join(8_000) }
            frame.dispose()
            System.exit(0)
        }, "kc-quit").start()
    }
}

/**
 * The favorites, set from KaizoCore PC's window: one box for each favorite the runs count (nine, or three for a Vanilla
 * pool), and beside each the names that match what is typed (the app's Favorites.suggest, as BizHawk's editor and
 * `!fav` match them); picking one fills the box. Save keeps them as the one list every run uses, with or without a
 * game open.
 */
class FavoritesDialog(owner: JFrame, private val session: Session) : javax.swing.JDialog(owner, "KaizoCore favorites", true) {
    init {
        val rows = session.favoritesShown
        val now = session.favoritesNow()
        val boxes = (0 until rows).map { javax.swing.JTextField(now.getOrElse(it) { "" }, 16) }
        val lists = (0 until rows).map { javax.swing.JComboBox<String>() }
        val grid = JPanel(java.awt.GridLayout(rows, 3, 6, 4)).apply { border = BorderFactory.createEmptyBorder(10, 12, 6, 12) }
        for (i in 0 until rows) {
            val box = boxes[i]
            val list = lists[i]
            var filling = false
            fun offer() {
                filling = true
                val names = box.text.trim().takeIf { it.isNotEmpty() }?.let { session.suggestFavorites(it) }.orEmpty()
                list.model = javax.swing.DefaultComboBoxModel(names.toTypedArray())
                list.selectedIndex = -1
                filling = false
            }
            box.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent) = SwingUtilities.invokeLater { offer() }
                override fun removeUpdate(e: javax.swing.event.DocumentEvent) = SwingUtilities.invokeLater { offer() }
                override fun changedUpdate(e: javax.swing.event.DocumentEvent) {}
            })
            list.addActionListener { if (!filling) (list.selectedItem as? String)?.let { if (it != box.text) box.text = it } }
            grid.add(JLabel("${i + 1}")); grid.add(box); grid.add(list)
        }
        val save = JButton("Save")
        val cancel = JButton("Cancel")
        save.addActionListener {
            val bad = boxes.map { it.text.trim() }.filter { it.isNotEmpty() && !session.isFavoriteName(it) }
            if (bad.isNotEmpty()) {
                javax.swing.JOptionPane.showMessageDialog(this, "Not a Pokemon in this run: ${bad.joinToString(", ")}.\nPick a name from the list beside the box.",
                    "KaizoCore favorites", javax.swing.JOptionPane.WARNING_MESSAGE)
                return@addActionListener
            }
            session.saveFavorites(boxes.map { it.text }); dispose()
        }
        cancel.addActionListener { dispose() }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4)).apply { add(save); add(cancel) }
        contentPane.add(grid, BorderLayout.CENTER)
        contentPane.add(buttons, BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(owner)
    }
}
