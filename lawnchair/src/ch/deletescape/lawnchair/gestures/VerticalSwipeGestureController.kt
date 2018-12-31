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

import android.view.MotionEvent
import ch.deletescape.lawnchair.LawnchairLauncher
import com.android.launcher3.Launcher
import com.android.launcher3.util.TouchController
import com.android.launcher3.LauncherState
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.touch.SwipeDetector
import java.lang.reflect.InvocationTargetException
import android.annotation.SuppressLint

class VerticalSwipeGestureController(private val launcher: Launcher) : TouchController, SwipeDetector.Listener {

    enum class GestureState {
        Locked,
        Free,
        NotificationOpened,
        NotificationClosed,
        Triggered
    }

    private val triggerVelocity = 2.25f
    private val notificationsCloseVelocity = 0.35f

    private val controller = LawnchairLauncher.getLauncher(launcher).gestureController
    private val gesture = controller.verticalSwipeGesture
    private val detector = SwipeDetector(launcher, this, SwipeDetector.VERTICAL)
    private var noIntercept = false

    private var hasSwipeUpOverride = false
    private var state = GestureState.Free
    private var downTime = 0L

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        downTime = ev.downTime
        val isDown = ev.actionMasked == MotionEvent.ACTION_DOWN
        val overrideAppeared = !hasSwipeUpOverride && controller.getSwipeUpOverride(ev.downTime) != null
        if (isDown || overrideAppeared) {
            if (isDown) {
                hasSwipeUpOverride = false
            }
            val swipeDirection = getSwipeDirection(ev)
            noIntercept = !canInterceptTouch(ev) && !hasSwipeUpOverride
            if (noIntercept) {
                return false
            }
            detector.setDetectableScrollConditions(swipeDirection, false)
        } else if (ev.pointerCount > 1) {
            noIntercept = true
        }
        if (noIntercept) {
            return false
        }
        onControllerTouchEvent(ev)
        return detector.isDraggingOrSettling
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        return detector.onTouchEvent(ev)
    }

    private fun canInterceptTouch(ev: MotionEvent): Boolean {
        return AbstractFloatingView.getTopOpenView(launcher) == null &&
                launcher.isInState(LauncherState.NORMAL)
    }

    private fun isOverHotseat(ev: MotionEvent): Boolean {
        val dp = launcher.deviceProfile
        val hotseatHeight = dp.hotseatBarSizePx + dp.insets.bottom
        return ev.y >= launcher.dragLayer.height - hotseatHeight
    }

    private fun getSwipeDirection(ev: MotionEvent): Int {
        return when {
            controller.getSwipeUpOverride(ev.downTime) != null -> {
                hasSwipeUpOverride = true
                if (canInterceptTouch(ev))
                    SwipeDetector.DIRECTION_BOTH
                else
                    SwipeDetector.DIRECTION_POSITIVE
            }
            gesture.customSwipeUp && !isOverHotseat(ev) -> SwipeDetector.DIRECTION_BOTH
            gesture.customDockSwipeUp && isOverHotseat(ev) -> SwipeDetector.DIRECTION_BOTH
            else -> SwipeDetector.DIRECTION_NEGATIVE
        }
    }

    override fun onDragStart(start: Boolean) {
        state = GestureState.Free
    }

    override fun onDrag(displacement: Float, velocity: Float): Boolean {
        if (state != GestureState.Locked) {
            if (gesture.customSwipeDown) {
                if (velocity > triggerVelocity && state == GestureState.Free) {
                    state = GestureState.Triggered
                    gesture.onSwipeDown()
                }
            } else {
                if (velocity > triggerVelocity &&
                        (state == GestureState.Free || state == GestureState.NotificationClosed)) {
                    state = if (openNotifications()) GestureState.NotificationOpened else GestureState.Locked
                } else if (velocity < -notificationsCloseVelocity && state == GestureState.NotificationOpened) {
                    state = if (closeNotifications()) GestureState.NotificationClosed else GestureState.Locked
                }
            }

            if (velocity < -triggerVelocity && state == GestureState.Free) {
                controller.getSwipeUpOverride(downTime)?.let {
                    state = GestureState.Triggered
                    it.onGestureTrigger(controller)
                } ?: if (gesture.customSwipeUp) {
                    state = GestureState.Triggered
                    gesture.onSwipeUp()
                } else if (gesture.customDockSwipeUp) {
                    state = GestureState.Triggered
                    gesture.onDockSwipeUp()
                }
            }
        }
        return true
    }

    override fun onDragEnd(velocity: Float, fling: Boolean) {
        launcher.workspace.postDelayed(detector::finishedScrolling, 200)
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun openNotifications(): Boolean {
        return try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(launcher.getSystemService("statusbar"))
            true
        } catch (ex: ClassNotFoundException) {
            false
        } catch (ex: NoSuchMethodException) {
            false
        } catch (ex: IllegalAccessException) {
            false
        } catch (ex: InvocationTargetException) {
            false
        }
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun closeNotifications(): Boolean {
        return try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("collapsePanels")
                    .invoke(launcher.getSystemService("statusbar"))
            true
        } catch (ex: ClassNotFoundException) {
            false
        } catch (ex: NoSuchMethodException) {
            false
        } catch (ex: IllegalAccessException) {
            false
        } catch (ex: InvocationTargetException) {
            false
        }

    }
}
