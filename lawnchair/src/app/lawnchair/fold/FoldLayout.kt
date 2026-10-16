package app.lawnchair.fold

import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.DeviceProfile

/**
 * The foldable layout: its preferences, read from the cache for the launcher3 code, and how the
 * cover screen's profiles map onto the large screen's.
 */
object FoldLayout {

    /**
     * Whether the large screen shows the cover layout scaled up ("Same as cover"): one page spanning
     * both halves instead of two panels.
     */
    @JvmStatic
    fun isHomeMirrored(prefs2: PreferenceManager2): Boolean = prefs2.foldHomeLayout.firstCached(prefs2) == FoldHomeLayout.MIRROR

    /**
     * Whether the large screen shows one wide page per pair of cover pages ("Expand"): cover pages
     * 2n and 2n + 1 side by side, twice the columns, scrolled one wide page at a time.
     */
    @JvmStatic
    fun isHomeExpanded(prefs2: PreferenceManager2): Boolean = prefs2.foldHomeLayout.firstCached(prefs2) == FoldHomeLayout.EXPAND

    /** Whether the large screen has its own items ("Independent"), see [FoldItemSets]. */
    @JvmStatic
    fun isHomeIndependent(prefs2: PreferenceManager2): Boolean = prefs2.foldHomeLayout.firstCached(prefs2) == FoldHomeLayout.INDEPENDENT

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

    /** Whether the large screen has its own icon and label sizes in landscape ([FoldLandscape.OWN]). */
    @JvmStatic
    fun isLandscapeOwn(prefs2: PreferenceManager2): Boolean = prefs2.foldLandscape.firstCached(prefs2) == FoldLandscape.OWN

    /**
     * The home icon size factor of the large screen in landscape with [FoldLandscape.OWN]: its own
     * value, or the portrait one ([PreferenceManager2.homeIconSizeFactor]) until it is set.
     */
    @JvmStatic
    fun landscapeIconSizeFactor(prefs2: PreferenceManager2): Float {
        val own = prefs2.foldLandscapeIconSizeFactor.firstCached(prefs2)
        return if (own < 0f) prefs2.homeIconSizeFactor.firstCached(prefs2) else own
    }

    /** Like [landscapeIconSizeFactor], for the home icon labels ([PreferenceManager2.homeIconLabelSizeFactor]). */
    @JvmStatic
    fun landscapeLabelSizeFactor(prefs2: PreferenceManager2): Float {
        val own = prefs2.foldLandscapeIconLabelSizeFactor.firstCached(prefs2)
        return if (own < 0f) prefs2.homeIconLabelSizeFactor.firstCached(prefs2) else own
    }

    /**
     * Scale that puts the cover screen over the fixed half of the large screen: the cover's width
     * fills the half exactly (Pixel 9 Pro Fold: cover 1080 px, half 1038 px). The dock, the drawer
     * and every transition place the cover with this one scale, so their icons start from the same
     * spot.
     */
    @JvmStatic
    fun coverScaleOnLarge(cover: DeviceProfile, large: DeviceProfile): Float = large.deviceProperties.widthPx / 2f / cover.deviceProperties.widthPx

    /**
     * The cover screen's profile matching [dp]: [dp] itself on the cover, else the cover's profile
     * in the same rotation, else its portrait one. The cover is the device's phone-sized screen.
     */
    fun coverProfileOf(dp: DeviceProfile): DeviceProfile? {
        if (!dp.deviceProperties.isTablet) return dp
        return findCoverProfile(dp.inv.supportedProfiles, dp.deviceProperties.rotationHint)
    }

    /** The cover profile among [profiles] for a screen in [rotation], see [coverProfileOf]. */
    fun findCoverProfile(profiles: List<DeviceProfile>, rotation: Int): DeviceProfile? {
        var portrait: DeviceProfile? = null
        var any: DeviceProfile? = null
        for (profile in profiles) {
            val properties = profile.deviceProperties
            if (properties.isTablet) continue
            if (properties.rotationHint == rotation) return profile
            if (portrait == null && !properties.isLandscape) portrait = profile
            if (any == null) any = profile
        }
        return portrait ?: any
    }

    /** The large profile matching the cover profile [dp], in the same rotation (itself when [dp] is large). */
    fun largeProfileOf(dp: DeviceProfile): DeviceProfile? {
        if (dp.deviceProperties.isTablet) return dp
        val rotation = dp.deviceProperties.rotationHint
        return dp.inv.supportedProfiles.firstOrNull {
            FoldDrawerLayouts.isLargeFoldable(it.deviceProperties) && it.deviceProperties.rotationHint == rotation
        }
    }
}
