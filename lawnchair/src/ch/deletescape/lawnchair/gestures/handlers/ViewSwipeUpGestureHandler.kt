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

package ch.deletescape.lawnchair.gestures.handlers

import android.view.View
import android.view.animation.DecelerateInterpolator
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.R
import com.android.launcher3.touch.OverScroll

class ViewSwipeUpGestureHandler(private val view: View, private val handler: GestureHandler)
    : GestureHandler(view.context, null), VerticalSwipeGestureHandler {

    private val negativeMax by lazy { view.resources.getDimensionPixelSize(R.dimen.swipe_up_negative_max) }
    private val positiveMax by lazy { view.resources.getDimensionPixelSize(R.dimen.swipe_up_positive_max) }

    override fun onGestureTrigger(controller: GestureController, view: View?) {
        controller.launcher.prepareDummyView(this.view) {
            handler.onGestureTrigger(controller, it)
        }
    }

    override fun onDrag(displacement: Float, velocity: Float) {
        view.translationY = OverScroll.dampedScroll(displacement, if (displacement < 0)
            negativeMax else positiveMax).toFloat()
    }

    override fun onDragEnd(velocity: Float, fling: Boolean) {
        view.animate().translationY(0f).setDuration(100).setInterpolator(DecelerateInterpolator()).start()
    }

    override val displayName = ""
}
