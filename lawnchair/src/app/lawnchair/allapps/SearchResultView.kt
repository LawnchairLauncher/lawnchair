package app.lawnchair.allapps

import android.os.Bundle
import app.lawnchair.search.SearchTargetCompat

sealed interface SearchResultView {

    val isQuickLaunch: Boolean
    val titleText: CharSequence? get() = null

    fun launch(): Boolean

    fun bind(target: SearchTargetCompat, shortcuts: List<SearchTargetCompat>)

    fun getFlags(extras: Bundle): Int {
        var flags = 0
        if (extras.getBoolean(EXTRA_HIDE_SUBTITLE, false)) {
            flags = flags or FLAG_HIDE_SUBTITLE
        }
        if (extras.getBoolean(EXTRA_HIDE_ICON, false)) {
            flags = flags or FLAG_HIDE_ICON
        }
        if (extras.getBoolean(EXTRA_QUICK_LAUNCH, false)) {
            flags = flags or FLAG_QUICK_LAUNCH
        }
        return flags
    }

    fun hasFlag(flags: Int, flag: Int): Boolean {
        return (flags and flag) != 0
    }

    companion object {
        const val FLAG_HIDE_SUBTITLE = 1 shl 0
        const val FLAG_HIDE_ICON = 1 shl 1
        const val FLAG_QUICK_LAUNCH = 1 shl 2

        const val EXTRA_HIDE_SUBTITLE = "hide_subtitle"
        const val EXTRA_HIDE_ICON = "hide_icon"
        const val EXTRA_QUICK_LAUNCH = "quick_launch"
        const val EXTRA_ICON_COMPONENT_KEY = "icon_component_key"
    }
}
