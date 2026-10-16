package app.lawnchair.fold

import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.DeviceProfile

/** Reads the foldable layout preferences from the cache, for the launcher3 code. */
object FoldLayout {

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

    /** Whether the large screen has its own items ("Independent"), see [FoldItemSets]. */
    @JvmStatic
    fun isHomeIndependent(prefs2: PreferenceManager2): Boolean = currentHomeLayout(prefs2) == FoldHomeLayout.INDEPENDENT

    private fun currentHomeLayout(prefs2: PreferenceManager2): FoldHomeLayout = prefs2.foldHomeLayout.firstCached(prefs2)

    /**
     * Whether a wide "Expand" page shows its even cover page on the end half (cells >= columns, the
     * physical right half). The even page goes on the fixed half when the hinge side is known, like
     * the page the user was on with "Two pages"; otherwise on the start half, as AOSP does.
     */
    @JvmStatic
    fun isEvenCoverPageOnEnd(dp: DeviceProfile): Boolean {
        // CellLayout cells don't mirror in RTL (only folder pages invert), so the end half is
        // always the physical right half: the even page goes there when the moving half is left.
        return FoldPosture.isMovingHalfLeft(dp.deviceProperties.rotationHint) ?: false
    }

    /** Whether the cover screen opens on the panel last touched on the large screen. */
    @JvmStatic
    fun opensLastPanelAfterFold(prefs2: PreferenceManager2): Boolean = prefs2.foldAfterFold.firstCached(prefs2) == FoldAfterFold.LAST_PANEL

    /**
     * Scale that puts the cover screen over the fixed half of the large screen: the cover's width
     * fills the half exactly (Pixel 9 Pro Fold: cover 1080 px, half 1038 px). The dock, the drawer
     * and every transition place the cover with this one scale, so their icons start from the same
     * spot.
     */
    @JvmStatic
    fun coverScaleOnLarge(cover: DeviceProfile, large: DeviceProfile): Float = large.deviceProperties.widthPx / 2f / cover.deviceProperties.widthPx
}
