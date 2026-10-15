package app.lawnchair.fold

import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached

/** Reads the foldable layout preferences from the cache, for the launcher3 code. */
object FoldLayout {

    /**
     * Whether the large screen shows the cover layout scaled up ("Same as cover"): one page spanning
     * both halves instead of two panels.
     */
    @JvmStatic
    fun isHomeMirrored(prefs2: PreferenceManager2): Boolean = prefs2.foldHomeLayout.firstCached(prefs2) == FoldHomeLayout.MIRROR

    /** Whether the cover screen opens on the panel last touched on the large screen. */
    @JvmStatic
    fun opensLastPanelAfterFold(prefs2: PreferenceManager2): Boolean = prefs2.foldAfterFold.firstCached(prefs2) == FoldAfterFold.LAST_PANEL
}
