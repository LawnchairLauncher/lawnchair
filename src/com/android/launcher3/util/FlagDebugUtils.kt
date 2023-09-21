package com.android.launcher3.util

import java.util.StringJoiner
import java.util.function.IntFunction

object FlagDebugUtils {

    /** Appends the [flagName] to [str] when the [flag] is set in [flags]. */
    @JvmStatic
    fun appendFlag(str: StringJoiner, flags: Int, flag: Int, flagName: String) {
        if (flags and flag != 0) {
            str.add(flagName)
        }
    }

    /**
     * Produces a human-readable representation of the [current] flags, followed by a diff from from
     * [previous].
     *
     * The resulting string is intented for logging and debugging.
     */
    @JvmStatic
    fun formatFlagChange(current: Int, previous: Int, flagSerializer: IntFunction<String>): String {
        val result = StringJoiner(" ")
        result.add("[" + flagSerializer.apply(current) + "]")
        val changed = current xor previous
        val added = current and changed
        if (added != 0) {
            result.add("+[" + flagSerializer.apply(added) + "]")
        }
        val removed = previous and changed
        if (removed != 0) {
            result.add("-[" + flagSerializer.apply(removed) + "]")
        }
        return result.toString()
    }
}
