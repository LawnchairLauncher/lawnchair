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

package ch.deletescape.lawnchair.gestures

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.support.annotation.Keep
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import ch.deletescape.lawnchair.LawnchairLauncher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.quickstep.OverviewInteractionState
import com.android.quickstep.TouchConsumer
import com.android.systemui.shared.system.NavigationBarCompat
import com.android.systemui.shared.system.WindowManagerWrapper
import org.json.JSONObject

@TargetApi(Build.VERSION_CODES.P)
class NavigationBarGestureConsumer(private val context: Context, target: TouchConsumer) :
        PassThroughTouchConsumer(target) {

    private val launcher get() = LauncherAppState.getInstance(context).launcher as? LawnchairLauncher
    private val controller get() = launcher?.gestureController
    private var displayRotation = 0
    private val stableInsets = Rect()
    private val tmpPoint = Point()
    private val downPos = PointF()
    private val lastPos = PointF()
    private var navBarSize = 0
    private var downFraction = 0f

    private var gestureHandler: GestureHandler? = null

    private var inQuickScrub = false
    override var passThroughEnabled: Boolean
        get() = inQuickScrub || gestureHandler == null || !passedInitialSlop
        set(_) {}

    private var passedInitialSlop = false
    private var quickStepDragSlop = 0
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    override fun accept(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureHandler = null

            if (controller == null) {
                super.accept(ev)
                return
            }

            activePointerId = ev.getPointerId(0)
            passedInitialSlop = false
            quickStepDragSlop = NavigationBarCompat.getQuickStepDragSlopPx()

            downPos.set(ev.x, ev.y)
            lastPos.set(downPos)

            val display = context.getSystemService<WindowManager>(WindowManager::class.java)!!.defaultDisplay
            display.getSize(tmpPoint)
            displayRotation = display.rotation
            WindowManagerWrapper.getInstance().getStableInsets(stableInsets)

            navBarSize = if (isNavBarVertical) tmpPoint.y else tmpPoint.x
            val downPosition = if (isNavBarVertical) ev.y else ev.x
            downFraction = downPosition / navBarSize
            if (isNavBarOnRight) downFraction = 1 - downFraction

            gestureHandler = getGestureHandler()
        }

        if (!inQuickScrub && gestureHandler != null) {
            if (ev.actionMasked == MotionEvent.ACTION_MOVE) {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex == MotionEvent.INVALID_POINTER_ID) {
                    return
                }
                lastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex))

                val displacement = getDisplacement(ev)
                if (!passedInitialSlop) {
                    if (Math.abs(displacement) > quickStepDragSlop) {
                        passedInitialSlop = true
                        gestureHandler?.onGestureTrigger(controller!!)
                    }
                }
            }
        }

        super.accept(ev)
    }

    private fun getDisplacement(ev: MotionEvent): Float {
        val eventX = ev.x
        val eventY = ev.y
        var displacement = eventY - downPos.y
        if (isNavBarOnRight) {
            displacement = eventX - downPos.x
        } else if (isNavBarOnLeft) {
            displacement = downPos.x - eventX
        }
        return displacement
    }

    private fun getGestureHandler(): GestureHandler? {
        val controller = controller ?: return null
        val handler = controller.navSwipeUpGesture.getHandlerForDownFraction(downFraction)
        return if (handler is SwitchAppsGestureHandler) null else handler
    }

    private val isNavBarVertical get() = isNavBarOnLeft || isNavBarOnRight

    private val isNavBarOnRight get() = displayRotation == Surface.ROTATION_90 && stableInsets.right > 0

    private val isNavBarOnLeft get() = displayRotation == Surface.ROTATION_270 && stableInsets.left > 0

    override fun updateTouchTracking(interactionType: Int) {
        super.updateTouchTracking(interactionType)
        inQuickScrub = interactionType == TouchConsumer.INTERACTION_QUICK_SCRUB
    }
}

@Keep
class NavSwipeUpGesture(controller: GestureController) : Gesture(controller) {

    override val isEnabled = OverviewInteractionState.getInstance(controller.launcher).isSwipeUpGestureEnabled

    private val defaultHandler = SwitchAppsGestureHandler(controller.launcher, null)
    private val leftHandler by controller.createHandlerPref("pref_gesture_nav_swipe_up_left", defaultHandler)
    private val centerHandler by controller.createHandlerPref("pref_gesture_nav_swipe_up_center", defaultHandler)
    private val rightHandler by controller.createHandlerPref("pref_gesture_nav_swipe_up_right", defaultHandler)

    fun getHandlerForDownFraction(downFraction: Float): GestureHandler {
        if (downFraction < 0.25f) return leftHandler
        if (downFraction > 0.75f) return rightHandler
        return centerHandler
    }
}

@Keep
class SwitchAppsGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_switch_apps)!!

    override fun onGestureTrigger(controller: GestureController) {

    }

    override fun isAvailableForSwipeUp(isSwipeUp: Boolean) = isSwipeUp
}
