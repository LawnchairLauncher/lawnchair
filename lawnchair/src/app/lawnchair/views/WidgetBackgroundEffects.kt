package app.lawnchair.views

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.widget.RoundedCornerEnforcement
import kotlin.math.roundToInt

/**
 * Applies the widget background settings to an app widget.
 *
 * Only the view the widget declares as its background (`@android:id/background`) is changed.
 * Widgets without one are left alone, since a guessed background may be a transparent container.
 */
class WidgetBackgroundEffects(private val host: ViewGroup) {

    private val prefs = PreferenceManager2.getInstance(host.context)

    private val backgroundAlpha =
        (255 * prefs.widgetBackgroundOpacity.firstCached(prefs).coerceIn(0f, 1f)).roundToInt()

    private var background: View? = null

    // RemoteViews updates can recolor or replace the background drawable without a new layout,
    // which resets its alpha, so it is applied again before every draw.
    private val fadeListener = ViewTreeObserver.OnPreDrawListener {
        fadeBackground()
        true
    }

    fun onAttachedToWindow() {
        if (backgroundAlpha < 255) host.viewTreeObserver.addOnPreDrawListener(fadeListener)
    }

    fun onDetachedFromWindow() {
        host.viewTreeObserver.removeOnPreDrawListener(fadeListener)
    }

    fun onLayout() {
        if (backgroundAlpha == 255) return
        background = RoundedCornerEnforcement.findBackground(host)
            ?.takeIf { it.id == android.R.id.background }
        fadeBackground()
    }

    private fun fadeBackground() {
        val bg = background ?: return
        // Only write on change: some drawables (e.g. NinePatchDrawable) invalidate on every
        // setAlpha, which would keep the widget redrawing from this pre-draw listener.
        bg.background?.mutate()?.let { if (it.alpha != backgroundAlpha) it.alpha = backgroundAlpha }
        // A background declared as an ImageView may draw its card through android:src
        if (bg is ImageView) bg.imageAlpha = backgroundAlpha
    }
}
