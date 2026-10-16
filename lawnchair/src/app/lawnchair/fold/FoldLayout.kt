package app.lawnchair.fold

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Utilities

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
    fun isHomeMirrored(prefs2: PreferenceManager2): Boolean = currentHomeLayout(prefs2) == FoldHomeLayout.MIRROR

    /**
     * Whether the large screen shows one wide page per pair of cover pages ("Expand"): cover pages
     * 2n and 2n + 1 side by side, twice the columns, scrolled one wide page at a time.
     */
    @JvmStatic
    fun isHomeExpanded(prefs2: PreferenceManager2): Boolean = currentHomeLayout(prefs2) == FoldHomeLayout.EXPAND

    private fun currentHomeLayout(prefs2: PreferenceManager2): FoldHomeLayout {
        val cached = prefs2.foldHomeLayout.firstCached(prefs2)
        val pending = pendingHomeLayout
        if (pending == cached) {
            pendingHomeLayout = null
        }
        return pending ?: cached
    }

    /**
     * Whether a wide "Expand" page shows its even cover page on the end half (the right half in a
     * left-to-right layout). The even page goes on the fixed half when the hinge side is known, like
     * the page the user was on with "Two pages"; otherwise on the start half, as AOSP does.
     */
    @JvmStatic
    fun isEvenCoverPageOnEnd(context: Context, dp: DeviceProfile): Boolean {
        val fixedHalfIsRight = when (FoldPosture.getHingeSide(dp.deviceProperties.rotationHint, assumeBookStyle = true)) {
            HingeSide.LEFT -> true
            HingeSide.RIGHT -> false
            HingeSide.UNKNOWN -> return false
        }
        return fixedHalfIsRight != Utilities.isRtl(context.resources)
    }

    /** Whether the cover screen opens on the panel last touched on the large screen. */
    @JvmStatic
    fun opensLastPanelAfterFold(prefs2: PreferenceManager2): Boolean = prefs2.foldAfterFold.firstCached(prefs2) == FoldAfterFold.LAST_PANEL
}
