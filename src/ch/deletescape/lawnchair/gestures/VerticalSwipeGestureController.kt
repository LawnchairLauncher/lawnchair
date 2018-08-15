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

    private val gesture = LawnchairLauncher.getLauncher(launcher).gestureController.verticalSwipeGesture
    private val detector = SwipeDetector(launcher, this, SwipeDetector.VERTICAL)
    private var noIntercept = false

    private var state = GestureState.Free

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            noIntercept = !canInterceptTouch(ev)
            if (noIntercept) {
                return false
            }
            detector.setDetectableScrollConditions(getSwipeDirection(), false)
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
                launcher.isInState(LauncherState.NORMAL) && !isOverHotseat(ev)
    }

    private fun isOverHotseat(ev: MotionEvent): Boolean {
        val dp = launcher.deviceProfile
        val hotseatHeight = dp.hotseatBarSizePx + dp.insets.bottom
        return ev.y >= launcher.dragLayer.height - hotseatHeight
    }

    private fun getSwipeDirection(): Int {
        return if (gesture.customSwipeUp) SwipeDetector.DIRECTION_BOTH
        else SwipeDetector.DIRECTION_NEGATIVE
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

            if (gesture.customSwipeUp) {
                if (velocity < -triggerVelocity && state == GestureState.Free) {
                    state = GestureState.Triggered
                    gesture.onSwipeUp()
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
