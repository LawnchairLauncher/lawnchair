package app.lawnchair.fold

import android.content.Context
import android.os.Build
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.views.ActivityContext

/**
 * The wallpaper of a foldable across its two screens (pref `foldWallpaper`), for
 * `WallpaperOffsetInterpolator`. A launcher only controls the wallpaper's offsets; the image and
 * its crops belong to the system.
 *
 * [FoldWallpaper.SPAN]: one image for both screens, and the fold should feel continuous: the cover
 * shows the part of the image the fixed half of the inner screen showed. From Android 15 the
 * system crops one image per screen shape ("multi-crop", AOSP `WallpaperCropper`, case 4): the
 * cover's crop starts at the center of the inner screen's resting crop and extends, for parallax,
 * to its end in reading direction. So with an offset of 1 the cover shows exactly the inner
 * screen's right half, which is the fixed half of a book-style foldable (hinge on the left of the
 * cover). With a wallpaper that does not scroll with the pages, the cover therefore rests on the
 * fixed half's side instead of the reading-direction start. With page scrolling there is no offset
 * that matches in general (the cover's crop only reaches the inner screen's resting crop), so
 * scrolling wallpapers keep today's offsets. Android 14 crops one bitmap for all screens and pans
 * it by offset; whether any offset matches depends on the image's width, so nothing changes there.
 *
 * [FoldWallpaper.EACH]: a separate image per screen. Samsung One UI does it itself ("Wallpaper and
 * style" sets the cover and main screen separately, and wallpaper apps set the screen in use);
 * AOSP and Pixel have no such API, so there Lawnchair's dual live wallpaper
 * ([FoldDualWallpaperService]) does it. Either way Lawnchair leaves the offsets as they are.
 */
object FoldWallpaperOffsets {

    /** Whether this device's system keeps a separate wallpaper per screen (see [FoldWallpaper.EACH]). */
    val systemHasWallpaperPerScreen: Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /**
     * The foldable screen [context]'s launcher is on: 0 the cover, 1 the large screen, -1 not a
     * foldable. Cheap; called for every wallpaper offset update.
     */
    @JvmStatic
    fun getSizeClass(context: Context): Int {
        val activity: ActivityContext = ActivityContext.lookupContextNoThrow(context) ?: return -1
        return when (activity.deviceProfile?.deviceProperties?.foldScreen) {
            FoldScreen.COVER -> 0
            FoldScreen.LARGE -> 1
            else -> -1
        }
    }

    /**
     * Offset (0 or 1) of a wallpaper that does not scroll with the pages. [defaultOffset] is the
     * reading-direction start the launcher uses otherwise.
     */
    @JvmStatic
    fun getRestingOffset(context: Context, defaultOffset: Int): Int {
        // Before Android 15 the system has no foldable-aware crops to rely on
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return defaultOffset
        if (getSizeClass(context) != 0) return defaultOffset
        val prefs2 = PreferenceManager2.getInstance(context)
        if (prefs2.foldWallpaper.firstCached(prefs2) != FoldWallpaper.SPAN) return defaultOffset
        val activity: ActivityContext = ActivityContext.lookupContextNoThrow(context) ?: return defaultOffset
        val rotation = activity.deviceProfile.deviceProperties.rotationHint
        return when (FoldPosture.isMovingHalfLeft(rotation)) {
            // The fixed half is the right half: the cover rests on the right end
            true -> 1

            false -> 0

            null -> defaultOffset
        }
    }
}
