package app.lawnchair.fold

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.deviceprofile.DeviceProperties

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
     * physical right half). With [FoldPageOrder.OPENING] the even page goes on the fixed half when
     * the hinge side is known, like the page the user was on with "Two pages"; otherwise on the
     * start half, as AOSP does. With [FoldPageOrder.READING] it comes first in the UI language.
     */
    @JvmStatic
    fun isEvenCoverPageOnEnd(context: Context, dp: DeviceProfile): Boolean {
        // CellLayout cells don't mirror in RTL (only folder pages invert), so the end half is
        // always the physical right half: the even page goes there when the moving half is left,
        // or when the language reads right to left.
        if (pageOrder(PreferenceManager2.getInstance(context)) == FoldPageOrder.READING) {
            return Utilities.isRtl(context.resources)
        }
        return FoldPosture.isMovingHalfLeft(dp.deviceProperties.rotationHint) ?: false
    }

    /**
     * The order of the home pages on the large screen: the preference with "Two pages" and
     * "Expand", [FoldPageOrder.OPENING] with the layouts it doesn't apply to.
     */
    @JvmStatic
    fun pageOrder(prefs2: PreferenceManager2): FoldPageOrder = when (prefs2.foldHomeLayout.firstCached(prefs2)) {
        FoldHomeLayout.PAGES, FoldHomeLayout.EXPAND -> prefs2.foldPageOrder.firstCached(prefs2)
        else -> FoldPageOrder.OPENING
    }

    /** Whether pages run right to left on [dp]'s two panels, see [FoldPagePairs.isPageOrderRtl]. */
    @JvmStatic
    fun isPageOrderRtl(dp: DeviceProfile, prefs2: PreferenceManager2, isRtlLanguage: Boolean): Boolean = FoldPagePairs.isPageOrderRtl(pageOrder(prefs2), isRtlLanguage, FoldPosture.isMovingHalfLeft(dp.deviceProperties.rotationHint))

    /**
     * Whether the fixed half of [dp]'s two panels shows the second page of each pair, see
     * [FoldPagePairs.isFixedHalfSecond].
     */
    @JvmStatic
    fun isFixedHalfSecond(dp: DeviceProfile, prefs2: PreferenceManager2, isRtlLanguage: Boolean): Boolean = FoldPagePairs.isFixedHalfSecond(pageOrder(prefs2), isRtlLanguage, FoldPosture.isMovingHalfLeft(dp.deviceProperties.rotationHint))

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

    /** Whether these are the properties of a foldable's large screen with a vertical fold line. */
    fun isLargeFoldable(properties: DeviceProperties): Boolean = properties.foldScreen == FoldScreen.LARGE &&
        FoldPosture.isMovingHalfLeft(properties.rotationHint) != null

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
            isLargeFoldable(it.deviceProperties) && it.deviceProperties.rotationHint == rotation
        }
    }
}
