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

    // The landscape preferences reload the grid in their onSet too, so they are handed over the
    // same way as the home layout.
    private val pendingLandscape = PendingValue<FoldLandscape>()
    private val pendingLandscapeIconSize = PendingValue<Float>()
    private val pendingLandscapeLabelSize = PendingValue<Float>()

    /** Called from [PreferenceManager2.foldLandscape]'s onSet, before reloading the grid. */
    @JvmStatic
    fun onLandscapeSet(landscape: FoldLandscape) = pendingLandscape.set(landscape)

    /** Called from [PreferenceManager2.foldLandscapeIconSizeFactor]'s onSet, before reloading the grid. */
    @JvmStatic
    fun onLandscapeIconSizeSet(factor: Float) = pendingLandscapeIconSize.set(factor)

    /** Called from [PreferenceManager2.foldLandscapeIconLabelSizeFactor]'s onSet, before reloading the grid. */
    @JvmStatic
    fun onLandscapeLabelSizeSet(factor: Float) = pendingLandscapeLabelSize.set(factor)

    /** Whether the large screen has its own icon and label sizes in landscape ([FoldLandscape.OWN]). */
    @JvmStatic
    fun isLandscapeOwn(prefs2: PreferenceManager2): Boolean = pendingLandscape.resolve(prefs2.foldLandscape.firstCached(prefs2)) == FoldLandscape.OWN

    /**
     * The home icon size factor of the large screen in landscape with [FoldLandscape.OWN]: its own
     * value, or the portrait one ([PreferenceManager2.homeIconSizeFactor]) until it is set.
     */
    @JvmStatic
    fun landscapeIconSizeFactor(prefs2: PreferenceManager2): Float {
        val own = pendingLandscapeIconSize.resolve(prefs2.foldLandscapeIconSizeFactor.firstCached(prefs2))
        return if (own < 0f) prefs2.homeIconSizeFactor.firstCached(prefs2) else own
    }

    /** Like [landscapeIconSizeFactor], for the home icon labels ([PreferenceManager2.homeIconLabelSizeFactor]). */
    @JvmStatic
    fun landscapeLabelSizeFactor(prefs2: PreferenceManager2): Float {
        val own = pendingLandscapeLabelSize.resolve(prefs2.foldLandscapeIconLabelSizeFactor.firstCached(prefs2))
        return if (own < 0f) prefs2.homeIconLabelSizeFactor.firstCached(prefs2) else own
    }

    /** A value just set, used until the preference cache returns the same value. */
    private class PendingValue<T : Any> {
        @Volatile
        private var value: T? = null

        fun set(newValue: T) {
            value = newValue
        }

        fun resolve(cached: T): T {
            val pending = value ?: return cached
            if (pending == cached) {
                value = null
            }
            return pending
        }
    }
}
