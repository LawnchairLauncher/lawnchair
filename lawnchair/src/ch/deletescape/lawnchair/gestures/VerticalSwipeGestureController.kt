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
import ch.deletescape.lawnchair.gestures.handlers.VerticalSwipeGestureHandler

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

    private val controller by lazy { LawnchairLauncher.getLauncher(launcher).gestureController }
    private val gesture by lazy { controller.verticalSwipeGesture }
    private val detector by lazy { SwipeDetector(launcher, this, SwipeDetector.VERTICAL) }
    private var noIntercept = false

    private var swipeUpOverride: GestureHandler? = null
    private val hasSwipeUpOverride get() = swipeUpOverride != null
    private var state = GestureState.Free
    private var downTime = 0L
    private var downSent = false
    private var pointerCount = 0

    private var overrideDragging = false

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        downTime = ev.downTime
        val isDown = ev.actionMasked == MotionEvent.ACTION_DOWN
        val overrideAppeared = !hasSwipeUpOverride && controller.getSwipeUpOverride(ev.downTime) != null
        if (isDown || overrideAppeared) {
            swipeUpOverride = if (isDown) {
                downSent = false
                null
            } else {
                controller.getSwipeUpOverride(ev.downTime)
            }
            noIntercept = !canInterceptTouch() && !hasSwipeUpOverride
            if (noIntercept) {
                return false
            }
            detector.setDetectableScrollConditions(getSwipeDirection(ev), false)
        }
        if (noIntercept) {
            return false
        }
        val action = ev.action
        if (!isDown && !downSent) {
            ev.action = MotionEvent.ACTION_DOWN
        }
        downSent = true
        onControllerTouchEvent(ev)
        ev.action = action
        return detector.isDraggingOrSettling
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        pointerCount = ev.pointerCount
        return detector.onTouchEvent(ev)
    }

    private fun canInterceptTouch(): Boolean {
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
                if (canInterceptTouch())
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
        (swipeUpOverride as? VerticalSwipeGestureHandler)?.onDragStart(start)
        overrideDragging = true
    }

    override fun onDrag(displacement: Float, velocity: Float): Boolean {
        if (state != GestureState.Locked) {
            val wasFree = state == GestureState.Free
            if (overrideDragging) {
                (swipeUpOverride as? VerticalSwipeGestureHandler)?.onDrag(displacement, velocity)
            }
            if (gesture.customSwipeDown) {
                if (velocity > triggerVelocity && state == GestureState.Free) {
                    state = GestureState.Triggered
                    gesture.onSwipeDown()
                }
            } else {
                if (velocity > triggerVelocity &&
                        (state == GestureState.Free || state == GestureState.NotificationClosed)) {
                    state = if (openNotificationsOrQuickSettings()) GestureState.NotificationOpened else GestureState.Locked
                } else if (velocity < -notificationsCloseVelocity && state == GestureState.NotificationOpened) {
                    state = if (closeNotifications()) GestureState.NotificationClosed else GestureState.Locked
                }
            }

            if (wasFree && state == GestureState.NotificationOpened) {
                sendOnDragEnd(velocity, false)
            } else if (velocity < -triggerVelocity && state == GestureState.Free) {
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
        sendOnDragEnd(velocity, fling)
    }

    private fun sendOnDragEnd(velocity: Float, fling: Boolean) {
        if (overrideDragging) {
            (swipeUpOverride as? VerticalSwipeGestureHandler)?.onDragEnd(velocity, fling)
            overrideDragging = false
        }
    }

    private fun openNotificationsOrQuickSettings(): Boolean {
        return if (pointerCount > 1) openQuickSettings() else openNotifications()
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
    private fun openQuickSettings(): Boolean {
        return try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandSettingsPanel")
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
