package app.lawnchair.views

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.widget.BaseLauncherAppWidgetHostView
import kotlin.math.roundToInt

/**
 * Applies the widget background settings to an app widget.
 *
 * Only the view the widget declares as its background (`@android:id/background`) is changed.
 * Widgets without exactly one such view are left alone.
 */
class WidgetBackgroundEffects private constructor(
    private val host: BaseLauncherAppWidgetHostView,
    private val backgroundAlpha: Int,
) : View.OnAttachStateChangeListener,
    ViewTreeObserver.OnGlobalLayoutListener,
    ViewTreeObserver.OnPreDrawListener {

    private var observer: ViewTreeObserver? = null
    private var background: View? = null

    override fun onViewAttachedToWindow(v: View) {
        observer = v.viewTreeObserver.also {
            it.addOnGlobalLayoutListener(this)
            it.addOnPreDrawListener(this)
        }
    }

    override fun onViewDetachedFromWindow(v: View) {
        observer?.takeIf { it.isAlive }?.let {
            it.removeOnGlobalLayoutListener(this)
            it.removeOnPreDrawListener(this)
        }
        observer = null
    }

    // RemoteViews updates can replace the widget's views, so look the background up after layouts
    override fun onGlobalLayout() {
        background = findDeclaredBackground(host)
    }

    // RemoteViews updates can also recolor the background drawable without a new layout,
    // which resets its alpha, so it is applied again before every draw.
    override fun onPreDraw(): Boolean {
        fadeBackground()
        return true
    }

    private fun fadeBackground() {
        val bg = background ?: return
        if (bg is ImageView) {
            // A background declared as an ImageView may draw its card through android:src, so
            // fade the whole view: background and src composite first, then fade once. Unlike
            // alpha and imageAlpha, transitionAlpha can't be set through RemoteViews, so the
            // provider's own values stay in effect and multiply with this one. Unchanged values
            // are ignored.
            bg.transitionAlpha = backgroundAlpha / 255f
            return
        }
        // Skip unchanged values: some drawables (e.g. NinePatchDrawable) invalidate on every
        // setAlpha. ColorDrawable reports its combined alpha here, so a translucent color always
        // differs, but its setAlpha ignores unchanged values itself.
        bg.background?.mutate()?.let { if (it.alpha != backgroundAlpha) it.alpha = backgroundAlpha }
    }

    companion object {
        /** Attaches the effects to [host] when any widget background setting is active. */
        @JvmStatic
        fun install(host: BaseLauncherAppWidgetHostView) {
            val prefs = PreferenceManager2.getInstance(host.context)
            val opacity = prefs.widgetBackgroundOpacity.firstCached(prefs).coerceIn(0f, 1f)
            val alpha = (255 * opacity).roundToInt()
            if (alpha == 255) return
            host.addOnAttachStateChangeListener(WidgetBackgroundEffects(host, alpha))
        }

        /** Returns the only view with `@android:id/background`, or null if there is none or several. */
        private fun findDeclaredBackground(root: View): View? {
            var found: View? = null
            fun visit(view: View): Boolean {
                if (view.id == android.R.id.background) {
                    if (found != null) return false
                    found = view
                }
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        if (!visit(view.getChildAt(i))) return false
                    }
                }
                return true
            }
            return if (visit(root)) found else null
        }
    }
}
