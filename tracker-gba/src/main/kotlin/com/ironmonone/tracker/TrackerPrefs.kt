package com.ironmonone.tracker

/**
 * The tracker settings the trackers themselves need. The app's TrackerOptions
 * owns and saves them and mirrors each change here, because this module cannot
 * see app code.
 */
object TrackerPrefs {
    /** Options["Determine friendship readiness"]: on by default in the reference (Options.lua). */
    @Volatile var determineFriendship: Boolean = true
}
