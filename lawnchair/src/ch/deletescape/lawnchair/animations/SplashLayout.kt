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

package ch.deletescape.lawnchair.animations

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.Insettable
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities

class SplashLayout(context: Context) : FrameLayout(context), Insettable {

    private val statusView = View(context)
    private val navView = View(context)
    private val cutoutView = View(context).apply { setBackgroundColor(Color.BLACK) }
    private var layoutInDisplayCutoutMode = 0

    private val insets = Rect()
    private val crop = Rect()

    init {
        addView(statusView, 0, 0)
        addView(navView, 0, 0)
        addView(cutoutView, 0, 0)
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(crop)
        super.draw(canvas)
        canvas.restore()
    }

    @SuppressLint("RtlHardcoded")
    override fun setInsets(insets: Rect) {
        this.insets.set(insets)
        val statusParams = (statusView.layoutParams as LayoutParams)
        val navParams = (navView.layoutParams as LayoutParams)
        val cutoutParams = (cutoutView.layoutParams as LayoutParams)

        // Default status bar configuration
        statusParams.setMargins(0, 0, 0, 0)
        statusParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        statusParams.height = insets.top
        statusParams.gravity = Gravity.TOP

        val drawCutout = layoutInDisplayCutoutMode != 1
        if (!drawCutout) {
            cutoutParams.width = 0
            cutoutParams.height = 0
        }
        cutoutParams.setMargins(0, 0, 0, 0)

        val isSeascape = Utilities.ATLEAST_OREO
                && LauncherAppState.getIDP(context).getDeviceProfile(context).isSeascape
        if (insets.left == 0 && insets.right == 0) {
            // Navbar is on the bottom
            navParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            navParams.height = insets.bottom
            navParams.gravity = Gravity.BOTTOM
        } else {
            if (isSeascape) {
                // Push status bar to right
                statusParams.leftMargin = insets.left
                navParams.width = insets.left
                navParams.gravity = Gravity.LEFT

                if (drawCutout) {
                    // Add the cutout
                    statusParams.rightMargin = insets.right
                    cutoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    cutoutParams.width = insets.right
                    cutoutParams.gravity = Gravity.RIGHT
                }
            } else {
                // Push status bar to the left
                statusParams.rightMargin = insets.right
                navParams.width = insets.right
                navParams.gravity = Gravity.RIGHT

                if (drawCutout) {
                    // Add the cutout
                    statusParams.leftMargin = insets.left
                    cutoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    cutoutParams.width = insets.left
                    cutoutParams.gravity = Gravity.LEFT
                }
            }
            navParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    fun applySplash(splashData: SplashResolver.SplashData) {
        background = splashData.background ?: ColorDrawable(Color.WHITE)
        statusView.setBackgroundColor(splashData.statusColor)
        navView.setBackgroundColor(splashData.navColor)
        layoutInDisplayCutoutMode = splashData.layoutInDisplayCutoutMode
        setInsets(this.insets)
    }

    fun setCrop(crop: Rect) {
        this.crop.set(crop)
        invalidate()
    }
}
