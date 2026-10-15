package app.lawnchair.fold

import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached

/** Reads the foldable layout preferences from the cache, for the launcher3 code. */
object FoldLayout {

    /**
     * The home layout just set, until the preference cache has it. The grid reload of
     * [PreferenceManager2.foldHomeLayout]'s onSet runs inline on the main thread right after the
     * DataStore write, which may be before the cache collector has received the new value.
     */
    @Volatile
    private var pendingHomeLayout: FoldHomeLayout? = null

    /** Called from [PreferenceManager2.foldHomeLayout]'s onSet, before reloading the grid. */
    @JvmStatic
    fun onHomeLayoutSet(layout: FoldHomeLayout) {
        pendingHomeLayout = layout
    }

    /**
     * Whether the large screen shows the cover layout scaled up ("Same as cover"): one page spanning
     * both halves instead of two panels.
     */
    @JvmStatic
    fun isHomeMirrored(prefs2: PreferenceManager2): Boolean {
        val cached = prefs2.foldHomeLayout.firstCached(prefs2)
        val pending = pendingHomeLayout
        if (pending == cached) {
            pendingHomeLayout = null
        }
        return (pending ?: cached) == FoldHomeLayout.MIRROR
    }

    /** Whether the cover screen opens on the panel last touched on the large screen. */
    @JvmStatic
    fun opensLastPanelAfterFold(prefs2: PreferenceManager2): Boolean = prefs2.foldAfterFold.firstCached(prefs2) == FoldAfterFold.LAST_PANEL
}
