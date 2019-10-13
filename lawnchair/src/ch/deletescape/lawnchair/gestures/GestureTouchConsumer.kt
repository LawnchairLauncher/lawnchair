/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.ViewConfiguration
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.ui.GestureHandlerInitListener
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.BuildConfig
import com.android.launcher3.LauncherAppState
import com.android.launcher3.R
import com.android.launcher3.Utilities.squaredHypot
import com.android.quickstep.ActivityControlHelper
import com.android.quickstep.inputconsumers.DelegateInputConsumer
import com.android.quickstep.inputconsumers.InputConsumer
import com.android.systemui.shared.system.InputMonitorCompat
import com.android.systemui.shared.system.WindowManagerWrapper
import com.android.systemui.shared.system.WindowManagerWrapper.NAV_BAR_POS_LEFT
import com.android.systemui.shared.system.WindowManagerWrapper.NAV_BAR_POS_RIGHT
import kotlin.math.atan2
import kotlin.math.hypot

class GestureTouchConsumer(
        private val context: Context,
        private val leftRegion: RectF,
        private val rightRegion: RectF,
        private val activityControlHelper: ActivityControlHelper<*>,
        delegate: InputConsumer, inputMonitor: InputMonitorCompat)
    : DelegateInputConsumer(delegate, inputMonitor) {

    private val launcher get() = activityControlHelper.createdActivity as? LawnchairLauncher
    private val controller get() = launcher?.gestureController
    private var gestureHandler: GestureHandler? = null

    private val gestureDetector = GestureDetector(context, object :
            GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float,
                             velocityY: Float): Boolean {
            return this@GestureTouchConsumer.onFling(velocityX, velocityY)
        }
    })

    private val downPos = PointF()
    private val lastPos = PointF()
    private val startDragPos = PointF()

    private var activePointerId = -1
    private var passedSlop = false
    private var triggeredGesture = false
    private var timeFraction = 0f
    private var dragTime = 0L
    private var distance = 0f
    private val distThreshold = context.resources.getDimension(R.dimen.gestures_assistant_drag_threshold)

    private val squaredSlop = ViewConfiguration.get(context).scaledTouchSlop.let { it * it }

    override fun getType() = InputConsumer.TYPE_CUSTOM_GESTURES or mDelegate.type

    override fun onMotionEvent(ev: MotionEvent) {
        gestureDetector.onTouchEvent(ev)

        when (ev.actionMasked) {
            ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                downPos.set(ev.x, ev.y)
                lastPos.set(downPos)
                timeFraction = 0f
                if (leftRegion.contains(downPos.x, downPos.y)) {
                    gestureHandler = controller?.navSwipeUpGesture?.leftHandler
                } else if (rightRegion.contains(downPos.x, downPos.y)) {
                    gestureHandler = controller?.navSwipeUpGesture?.rightHandler
                }
                if (gestureHandler == null) {
                    mState = STATE_DELEGATE_ACTIVE
                }
            }
            ACTION_POINTER_DOWN, ACTION_POINTER_UP -> {
                if (ev.actionMasked == ACTION_POINTER_DOWN && mState != STATE_ACTIVE) {
                    mState = STATE_DELEGATE_ACTIVE
                } else {
                    val ptrIdx = ev.actionIndex
                    val ptrId = ev.getPointerId(ptrIdx)
                    if (ptrId == activePointerId) {
                        val newPointerIdx = if (ptrIdx == 0) 1 else 0
                        downPos.set(
                                ev.getX(newPointerIdx) - (lastPos.x - downPos.x),
                                ev.getY(newPointerIdx) - (lastPos.y - downPos.y))
                        lastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx))
                        activePointerId = ev.getPointerId(newPointerIdx)
                    }
                }
            }
            ACTION_MOVE -> {
                if (!mDelegate.allowInterceptByParent()) {
                    mState = STATE_DELEGATE_ACTIVE
                } else if (mState != STATE_DELEGATE_ACTIVE) {
                    val pointerIndex = ev.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        lastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex))
                        if (!passedSlop) {
                            if (squaredHypot(lastPos.x - downPos.x, lastPos.y - downPos.y) > squaredSlop) {
                                passedSlop = true
                                startDragPos.set(lastPos)
                                dragTime = SystemClock.uptimeMillis()

                                if (isValidGestureAngle(getDeltaX(), getDeltaY())) {
                                    setActive(ev)
                                } else {
                                    mState = STATE_DELEGATE_ACTIVE
                                }
                            }
                        } else {
                            distance = hypot(
                                    (lastPos.x - startDragPos.x).toDouble(),
                                    (lastPos.y - startDragPos.y).toDouble()).toFloat()
                            if (distance >= distThreshold && !triggeredGesture) {
                                triggerGesture()
                            }
                        }
                    }
                }
            }
            ACTION_UP, ACTION_CANCEL -> {
                passedSlop = false
                mState = STATE_INACTIVE
            }
        }

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev)
        }
    }

    private fun triggerGesture() {
        triggeredGesture = true
        val handler = gestureHandler ?: return
        if (!handler.requiresForeground) {
            launcher?.rootView?.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
            handler.onGestureTrigger(controller!!)
        }
    }

    private fun onFling(velocityX: Float, velocityY: Float): Boolean {
        val navBarPosition = WindowManagerWrapper.getInstance().getNavBarPosition(0)
        val vx = when (navBarPosition) {
            NAV_BAR_POS_LEFT -> velocityY
            NAV_BAR_POS_RIGHT -> -velocityY
            else -> velocityX
        }
        val vy = when (navBarPosition) {
            NAV_BAR_POS_LEFT -> velocityX
            NAV_BAR_POS_RIGHT -> -velocityX
            else -> velocityY
        }
        if (!triggeredGesture && mState != STATE_DELEGATE_ACTIVE && isValidGestureAngle(vx, -vy)) {
            triggerGesture()
        }
        return true
    }

    private fun getDeltaX(): Float {
        return when (WindowManagerWrapper.getInstance().getNavBarPosition(0)) {
            NAV_BAR_POS_LEFT -> downPos.y - lastPos.y
            NAV_BAR_POS_RIGHT -> lastPos.y - downPos.y
            else -> downPos.x - lastPos.x
        }
    }

    private fun getDeltaY(): Float {
        return when (WindowManagerWrapper.getInstance().getNavBarPosition(0)) {
            NAV_BAR_POS_LEFT -> downPos.x - lastPos.x
            NAV_BAR_POS_RIGHT -> lastPos.x - downPos.x
            else -> downPos.y - lastPos.y
        }
    }

    private fun isValidGestureAngle(deltaX: Float, deltaY: Float): Boolean {
        var angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()

        // normalize so that angle is measured clockwise from horizontal in the bottom right corner
        // and counterclockwise from horizontal in the bottom left corner
        angle = if (angle > 90) 180 - angle else angle
        return angle in 45f..90f
    }
}

class NavSwipeUpGesture(controller: GestureController) : Gesture(controller) {

    override val isEnabled = true

    private val blankHandler = controller.blankGestureHandler
    private val leftHandlerPref by controller.createHandlerPref("pref_gesture_nav_swipe_up_left", blankHandler)
    private val rightHandlerPref by controller.createHandlerPref("pref_gesture_nav_swipe_up_right", blankHandler)

    val leftHandler get() = leftHandlerPref.takeUnless { it is BlankGestureHandler }
    val rightHandler get() = rightHandlerPref.takeUnless { it is BlankGestureHandler }
}
