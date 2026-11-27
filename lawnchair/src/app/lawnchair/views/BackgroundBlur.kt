package app.lawnchair.views

import android.graphics.Canvas
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import app.lawnchair.launcherNullable
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.Utilities
import kotlin.math.roundToInt

/**
 * Blurs what is behind the launcher window (usually the wallpaper) in a rounded rectangle of
 * [view], using the platform's cross-window background blur (Android 12+).
 *
 * Call [draw] from the view's `dispatchDraw` before its content, and [release] when the view is
 * detached, since the blur belongs to the window it was created for. While the system has window
 * blurs turned off, for example in battery saver, the region simply stays unblurred.
 *
 * The blur region is composited by the system, so it doesn't fade with the view on its own. It
 * follows the combined alpha of the view and its parents instead, e.g. while the dock fades out
 * on the way to the app drawer.
 */
class BackgroundBlur private constructor(
    private val view: View,
    private var radius: Int,
) : ViewTreeObserver.OnPreDrawListener {

    private var drawable: BackgroundBlurDrawable? = null
    private var observer: ViewTreeObserver? = null
    private var alpha = 255

    var cornerRadius = 0f
        set(value) {
            if (field == value) return
            field = value
            drawable?.setCornerRadius(value)
        }

    fun draw(canvas: Canvas, bounds: Rect) {
        // The blur drawable records a RenderNode, which needs a hardware canvas
        if (bounds.isEmpty || !canvas.isHardwareAccelerated) return
        val blur = drawable() ?: return
        blur.bounds = bounds
        blur.alpha = alpha
        blur.draw(canvas)
    }

    fun release() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
        drawable = null
    }

    // Alpha changes don't redraw the view's content, so check them before every frame
    override fun onPreDraw(): Boolean {
        val newAlpha = (255 * effectiveAlpha()).roundToInt()
        if (newAlpha != alpha) {
            alpha = newAlpha
            view.invalidate()
        }
        return true
    }

    private fun effectiveAlpha(): Float {
        var result = 1f
        var current: View? = view
        while (current != null && result > 0f) {
            result *= current.alpha
            current = current.parent as? View
        }
        return result
    }

    private fun drawable(): BackgroundBlurDrawable? {
        if (radius == 0) return null
        drawable?.let { return it }
        return try {
            view.viewRootImpl?.createBackgroundBlurDrawable()?.also {
                it.setBlurRadius(radius)
                it.setCornerRadius(cornerRadius)
                drawable = it
                alpha = (255 * effectiveAlpha()).roundToInt()
                observer = view.viewTreeObserver.also { vto -> vto.addOnPreDrawListener(this) }
            }
        } catch (e: LinkageError) {
            // Hidden API: missing or blocked on this device
            Log.w(TAG, "Background blur unavailable", e)
            radius = 0
            null
        }
    }

    companion object {
        private const val TAG = "BackgroundBlur"
        private const val MAX_BLUR_DP = 40f

        /**
         * Creates a blur for a strength setting from 0 to 1, or returns null when the strength is
         * 0, below Android 12, or outside the launcher window. The taskbar and the settings
         * previews inflate launcher views too, and there the blur would sit over an app or the
         * settings instead of the wallpaper.
         */
        fun create(view: View, strength: Float): BackgroundBlur? {
            val blur = strength.coerceIn(0f, 1f)
            if (blur == 0f || !Utilities.ATLEAST_S || view.context.launcherNullable == null) return null
            val radius = (MAX_BLUR_DP * view.resources.displayMetrics.density * blur).roundToInt()
            return if (radius > 0) BackgroundBlur(view, radius) else null
        }
    }
}
