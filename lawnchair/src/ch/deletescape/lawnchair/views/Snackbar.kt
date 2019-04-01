/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.views.BaseDragLayer

class Snackbar(context: Context, attributeSet: AttributeSet?) : AbstractFloatingView(context, attributeSet) {

    private val launcher = Launcher.getLauncher(context)
    var onDismissed: Runnable? = null

    init {
        LinearLayout.inflate(context, R.layout.snackbar, this)
    }

    public override fun handleClose(z: Boolean) {
        if (mIsOpen) {
            if (z) {
                animate()
                        .alpha(0f)
                        .withLayer()
                        .setStartDelay(0)
                        .setDuration(180)
                        .setInterpolator(Interpolators.ACCEL)
                        .withEndAction(::onClosed).start()
            } else {
                animate().cancel()
                onClosed()
            }
            this.mIsOpen = false
        }
    }

    public override fun isOfType(i: Int): Boolean {
        return i and 128 != 0
    }

    override fun logActionCommand(i: Int) {}

    private fun onClosed() {
        launcher.dragLayer.removeView(this)
        onDismissed?.run()
    }

    override fun onControllerInterceptTouchEvent(motionEvent: MotionEvent): Boolean {
        if (motionEvent.action == 0 && !launcher.dragLayer.isEventOverView(this, motionEvent)) {
            close(true)
        }
        return false
    }

    companion object {

        @JvmStatic
        fun show(launcher: Launcher, i: Int, i2: Int, runnable: Runnable?, runnable2: Runnable?) {
            AbstractFloatingView.closeOpenViews(launcher, true, AbstractFloatingView.TYPE_QUICKSTEP_PREVIEW)
            val snackbar = Snackbar(launcher, null)
            snackbar.orientation = LinearLayout.HORIZONTAL
            snackbar.gravity = 16
            val resources = launcher.resources
            snackbar.elevation = resources.getDimension(R.dimen.snackbar_elevation)
            var dimensionPixelSize = resources.getDimensionPixelSize(R.dimen.snackbar_padding)
            snackbar.setPadding(dimensionPixelSize, dimensionPixelSize, dimensionPixelSize, dimensionPixelSize)
            snackbar.setBackgroundResource(R.drawable.round_rect_primary)
            snackbar.mIsOpen = true
            val dragLayer = launcher.dragLayer
            dragLayer.addView(snackbar)
            val layoutParams = snackbar.layoutParams as BaseDragLayer.LayoutParams
            layoutParams.gravity = 81
            layoutParams.height = resources.getDimensionPixelSize(R.dimen.snackbar_height)
            val dimensionPixelSize2 = resources.getDimensionPixelSize(R.dimen.snackbar_max_margin_left_right)
            var dimensionPixelSize3 = resources.getDimensionPixelSize(R.dimen.snackbar_min_margin_left_right)
            val dimensionPixelSize4 = resources.getDimensionPixelSize(R.dimen.snackbar_margin_bottom)
            val rect = launcher.deviceProfile.insets
            val width = dragLayer.width - dimensionPixelSize3 * 2 - rect.left - rect.right
            layoutParams.width = dragLayer.width - dimensionPixelSize2 * 2 - rect.left - rect.right
            layoutParams.setMargins(0, 0, 0, dimensionPixelSize4 + rect.bottom)
            val textView = snackbar.findViewById<View>(R.id.label) as TextView
            val textView2 = snackbar.findViewById<View>(R.id.action) as TextView
            val string = resources.getString(i)
            val string2 = resources.getString(i2)
            dimensionPixelSize *= 2
            dimensionPixelSize += textView2.paddingLeft + (textView2.paddingRight + (textView.paddingLeft + (textView.paddingRight + (textView2.paint.measureText(string2) + textView.paint.measureText(string)).toInt())))
            if (dimensionPixelSize > layoutParams.width) {
                if (dimensionPixelSize <= width) {
                    layoutParams.width = dimensionPixelSize
                } else {
                    dimensionPixelSize = resources.getDimensionPixelSize(R.dimen.snackbar_content_height)
                    val dimension = resources.getDimension(R.dimen.snackbar_min_text_size)
                    textView.setLines(2)
                    dimensionPixelSize3 = dimensionPixelSize * 2
                    textView.layoutParams.height = dimensionPixelSize3
                    textView2.layoutParams.height = dimensionPixelSize3
                    textView.setTextSize(0, dimension)
                    textView2.setTextSize(0, dimension)
                    layoutParams.height += dimensionPixelSize
                    layoutParams.width = width
                }
            }
            textView.text = string
            textView2.text = string2
            textView2.setTextColor(ColorEngine.getInstance(launcher).accent)
            textView2.setOnClickListener {
                runnable2?.run()
                snackbar.onDismissed = null
                snackbar.close(true)
            }
            snackbar.onDismissed = runnable
            snackbar.alpha = 0f
            snackbar.scaleX = 0.8f
            snackbar.scaleY = 0.8f
            snackbar.animate().alpha(1f).withLayer().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(Interpolators.ACCEL_DEACCEL).start()
            snackbar.postDelayed({ snackbar.close(true) }, 4000)
        }
    }
}