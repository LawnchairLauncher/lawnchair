package app.lawnchair.blur

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.View
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.statemanager.StateManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Live blur of the wallpaper behind a home screen item, the Android equivalent of CSS
 * backdrop-filter. Items draw it right before their own content: SurfaceFlinger blurs what is
 * behind the launcher window within the region, which follows the item as it moves.
 */
class GlassBlur(private val view: View, private val type: Type) {

    enum class Type { ICON, FOLDER_ICON, OPEN_FOLDER, WIDGET }

    /**
     * Workspace item drawing a glass blur, released while the item is hidden.
     */
    interface Host {
        fun releaseGlassBlur()
    }

    val isEnabled: Boolean
    private val radiusPx: Int

    // Folders and widgets get their tint from their own background
    private val tint: Int
    private var drawable: GlassBlurDrawable? = null
    private val bounds = Rect()
    private val clipBounds = RectF()
    private val launcher by lazy {
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .firstOrNull { it is Launcher } as Launcher?
    }

    init {
        // The launcher is recreated when these settings change
        val prefs = PreferenceManager.getInstance(view.context)
        isEnabled = prefs.glassBlurIcons.get() && when (type) {
            Type.ICON -> true
            Type.FOLDER_ICON, Type.OPEN_FOLDER -> prefs.glassBlurFolders.get()
            Type.WIDGET -> prefs.glassBlurWidgets.get()
        }
        radiusPx = (prefs.glassBlurRadius.get() * view.resources.displayMetrics.density).roundToInt()
        tint = if (type == Type.ICON) {
            Color.argb((prefs.glassBlurTint.get() * 255).roundToInt(), 255, 255, 255)
        } else {
            Color.TRANSPARENT
        }
    }

    /**
     * Blurs behind the opaque part of an icon bitmap drawn within the given bounds.
     */
    fun drawIcon(canvas: Canvas, iconBounds: Rect, icon: Bitmap) {
        if (!canDraw(canvas)) {
            return
        }
        val shape = GlassBlurShapes.getIconShape(icon, view) ?: return
        val size = iconBounds.width()
        // Slightly inside the icon, so the edge of the blur never shows around it
        val inset = (size * ICON_INSET_FRACTION).roundToInt()
        bounds.set(
            iconBounds.left + (shape.bounds.left * size).roundToInt() + inset,
            iconBounds.top + (shape.bounds.top * size).roundToInt() + inset,
            iconBounds.left + (shape.bounds.right * size).roundToInt() - inset,
            iconBounds.top + (shape.bounds.bottom * size).roundToInt() - inset,
        )
        draw(canvas, shape.radiusTL * size, shape.radiusTR * size, shape.radiusBL * size, shape.radiusBR * size)
    }

    /**
     * Blurs behind a folder icon background, drawn with the folder shape at the given position.
     */
    fun drawFolderIcon(canvas: Canvas, left: Int, top: Int, radius: Int) {
        if (view.parent !is ShortcutAndWidgetContainer || !canDraw(canvas)) {
            return
        }
        bounds.set(left, top, left + 2 * radius, top + 2 * radius)
        val folderShape = ThemeManager.INSTANCE.get(view.context).folderShape
        val corner = radius * GlassBlurShapes.getFolderCornerRatio(folderShape)
        draw(canvas, corner, corner, corner, corner)
    }

    /**
     * Blurs behind an open folder background. While opening and closing, the folder is clipped
     * to a rounded rect growing from the folder icon, which the blur follows.
     */
    fun drawOpenFolder(canvas: Canvas, background: GradientDrawable, clipPath: Path?) {
        if (!canDraw(canvas)) {
            return
        }
        bounds.set(background.bounds)
        var corner = background.cornerRadius
        if (clipPath != null) {
            clipPath.computeBounds(clipBounds, false)
            val intersects = bounds.intersect(
                clipBounds.left.roundToInt(),
                clipBounds.top.roundToInt(),
                clipBounds.right.roundToInt(),
                clipBounds.bottom.roundToInt(),
            )
            if (!intersects) {
                bounds.setEmpty()
            }
            // The reveal starts from the folder icon, about as round as a circle
            corner = min(corner, min(bounds.width(), bounds.height()) / 2f)
        }
        draw(canvas, corner, corner, corner, corner)
    }

    /**
     * Blurs behind a widget background, with the corners the widget is clipped to.
     */
    fun drawWidget(canvas: Canvas, backgroundBounds: Rect, cornerRadius: Float) {
        if (backgroundBounds.isEmpty || view.parent !is ShortcutAndWidgetContainer || !canDraw(canvas)) {
            return
        }
        bounds.set(backgroundBounds)
        val corner = max(cornerRadius, 0f)
        draw(canvas, corner, corner, corner, corner)
    }

    /**
     * Unregisters the blur region, which is registered again on the next draw.
     */
    fun release() {
        drawable?.setVisible(false, false)
        drawable = null
    }

    private fun canDraw(canvas: Canvas): Boolean {
        // Software canvases (e.g. icon snapshots) cannot draw render nodes
        if (!isEnabled || !canvas.isHardwareAccelerated) {
            return false
        }
        // Folders opened from the app drawer are not hidden by it
        val state = launcher?.stateManager?.state
        return type == Type.OPEN_FOLDER || state == null || !isSuppressedIn(state)
    }

    private fun draw(canvas: Canvas, radiusTL: Float, radiusTR: Float, radiusBL: Float, radiusBR: Float) {
        val blur = drawable ?: run {
            if (view.viewRootImpl == null) {
                return
            }
            GlassBlurDrawable.create(view).also {
                it.setBlurRadius(radiusPx)
                it.setColor(tint)
                drawable = it
            }
        }
        blur.bounds = bounds
        blur.setCornerRadius(radiusTL, radiusTR, radiusBL, radiusBR)
        blur.draw(canvas)
    }

    companion object {
        private const val ICON_INSET_FRACTION = 0.02f

        /**
         * Workspace items are hidden behind the app drawer and recents. Without root, their
         * regions would also count towards the SurfaceFlinger limit of 10 blur requests on
         * screen, and disable the window background blur used by the drawer and recents.
         */
        private fun isSuppressedIn(state: LauncherState) = state == LauncherState.ALL_APPS || state.isRecentsViewVisible

        /**
         * Releases the blur regions of the workspace items while they are hidden.
         */
        @JvmStatic
        fun createStateListener(launcher: Launcher) = object : StateManager.StateListener<LauncherState> {
            override fun onStateTransitionStart(toState: LauncherState) {
                if (isSuppressedIn(toState)) {
                    releaseWorkspaceItems(launcher)
                }
            }

            override fun onStateTransitionComplete(finalState: LauncherState) {
                if (isSuppressedIn(finalState)) {
                    releaseWorkspaceItems(launcher)
                } else {
                    // Items are not redrawn on their own, while their regions are registered on draw
                    launcher.workspace.mapOverItems { _, item ->
                        if (item is Host) {
                            item.invalidate()
                        }
                        false
                    }
                }
            }
        }

        private fun releaseWorkspaceItems(launcher: Launcher) {
            launcher.workspace.mapOverItems { _, item ->
                (item as? Host)?.releaseGlassBlur()
                false
            }
        }
    }
}
