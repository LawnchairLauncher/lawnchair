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

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.support.annotation.Keep
import android.support.v4.content.ContextCompat
import android.view.*
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.lawnchairApp
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.root.RootHelperManager
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.quickstep.OverviewInteractionState
import com.android.quickstep.TouchConsumer
import com.android.systemui.shared.system.NavigationBarCompat
import com.android.systemui.shared.system.WindowManagerWrapper
import org.json.JSONObject
import kotlin.math.abs

@TargetApi(Build.VERSION_CODES.P)
class NavigationBarGestureConsumer(private val context: Context, target: TouchConsumer,
                                   @NavigationBarCompat.HitTarget private val downTarget: Int) :
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
    private var downTime:Long = 0

    private var gestureHandler: GestureHandler? = null

    private var inQuickScrub = false
    override var passThroughEnabled: Boolean
        get() = inQuickScrub || gestureHandler == null || !passedInitialSlop
        set(_) {}

    private var passedInitialSlop = false
    private var quickStepDragSlop = 0
    private var quickScrubDragSlop = 0
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    private val swipeForBack = launcher != null && context.lawnchairPrefs.swipeLeftToGoBack

    override fun accept(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestureHandler = null

                if (controller == null) {
                    super.accept(ev)
                    return
                }

                activePointerId = ev.getPointerId(0)
                passedInitialSlop = false
                quickStepDragSlop = NavigationBarCompat.getQuickStepDragSlopPx()
                quickScrubDragSlop = NavigationBarCompat.getQuickScrubTouchSlopPx()

                downTime = SystemClock.uptimeMillis()

                downPos.set(ev.x, ev.y)
                lastPos.set(downPos)

                val display = context.getSystemService(WindowManager::class.java)!!.defaultDisplay
                display.getSize(tmpPoint)
                displayRotation = display.rotation
                WindowManagerWrapper.getInstance().getStableInsets(stableInsets)

                navBarSize = if (isNavBarVertical) tmpPoint.y else tmpPoint.x
                val downPosition = if (isNavBarVertical) ev.y else ev.x
                downFraction = downPosition / navBarSize
                if (isNavBarOnRight) downFraction = 1 - downFraction

                gestureHandler = getGestureHandler()
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex == MotionEvent.INVALID_POINTER_ID) {
                    return
                }
                lastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex))

                if (!passedInitialSlop) {
                    if (abs(getVerticalDisplacement(ev)) > quickStepDragSlop) {
                        passedInitialSlop = true

                        if (!inQuickScrub && gestureHandler != null) {
                            gestureHandler?.onGestureTrigger(controller!!)
                        }
                    } else if (swipeForBack && getHorizontalDisplacement(ev) > quickScrubDragSlop) {
                        passedInitialSlop = true

                        if (!inQuickScrub) {
                            if (performBackPress()) {
                                playClickEffect()
                            }
                        }
                    }
                }
            }
        }

        super.accept(ev)
    }

    private fun performBackPress(): Boolean {
        if (RootHelperManager.isAvailable) {
            RootHelperManager.getInstance(context).run {
                it.sendKeyEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0, downTime, SystemClock.uptimeMillis())
                it.sendKeyEvent(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, 0, downTime, SystemClock.uptimeMillis())
            }
            return true
        }
        return context.lawnchairApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun playClickEffect() {
        val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)
        audioManager?.playSoundEffect(SoundEffectConstants.CLICK)
        launcher?.rootView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    private fun getVerticalDisplacement(ev: MotionEvent): Float {
        return when {
            isNavBarOnRight -> ev.x - downPos.x
            isNavBarOnLeft -> downPos.x - ev.x
            else -> ev.y - downPos.y
        }
    }

    private fun getHorizontalDisplacement(ev: MotionEvent): Float {
        return when {
            isNavBarOnRight -> ev.y - downPos.y
            isNavBarOnLeft -> downPos.y - ev.y
            else -> downPos.x - ev.x
        }.let { if (isRtl) -it else it }
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

    override val displayName: String = context.getString(R.string.action_switch_apps)

    override fun onGestureTrigger(controller: GestureController, view: View?) {

    }

    override fun isAvailableForSwipeUp(isSwipeUp: Boolean) = isSwipeUp
}
