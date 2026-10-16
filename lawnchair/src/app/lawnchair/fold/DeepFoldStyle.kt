package app.lawnchair.fold

import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.RequiresApi
import com.android.launcher3.BubbleTextView
import com.android.launcher3.folder.FolderIcon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * DEEP: the moving half has depth. While it is tilted (virtually, up to
 * [FoldTunables.DEEP_MAX_TILT_DEGREES] at progress 0), its layers shift toward the hinge by
 * sin(tilt) × depth: the wallpaper least ([FoldTunables.DEEP_WALLPAPER_DEPTH], through
 * `WallpaperManager.setWallpaperOffsets` via the workspace's offset interpolator), widgets more
 * ([FoldTunables.DEEP_WIDGET_DEPTH]), icons most (depth 1). Icons also rise off the page with a soft
 * elevation shadow that fades as the half comes flat. The fixed half is not tilted, so nothing on
 * it moves; on the cover screen after a fold nothing is tilted either.
 *
 * Icons get a temporary outline (their icon area) so the shadow has a shape; the previous outline
 * provider and elevation are restored at the end, and the wallpaper offset goes back to the
 * workspace's.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class DeepFoldStyle : FoldStyle() {

    private val tracker = FoldItemTracker(::release)
    private val iconRect = Rect()

    /** Outlines the icon area (not the label) of an icon or folder. */
    private val iconOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            when (view) {
                is BubbleTextView -> view.getIconBounds(iconRect)
                is FolderIcon -> view.getPreviewBounds(iconRect)
                else -> iconRect.set(0, 0, view.width, view.height)
            }
            outline.setRoundRect(iconRect, iconRect.width() * ICON_CORNER_FRACTION)
            outline.alpha = SHADOW_ALPHA
        }
    }

    private var shiftPx = 0f
    private var elevationPx = 0f
    private var appliedWallpaperOffset = 0f

    override fun onStart(scene: FoldScene): Boolean {
        shiftPx = scene.dpToPx(FoldTunables.DEEP_MAX_SHIFT_DP)
        elevationPx = scene.dpToPx(FoldTunables.DEEP_MAX_ELEVATION_DP)
        return true
    }

    override fun onPreDraw(scene: FoldScene, progress: Float) {
        // Only the large layout has a tilted half.
        if (!scene.largeLayout) return
        val geometry = scene.geometry
        val tilt = FoldTunables.DEEP_MAX_TILT_DEGREES * (1f - progress.coerceIn(0f, 1f)) * (PI.toFloat() / 180f)
        val lean = sin(tilt)
        tracker.update(scene)
        val items = tracker.items
        for (i in items.indices) {
            val item = items[i]
            if (!item.onMovingHalf) {
                release(item)
                continue
            }
            val depth = if (item.isWidget) FoldTunables.DEEP_WIDGET_DEPTH else 1f
            val shift = lean * depth * shiftPx
            item.setTranslation(geometry.towardHingeX * shift, geometry.towardHingeY * shift)
            if (!item.isWidget) raise(item, lean * elevationPx)
        }
        // The wallpaper scrolls the other way round: a lower offset moves its content right.
        val wallpaper = if (geometry.isVerticalHinge) {
            -geometry.towardHingeX * lean * FoldTunables.DEEP_WALLPAPER_DEPTH * FoldTunables.DEEP_WALLPAPER_SHIFT
        } else {
            0f
        }
        setWallpaperOffset(scene, wallpaper)
    }

    override fun onEnd(scene: FoldScene) {
        tracker.clear()
        setWallpaperOffset(scene, 0f)
    }

    private fun raise(item: FoldItem, z: Float) {
        val view = item.view
        if (item.styleState == null) {
            // Save what we change: styleState keeps the outline provider, value the elevation.
            item.styleState = view.outlineProvider ?: NO_OUTLINE
            item.value = view.translationZ
            view.outlineProvider = iconOutline
        }
        if (view.translationZ != item.value + z) view.translationZ = item.value + z
    }

    private fun release(item: FoldItem) {
        item.resetTranslation()
        val saved = item.styleState ?: return
        val view = item.view
        view.translationZ = item.value
        view.outlineProvider = if (saved === NO_OUTLINE) null else saved as ViewOutlineProvider
        item.styleState = null
        item.value = 0f
    }

    private fun setWallpaperOffset(scene: FoldScene, offset: Float) {
        if (abs(offset - appliedWallpaperOffset) < MIN_WALLPAPER_CHANGE && (offset != 0f || appliedWallpaperOffset == 0f)) return
        appliedWallpaperOffset = offset
        scene.launcher.workspace?.setWallpaperFoldOffset(offset)
    }

    private companion object {
        const val ICON_CORNER_FRACTION = 0.3f
        const val SHADOW_ALPHA = 0.6f
        const val MIN_WALLPAPER_CHANGE = 0.0005f

        /** Stands for "the view had no outline provider" in the saved state. */
        val NO_OUTLINE = Any()
    }
}
