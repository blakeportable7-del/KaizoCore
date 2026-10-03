package com.ironmonone.app

import com.ironmonone.tracker.GbaTracker

/**
 * The Gen 3 tracker's rival, kept with the run (rc32 audit P2 #130). The reference saves Tracker.Data.whichRival with the
 * run's tracked data and loads it back (Tracker.lua:362-371, :612, :632); the tracker here is made again on every visit
 * to Play, so a rival it learned is written to the run's StatMarks, and a tracker that has not learned one is given the
 * kept one back. Only a rival a battle taught this tracker is written (GbaTracker.takeLearnedRival), never one it was
 * given: a tracker still up when New Run clears the marks must not carry the last run's rival into the next.
 *
 * Called on the main thread, where StatMarks is written, from SideScreenDialogs on every poll it is drawn for.
 */
internal object TrackerRival {
    fun sync(tracker: GbaTracker?, marks: StatMarks) {
        val t = tracker ?: return
        t.takeLearnedRival()?.let { marks.noteGbaRival(it) }
        if (t.rivalChoice == null) t.restoreRival(marks.gbaRival())
    }
}
