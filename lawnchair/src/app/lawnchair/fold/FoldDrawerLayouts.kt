package app.lawnchair.fold

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.deviceprofile.DeviceProperties
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * The app drawer of an unfolded foldable ([FoldDrawerLayout], pref `foldDrawerLayout`).
 *
 * - [FoldDrawerLayout.EXPAND]: today's drawer, more columns (the "when unfolded" drawer columns).
 * - [FoldDrawerLayout.PAGES], "book": the fixed half shows the cover's drawer, with its columns,
 *   cell sizes and paddings; the moving half continues the list where the fixed half ends, like the
 *   second page of a book ([FoldDrawerBook]). Both pages scroll together, by rows.
 * - [FoldDrawerLayout.BIG_ICONS], "zoom": the cover's columns across the whole width, icons and
 *   labels scaled up (at most [FoldTunables.DRAWER_BIG_ICONS_MAX_SCALE]).
 *
 * Applies only to the large screen of a foldable with a vertical fold line; phones, tablets, the
 * cover screen and landscape (horizontal fold line) keep today's drawer. The search bar spans the
 * whole width in every layout; search results use the whole width too.
 *
 * Header rows (predictions, the personal/work tabs) follow the grid: on the fixed half for PAGES,
 * across the width for the others. Drawer folders are grid items like apps.
 *
 * The device profiles of the large screen are adjusted once all profiles exist
 * ([applyToProfiles], called from `InvariantDeviceProfile`), from the cover profile of the same
 * rotation, so the fixed page matches the cover exactly when both screens have the same density.
 */
object FoldDrawerLayouts {

    private const val TAG = "LC-FoldDrawer"

    private class Applied(val layout: FoldDrawerLayout, val cover: DeviceProfile)

    private val applied: MutableMap<DeviceProfile, Applied> = Collections.synchronizedMap(WeakHashMap())

    /** Whether these are the properties of a foldable's large screen with a vertical fold line. */
    fun isLargeFoldable(properties: DeviceProperties): Boolean = properties.foldScreen == FoldScreen.LARGE &&
        FoldPosture.isMovingHalfLeft(properties.rotationHint) != null

    /** Columns of the drawer grid; the cover's for PAGES and BIG_ICONS. Called from `DeviceProfile`. */
    @JvmStatic
    fun getShownAllAppsColumns(
        prefs2: PreferenceManager2,
        inv: InvariantDeviceProfile,
        properties: DeviceProperties,
        columns: Int,
    ): Int {
        if (!isLargeFoldable(properties)) return columns
        return when (prefs2.foldDrawerLayout.firstCached(prefs2)) {
            FoldDrawerLayout.EXPAND -> columns
            FoldDrawerLayout.PAGES, FoldDrawerLayout.BIG_ICONS -> inv.numAllAppsColumns
        }
    }

    /**
     * Adjusts the large-screen profiles of a foldable for PAGES and BIG_ICONS from the cover profile
     * of the same rotation. Called from `InvariantDeviceProfile` once every profile is built.
     */
    @JvmStatic
    fun applyToProfiles(context: Context, profiles: List<DeviceProfile>) {
        if (InvariantDeviceProfile.deviceType != InvariantDeviceProfile.TYPE_MULTI_DISPLAY) return
        val prefs2 = PreferenceManager2.getInstance(context)
        val layout = prefs2.foldDrawerLayout.firstCached(prefs2)
        if (layout == FoldDrawerLayout.EXPAND) return
        for (dp in profiles) {
            val properties = dp.deviceProperties
            if (!isLargeFoldable(properties)) continue
            val cover = FoldLayout.findCoverProfile(profiles, properties.rotationHint)
            if (cover == null) {
                Log.w(TAG, "no cover profile for ${properties.widthPx}x${properties.heightPx}, drawer stays expanded")
                continue
            }
            val scale = when (layout) {
                FoldDrawerLayout.PAGES -> applyPages(dp, cover)
                else -> applyBigIcons(dp, cover)
            }
            applied[dp] = Applied(layout, cover)
            Log.d(TAG, "$layout on ${properties.widthPx}x${properties.heightPx}: ${dp.numShownAllAppsColumns} columns, scale $scale")
        }
    }

    /** Fixed page = the cover's drawer: its profile, its side padding, a full-width sheet. */
    private fun applyPages(dp: DeviceProfile, cover: DeviceProfile): Float {
        val scale = FoldLayout.coverScaleOnLarge(cover, dp)
        val profile = cover.allAppsProfile
        dp.setAllAppsProfile(profile.copy(borderSpacePx = Point(profile.borderSpacePx.x, profile.borderSpacePx.y)))
        dp.updateAllAppsDotRenderer()
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
        // Notification dots follow the larger icons (drawer folders use them too, see FolderIcon).
        // Folder previews already follow the all-apps icon size (PreviewBackground).
        dp.updateAllAppsDotRenderer()
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
    fun isZoomDrawer(dp: DeviceProfile): Boolean = applied[dp]?.layout == FoldDrawerLayout.BIG_ICONS

    /** The drawer layout [dp] was built for, or null when [dp] is not a foldable's large screen. */
    @JvmStatic
    fun drawerLayoutOf(dp: DeviceProfile): FoldDrawerLayout? {
        if (!isLargeFoldable(dp.deviceProperties)) return null
        return applied[dp]?.layout ?: FoldDrawerLayout.EXPAND
    }

    /**
     * Side paddings of the A-Z apps grids (not the search results) and of the header rows above
     * them. PAGES puts both on the fixed half: the moving half's width is added on its side. Called
     * from the drawer container and its header.
     */
    @JvmStatic
    fun adjustAppsGridPadding(dp: DeviceProfile, padding: Rect) {
        if (applied[dp]?.layout != FoldDrawerLayout.PAGES) return
        val half = dp.deviceProperties.widthPx / 2
        if (FoldPosture.isMovingHalfLeft(dp.deviceProperties.rotationHint) == true) padding.left += half else padding.right += half
    }

    /** Horizontal shift from the fixed page to the moving page (PAGES), in pixels. */
    fun bookShiftX(dp: DeviceProfile): Int {
        val half = dp.deviceProperties.widthPx / 2
        return if (FoldPosture.isMovingHalfLeft(dp.deviceProperties.rotationHint) == true) -half else half
    }

    /**
     * Side padding of the drawer search bar. For PAGES it is the cover's, so the bar spans both
     * pages; otherwise the profile's own icon margin, as before.
     */
    @JvmStatic
    fun getSearchBarPadding(context: Context, dp: DeviceProfile): Int {
        val pages = applied[dp]?.takeIf { it.layout == FoldDrawerLayout.PAGES }
            ?: return dp.getAllAppsIconStartMargin(context)
        return (pages.cover.getAllAppsIconStartMargin(context) * FoldLayout.coverScaleOnLarge(pages.cover, dp)).roundToInt()
    }
}
