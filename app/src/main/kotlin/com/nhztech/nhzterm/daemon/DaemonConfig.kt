package com.nhztech.nhzterm.daemon

/**
 * Operational specs & limits — concept doc §8. Every constant here is a
 * spec value, not a tuning knob; changing one means changing the spec.
 */
object DaemonConfig {
    /** §8: max 15 concurrent sessions — past this, SESSION_LIMIT_REACHED. */
    const val MAX_SESSIONS = 15

    /** §8: ~5,000 lines of scrollback per session, ring buffer. */
    const val SCROLLBACK_LINES = 5000

    /**
     * Integration Point (build plan Part 2): the daemon's default shell is
     * the real nhzsh, resolved to the staged system/bin/nhzsh path. Until
     * nhzsh is staged, sessions honestly fall back to the placeholder sh.
     */
    const val DEFAULT_SHELL = "nhzsh"
    const val FALLBACK_SHELL = "/system/bin/sh"

    /** §8: wake lock is opt-in, OFF by default — battery cost must be a choice. */
    const val WAKE_LOCK_DEFAULT = false

    const val CHANNEL_ID = "nhztermd"
    const val NOTIFICATION_ID = 0x4e485a // "NHZ"

    /** Initial PTY geometry until the client resizes. */
    const val DEFAULT_COLS = 80
    const val DEFAULT_ROWS = 24
}
