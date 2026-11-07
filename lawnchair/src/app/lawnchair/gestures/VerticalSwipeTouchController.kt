package app.lawnchair.gestures

import android.graphics.PointF
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.touch.BothAxesSwipeDetector
import com.android.launcher3.util.TouchController
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class VerticalSwipeTouchController(
    private val launcher: LawnchairLauncher,
    private val gestureController: GestureController,
) : TouchController,
    BothAxesSwipeDetector.Listener {

    private val prefs = PreferenceManager2.getInstance(launcher)
    private val detector = BothAxesSwipeDetector(launcher, this)

    private var overrideSwipeUp = false
    private var overrideSwipeDown = false
    private var overrideTwoFingerSwipeUp = false
    private var overrideTwoFingerSwipeDown = false

    private var noIntercept = false
    private var currentMillis = 0L
    private var currentVelocity = 0f
    private var currentDisplacement = 0f

    private var pointerCount = 0
    private var maxPointerCount = 0
    private var triggered = false

    // LC: a touch that starts on a paged dock belongs to the pager while it is mostly horizontal.
    private val touchSlop = ViewConfiguration.get(launcher).scaledTouchSlop
    private var downOnPagedDock = false
    private var downX = 0f
    private var downY = 0f

    init {
        launcher.lifecycleScope.launch {
            prefs.swipeUpGestureHandler.get()
                .onEach { overrideSwipeUp = it != prefs.swipeUpGestureHandler.defaultValue && it !is GestureHandlerConfig.OpenAppDrawer }
                .launchIn(this)
            prefs.swipeDownGestureHandler.get()
                .onEach { overrideSwipeDown = it != prefs.swipeDownGestureHandler.defaultValue && it !is GestureHandlerConfig.OpenNotifications }
                .launchIn(this)
            // Override when a custom gesture handler is configured, as launcher does not handle these by default
            prefs.twoFingerSwipeUpGestureHandler.get()
                .onEach { overrideTwoFingerSwipeUp = it !is GestureHandlerConfig.NoOp }
                .launchIn(this)
            prefs.twoFingerSwipeDownGestureHandler.get()
                .onEach { overrideTwoFingerSwipeDown = it !is GestureHandlerConfig.NoOp }
                .launchIn(this)
        }
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            noIntercept = !canInterceptTouch(ev)
            if (noIntercept) {
                return false
            }
            downOnPagedDock = isOnPagedDock(ev)
            downX = ev.x
            downY = ev.y
            maxPointerCount = 1
        } else if (downOnPagedDock && !noIntercept &&
            HorizontalSwipeFilter.isHorizontalSwipe(ev.x - downX, ev.y - downY, touchSlop / 2f)
        ) {
            noIntercept = true
        }
        if (noIntercept) {
            return false
        }
        pointerCount = ev.pointerCount
        maxPointerCount = maxOf(maxPointerCount, pointerCount)
        val effectivePointerCount = if (maxPointerCount >= 2) 2 else pointerCount
        detector.setDetectableScrollConditions(getSwipeDirection(effectivePointerCount), false)
        onControllerTouchEvent(ev)
        return detector.isDraggingOrSettling
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        // We don't need to check when the pointer count changes during a swipe
        pointerCount = ev.pointerCount
        maxPointerCount = maxOf(maxPointerCount, pointerCount)
        return detector.onTouchEvent(ev)
    }

    private fun isOnPagedDock(ev: MotionEvent): Boolean {
        val hotseat = launcher.hotseat ?: return false
        return hotseat.pagedView.isPagingEnabled && launcher.dragLayer.isEventOverView(hotseat, ev)
    }

    private fun canInterceptTouch(ev: MotionEvent): Boolean {
        if ((ev.edgeFlags and Utilities.EDGE_NAV_BAR) != 0) {
            return false
        }
        // LC: Icon Swipe Gestures (For vertical gestures)
        val isIconSwipe = launcher.workspace?.let {
            val coord = floatArrayOf(ev.x, ev.y)
            launcher.dragLayer.mapCoordInSelfToDescendant(it, coord)
            it.isTouchOnIconWithSwipeGesture(coord[0], coord[1], true)
        } ?: false
        if (isIconSwipe) {
            return false
        }
        return AbstractFloatingView.getTopOpenView(launcher) == null &&
            launcher.isInState(LauncherState.NORMAL)
    }

    override fun onDragStart(start: Boolean) {
        triggered = false
    }

    override fun onDrag(displacement: PointF, motionEvent: MotionEvent): Boolean {
        if (triggered) return true
        val velocity = computeVelocity(displacement.y - currentDisplacement, motionEvent.eventTime)
        if (velocity.absoluteValue > TRIGGER_VELOCITY) {
            triggered = true
            val effectivePointerCount = if (maxPointerCount >= 2) 2 else pointerCount
            if (velocity < 0) {
                if (effectivePointerCount == 1 && overrideSwipeUp) {
                    gestureController.onSwipeUp()
                } else if (effectivePointerCount == 2 && overrideTwoFingerSwipeUp) {
                    gestureController.onTwoFingerSwipeUp()
                }
            } else {
                if (effectivePointerCount == 1 && overrideSwipeDown) {
                    gestureController.onSwipeDown()
                } else if (effectivePointerCount == 2 && overrideTwoFingerSwipeDown) {
                    gestureController.onTwoFingerSwipeDown()
                }
            }
        }
        return true
    }

    override fun onDragEnd(velocity: PointF) {
        detector.finishedScrolling()
        maxPointerCount = 0
    }

    private fun getSwipeDirection(pointerCount: Int): Int {
        var directions = 0
        when (pointerCount) {
            1 -> {
                if (overrideSwipeUp) {
                    directions = directions or BothAxesSwipeDetector.DIRECTION_UP
                }
                if (overrideSwipeDown) {
                    directions = directions or BothAxesSwipeDetector.DIRECTION_DOWN
                }
            }

            2 -> {
                if (overrideTwoFingerSwipeUp) {
                    directions = directions or BothAxesSwipeDetector.DIRECTION_UP
                }
                if (overrideTwoFingerSwipeDown) {
                    directions = directions or BothAxesSwipeDetector.DIRECTION_DOWN
                }
            }
        }
        return directions
    }

    private fun computeVelocity(delta: Float, millis: Long): Float {
        val previousMillis = currentMillis
        currentMillis = millis

        val deltaTimeMillis = (currentMillis - previousMillis).toFloat()
        val velocity = if (deltaTimeMillis > 0) delta / deltaTimeMillis else 0f
        currentVelocity = if (currentVelocity.absoluteValue < 0.001f) {
            velocity
        } else {
            val alpha = computeDampeningFactor(deltaTimeMillis)
            Utilities.mapRange(alpha, currentVelocity, velocity)
        }
        return currentVelocity
    }

    /**
     * Returns a time-dependent dampening factor using delta time.
     */
    private fun computeDampeningFactor(deltaTime: Float): Float {
        return deltaTime / (SCROLL_VELOCITY_DAMPENING_RC + deltaTime)
    }

    companion object {
        private const val SCROLL_VELOCITY_DAMPENING_RC = 1000f / (2f * Math.PI.toFloat() * 10f)
        private const val TRIGGER_VELOCITY = 2.25f
    }
}
