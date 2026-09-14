package app.lawnchair.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewTreeObserver
import app.lawnchair.ui.liquid.DrawerGlassBackdrop
import app.lawnchair.ui.liquid.LiquidGlassPanel
import app.lawnchair.util.DRAWER_TINT_ALPHA
import app.lawnchair.ui.liquid.LiquidGlassWallpaper
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.util.Executors
import com.android.launcher3.util.SystemUiController
import com.android.launcher3.util.Themes
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.views.ScrimView
import com.android.launcher3.views.SpringRelativeLayout

class LawnchairScrimView(context: Context, attrs: AttributeSet?) : ScrimView(context, attrs) {


    /**
     * The blurred home screen the drawer opens over.
     *
     * The scrim is the right place for this: it is the one view that spans the
     * whole screen between the workspace and the drawer, so its colour was
     * already the drawer's background -- this just gives that colour something
     * to tint instead of having it sit on nothing.
     */
    private val glassBackdrop = DrawerGlassBackdrop(this) { drawerOpenness() <= 0f }

    private val launcher by lazy(LazyThreadSafetyMode.NONE) {
        runCatching { Launcher.getLauncher(context) }.getOrNull()
    }

    private var mergedDrawerProgress = 0f
    val isMerged: Boolean get() = launcher?.isMergeAppDrawerToWorkspace() == true

    var mergedBlurOverlay: LiquidGlassPanel? = null
        private set

    fun onMergedDrawerScroll(progress: Float) {
        if (mergedDrawerProgress != progress) {
            mergedDrawerProgress = progress
            updateMergedBlurOverlay(progress)
            invalidate()
        }
    }

    fun ensureMergedBlurOverlay(): LiquidGlassPanel? {
        mergedBlurOverlay?.let { return it }
        val dragLayer = launcher?.dragLayer ?: return null
        val panel = LiquidGlassPanel(context)
        val params = BaseDragLayer.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        params.ignoreInsets = true
        panel.layoutParams = params
        panel.touchThrough = true
        panel.setBlurOnly(true)
        panel.setRimEnabled(false)
        panel.setTinted(false)
        panel.setCornerRadius(0f)
        panel.setBlurRadiusDp(0f)
        panel.visibility = GONE
        panel.alpha = 0f

        val display = context.resources.displayMetrics
        val wallpaper = LiquidGlassWallpaper.get(context)
        panel.setScene(wallpaper, 0, 0, display.widthPixels, display.heightPixels)

        val w = dragLayer.width.toFloat().coerceAtLeast(display.widthPixels.toFloat())
        val h = dragLayer.height.toFloat().coerceAtLeast(display.heightPixels.toFloat())
        panel.setPaneBounds(0f, 0f, w, h, 1f)
        panel.onSyncFrame = Runnable {
            val p = mergedBlurOverlay ?: return@Runnable
            val dl = launcher?.dragLayer ?: return@Runnable
            val shiftX = launcher?.workspace?.overScrollShift ?: 0
            p.setPaneBounds(shiftX.toFloat(), 0f, dl.width.toFloat(), dl.height.toFloat(), 1f)
        }

        val workspace = dragLayer.findViewById<android.view.View>(R.id.workspace)
        val wsIndex = if (workspace != null) dragLayer.indexOfChild(workspace) else -1
        val insertIndex = if (wsIndex >= 0) wsIndex else 0
        dragLayer.addView(panel, insertIndex)
        mergedBlurOverlay = panel
        return panel
    }

    private fun releaseMergedBlurOverlay() {
        val panel = mergedBlurOverlay ?: return
        mergedBlurOverlay = null
        panel.onSyncFrame = null
        panel.visibility = GONE
        (panel.parent as? ViewGroup)?.removeView(panel)
    }

    private var lastWallpaperGeneration = -1

    private fun updateMergedBlurOverlay(progress: Float) {
        if (!isMerged) {
            mergedBlurOverlay?.visibility = GONE
            return
        }
        val panel = ensureMergedBlurOverlay() ?: return
        if (progress <= 0f) {
            panel.visibility = GONE
            panel.alpha = 0f
            panel.setBlurRadiusDp(0f)
        } else {
            if (lastWallpaperGeneration != LiquidGlassWallpaper.generation) {
                lastWallpaperGeneration = LiquidGlassWallpaper.generation
                panel.refreshScene()
            }
            panel.visibility = VISIBLE
            panel.alpha = (progress / 0.15f).coerceIn(0f, 1f)
            panel.setBlurRadiusDp(progress * 18f)
        }
    }

    /**
     * Keeping the capture fresh has to happen outside the draw pass -- drawing
     * the drag layer's children into a bitmap from inside its own draw would be
     * re-entrant -- and a pre-draw listener is the one hook that runs every
     * frame while still being between them.
     */
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (drawerOpenness() <= 0f) glassBackdrop.refreshWhileClosed()
        true
    }

    /**
     * The frosted home screen this scrim is drawing, so surfaces inside the
     * drawer can refract the same image rather than capturing their own.
     */
    val drawerBackdrop: Bitmap? get() = glassBackdrop.frosted

    /** The same scene unblurred, for glass inside the drawer to refract. */
    val drawerSharpBackdrop: Bitmap? get() = glassBackdrop.sharp

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(preDrawListener)

        // Reading and rasterising the wallpaper is the slowest thing any glass
        // surface does, and it is done once per process. Whoever asks for it
        // first otherwise pays for it on the main thread -- a closed folder's
        // icon asks during a draw, and the drawer's backdrop asks moments after
        // the launcher settles. Starting it here means the answer is usually
        // already waiting by the time either of them needs it.
        Executors.THREAD_POOL_EXECUTOR.execute {
            LiquidGlassWallpaper.getBlurred(context)
        }

        if (isMerged) {
            post {
                if (isAttachedToWindow && isMerged) {
                    ensureMergedBlurOverlay()
                    updateMergedBlurOverlay(mergedDrawerProgress)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        glassBackdrop.discard()
        releaseMergedBlurOverlay()
        super.onDetachedFromWindow()
    }

    override fun draw(canvas: Canvas) {
        if (isMerged) {
            return
        }
        val openness = drawerOpenness()
        val overScrollY = (launcher?.appsView as? SpringRelativeLayout)?.overScrollShift ?: 0
        glassBackdrop.draw(canvas, openness, isMerged, overScrollY)
        if (overScrollY != 0) {
            val save = canvas.save()
            canvas.clipRect(0f, overScrollY.toFloat().coerceAtLeast(0f), width.toFloat(), height.toFloat())
            super.draw(canvas)
            canvas.restoreToCount(save)
        } else {
            super.draw(canvas)
        }
    }

    /**
     * How far the drawer has travelled: 0 on the workspace, 1 fully open.
     *
     * Taken from the transition controller rather than from the scrim's own
     * colour, because a folder opening sets that colour too and has no business
     * frosting the home screen.
     */
    fun drawerOpenness(): Float {
        if (isMerged) {
            return mergedDrawerProgress.coerceIn(0f, 1f)
        }
        val controller = launcher?.allAppsController ?: return 0f
        return (1f - controller.progress).coerceIn(0f, 1f)
    }

    override fun updateSysUiColors() {
        val threshold = STATUS_BAR_COLOR_FORCE_UPDATE_THRESHOLD
        val forceChange = visibility == VISIBLE &&
            alpha > threshold &&
            Color.alpha(mBackgroundColor) / 255f > threshold
        with(systemUiController) {
            if (forceChange) {
                updateUiState(SystemUiController.UI_STATE_SCRIM_VIEW, !isScrimDark)
            } else {
                updateUiState(SystemUiController.UI_STATE_SCRIM_VIEW, 0)
            }
        }
    }

    override fun isScrimDark() = if (DRAWER_TINT_ALPHA <= 0.3f) {
        !Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)
    } else {
        super.isScrimDark()
    }
}
