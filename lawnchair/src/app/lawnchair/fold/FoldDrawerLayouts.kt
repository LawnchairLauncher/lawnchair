package app.lawnchair.fold

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Utilities
import com.android.launcher3.deviceprofile.DeviceProperties
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.window.WindowManagerProxy
import kotlin.math.roundToInt

/**
 * The app drawer of an unfolded foldable ([FoldDrawerLayout], pref `foldDrawerLayout`).
 *
 * - [FoldDrawerLayout.EXPAND]: today's drawer, more columns (the "when unfolded" drawer columns).
 * - [FoldDrawerLayout.PAGES], "book": the first page shows the cover's drawer, with its columns,
 *   cell sizes and paddings; the other half continues the list where the first page ends, like the
 *   second page of a book ([FoldDrawerBook]). Both pages scroll together, by rows. The first page
 *   is on the side where the "Page order" option starts ([FoldPageOrder]): the fixed half with
 *   [FoldPageOrder.OPENING], the language's start side with [FoldPageOrder.READING].
 * - [FoldDrawerLayout.BIG_ICONS], "zoom": the cover's columns across the whole width, icons and
 *   labels scaled up (at most [FoldTunables.DRAWER_BIG_ICONS_MAX_SCALE]).
 *
 * Applies only to the large screen of a foldable with a vertical fold line; phones, tablets, the
 * cover screen and landscape (horizontal fold line) keep today's drawer. The search bar spans the
 * whole width in every layout; search results use the whole width too.
 *
 * Header rows (predictions, the personal/work tabs) follow the grid: on the first page for PAGES,
 * across the width for the others. Drawer folders are grid items like apps.
 *
 * Each device profile of the large screen takes its drawer metrics from the cover profile of the
 * same rotation while it is built ([create], called from the `DeviceProfile` constructor), so the
 * first page matches the cover exactly when both screens have the same density, and copies of the
 * profile (the taskbar's) keep the layout. A multi-window profile has no rotation (its window
 * bounds come from the window metrics), so it isn't the large screen here and keeps today's drawer.
 */
object FoldDrawerLayouts {

    private const val TAG = "LC-FoldDrawer"

    /**
     * The drawer layout a large-screen profile was built for, and the cover profile it copies. For
     * PAGES, whether the first page is on the right half ([FoldPagePairs.isPageOrderRtl]) and
     * whether it is on the moving half ([FoldPagePairs.isFixedHalfSecond]); never with
     * [FoldPageOrder.OPENING], whose first page is on the fixed half as before the option.
     */
    class Drawer(
        val layout: FoldDrawerLayout,
        val cover: DeviceProfile,
        val isFirstPageOnRight: Boolean = false,
        val isFirstPageOnMovingHalf: Boolean = false,
    )

    /** Columns of the drawer grid; the cover's for PAGES and BIG_ICONS. Called from `DeviceProfile`. */
    @JvmStatic
    fun getShownAllAppsColumns(
        prefs2: PreferenceManager2,
        inv: InvariantDeviceProfile,
        properties: DeviceProperties,
        columns: Int,
    ): Int {
        if (!FoldLayout.isLargeFoldable(properties)) return columns
        return when (prefs2.foldDrawerLayout.firstCached(prefs2)) {
            FoldDrawerLayout.EXPAND -> columns
            FoldDrawerLayout.PAGES, FoldDrawerLayout.BIG_ICONS -> inv.numAllAppsColumns
        }
    }

    /**
     * Applies PAGES or BIG_ICONS to [dp], a large-screen profile being built, from the cover profile
     * of the same rotation. Returns what [dp] keeps as `DeviceProfile.foldDrawer`, or null for
     * today's drawer (phones, tablets, the cover screen, EXPAND). Called at the end of the
     * `DeviceProfile` constructor, before the notification dots are sized for the drawer's icons.
     */
    @JvmStatic
    fun create(
        dp: DeviceProfile,
        context: Context,
        info: DisplayController.Info,
        wmProxy: WindowManagerProxy,
        themeManager: ThemeManager,
    ): Drawer? {
        val properties = dp.deviceProperties
        if (!FoldLayout.isLargeFoldable(properties)) return null
        val prefs2 = PreferenceManager2.getInstance(context)
        val layout = prefs2.foldDrawerLayout.firstCached(prefs2)
        if (layout == FoldDrawerLayout.EXPAND) return null
        val cover = buildCoverProfile(dp, context, info, wmProxy, themeManager)
        if (cover == null) {
            Log.w(TAG, "no cover profile for ${properties.widthPx}x${properties.heightPx}, drawer stays expanded")
            return null
        }
        val scale = when (layout) {
            FoldDrawerLayout.PAGES -> applyPages(dp, cover)
            else -> applyBigIcons(dp, cover)
        }
        Log.d(TAG, "$layout on ${properties.widthPx}x${properties.heightPx}: ${dp.numShownAllAppsColumns} columns, scale $scale")
        if (layout != FoldDrawerLayout.PAGES) return Drawer(layout, cover)
        // The pages follow the "Page order" option, like the home pages
        val order = prefs2.foldPageOrder.firstCached(prefs2)
        val isRtl = Utilities.isRtl(context.resources)
        val movingHalfLeft = FoldPosture.isMovingHalfLeft(properties.rotationHint)
        return Drawer(
            layout,
            cover,
            isFirstPageOnRight = FoldPagePairs.isPageOrderRtl(order, isRtl, movingHalfLeft),
            isFirstPageOnMovingHalf = FoldPagePairs.isFixedHalfSecond(order, isRtl, movingHalfLeft),
        )
    }

    /**
     * The cover screen's profile in [dp]'s rotation, else its portrait one (the choice of
     * [FoldLayout.findCoverProfile]), built as `InvariantDeviceProfile` builds it. Built here
     * because the profiles of `InvariantDeviceProfile.supportedProfiles` may be from the previous
     * grid while the new ones are built.
     */
    private fun buildCoverProfile(
        dp: DeviceProfile,
        context: Context,
        info: DisplayController.Info,
        wmProxy: WindowManagerProxy,
        themeManager: ThemeManager,
    ): DeviceProfile? {
        val covers = info.supportedBounds.filter { !info.isTablet(it) }
        val rotation = dp.deviceProperties.rotationHint
        val bounds = covers.firstOrNull { it.rotationHint == rotation }
            ?: covers.firstOrNull { !it.isLandscape }
            ?: covers.firstOrNull()
            ?: return null
        return DeviceProfile.Builder(context, dp.inv, info, wmProxy, themeManager)
            .setIsMultiDisplay(true)
            .setWindowBounds(bounds)
            .build()
    }

    /** Fixed page = the cover's drawer: its profile, its side padding, a full-width sheet. */
    private fun applyPages(dp: DeviceProfile, cover: DeviceProfile): Float {
        val scale = FoldLayout.coverScaleOnLarge(cover, dp)
        val profile = cover.allAppsProfile
        dp.setAllAppsProfile(profile.copy(borderSpacePx = Point(profile.borderSpacePx.x, profile.borderSpacePx.y)))
        dp.allAppsLeftRightMargin = 0
        dp.allAppsPadding.left = (cover.allAppsPadding.left * scale).roundToInt()
        dp.allAppsPadding.right = (cover.allAppsPadding.right * scale).roundToInt()
        return 1f
    }

    /** The cover's grid zoomed to the whole width: same columns, larger icons and labels. */
    private fun applyBigIcons(dp: DeviceProfile, cover: DeviceProfile): Float {
        val widthScale = dp.deviceProperties.widthPx.toFloat() / cover.deviceProperties.widthPx
        val scale = widthScale.coerceIn(1f, FoldTunables.DRAWER_BIG_ICONS_MAX_SCALE)
        val profile = cover.allAppsProfile
        dp.setAllAppsProfile(
            profile.copy(
                borderSpacePx = Point(
                    (profile.borderSpacePx.x * scale).roundToInt(),
                    (profile.borderSpacePx.y * scale).roundToInt(),
                ),
                cellHeightPx = (profile.cellHeightPx * scale).roundToInt(),
                iconSizePx = (profile.iconSizePx * scale).roundToInt(),
                iconTextSizePx = profile.iconTextSizePx * scale,
                iconDrawablePaddingPx = (profile.iconDrawablePaddingPx * scale).roundToInt(),
                cellWidthPx = (profile.cellWidthPx * widthScale).roundToInt(),
            ),
        )
        // Notification dots follow the larger icons (drawer folders use them too, see FolderIcon):
        // the profile sizes them after this. Folder previews already follow the all-apps icon size
        // (PreviewBackground).
        dp.allAppsLeftRightMargin = 0
        dp.allAppsPadding.left = (cover.allAppsPadding.left * widthScale).roundToInt()
        dp.allAppsPadding.right = (cover.allAppsPadding.right * widthScale).roundToInt()
        return scale
    }

    /**
     * Bitmap size of the icon cache. The cache keeps one size for every icon, so for the zoom drawer
     * ([FoldDrawerLayout.BIG_ICONS], up to [FoldTunables.DRAWER_BIG_ICONS_MAX_SCALE] larger) every icon
     * is cached that much larger, which keeps the large drawer sharp. The cost is memory: icon
     * bitmaps grow with the square of the scale (about 2.5x at 1.6x, roughly 25 MB more for 200
     * apps), on both screens. It changes only when the layout is switched (the grid reload rebuilds
     * the icon cache once), never on a fold, and not at all on phones, tablets or other layouts.
     * Called from `InvariantDeviceProfile.initGrid`.
     */
    @JvmStatic
    fun getIconCacheBitmapSize(context: Context, iconBitmapSize: Int, allAppsIconBitmapSize: Int): Int {
        if (InvariantDeviceProfile.deviceType != InvariantDeviceProfile.TYPE_MULTI_DISPLAY) return iconBitmapSize
        val prefs2 = PreferenceManager2.getInstance(context)
        if (prefs2.foldDrawerLayout.firstCached(prefs2) != FoldDrawerLayout.BIG_ICONS) return iconBitmapSize
        val zoomed = (allAppsIconBitmapSize * FoldTunables.DRAWER_BIG_ICONS_MAX_SCALE).roundToInt()
        return maxOf(iconBitmapSize, zoomed)
    }

    /** Whether [dp] was built for the zoom drawer ([FoldDrawerLayout.BIG_ICONS]). */
    @JvmStatic
    fun isZoomDrawer(dp: DeviceProfile): Boolean = dp.foldDrawer?.layout == FoldDrawerLayout.BIG_ICONS

    /** The drawer layout [dp] was built for, or null when [dp] is not a foldable's large screen. */
    @JvmStatic
    fun drawerLayoutOf(dp: DeviceProfile): FoldDrawerLayout? {
        if (!FoldLayout.isLargeFoldable(dp.deviceProperties)) return null
        return dp.foldDrawer?.layout ?: FoldDrawerLayout.EXPAND
    }

    /**
     * Side paddings of the A-Z apps grids (not the search results) and of the header rows above
     * them. PAGES puts both on the first page: the other half's width is added on its side. Called
     * from the drawer container and its header.
     */
    @JvmStatic
    fun adjustAppsGridPadding(dp: DeviceProfile, padding: Rect) {
        val drawer = dp.foldDrawer?.takeIf { it.layout == FoldDrawerLayout.PAGES } ?: return
        val half = dp.deviceProperties.widthPx / 2
        if (drawer.isFirstPageOnRight) padding.left += half else padding.right += half
    }

    /** Horizontal shift from the first page to the second page (PAGES), in pixels. */
    fun bookShiftX(dp: DeviceProfile): Int {
        val half = dp.deviceProperties.widthPx / 2
        return if (dp.foldDrawer?.isFirstPageOnRight == true) -half else half
    }

    /**
     * Whether [dp] has the PAGES drawer with its first page on the moving half. Its unfold can't
     * keep the rows seen on the cover in place, so the engine plays [SimpleFoldStyle] instead of
     * [DrawerBookStyle] then.
     */
    fun isBookFirstPageOnMovingHalf(dp: DeviceProfile): Boolean = dp.foldDrawer?.let { it.layout == FoldDrawerLayout.PAGES && it.isFirstPageOnMovingHalf } == true

    /**
     * Side padding of the drawer search bar. For PAGES it is the cover's, so the bar spans both
     * pages; otherwise the profile's own icon margin, as before.
     */
    @JvmStatic
    fun getSearchBarPadding(context: Context, dp: DeviceProfile): Int {
        val pages = dp.foldDrawer?.takeIf { it.layout == FoldDrawerLayout.PAGES }
            ?: return dp.getAllAppsIconStartMargin(context)
        return (pages.cover.getAllAppsIconStartMargin(context) * FoldLayout.coverScaleOnLarge(pages.cover, dp)).roundToInt()
    }
}
