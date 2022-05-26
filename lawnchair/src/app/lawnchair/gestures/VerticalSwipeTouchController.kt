package app.lawnchair.gestures

import android.graphics.PointF
import android.view.MotionEvent
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.touch.BothAxesSwipeDetector
import com.android.launcher3.util.TouchController
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class VerticalSwipeTouchController(
    private val launcher: LawnchairLauncher,
    private val gestureController: GestureController
) : TouchController, BothAxesSwipeDetector.Listener {

    private val prefs = PreferenceManager2.getInstance(launcher)
    private val detector = BothAxesSwipeDetector(launcher, this)

    private var overrideSwipeUp = false
    private var overrideSwipeDown = false

    private var noIntercept = false
    private var currentMillis = 0L
    private var currentVelocity = 0f
    private var currentDisplacement = 0f

    private var triggered = false

    init {
        launcher.lifecycleScope.launch {
            prefs.swipeUpGestureHandler.get()
                .onEach { overrideSwipeUp = it != prefs.swipeUpGestureHandler.defaultValue }
                .launchIn(this)
            prefs.swipeDownGestureHandler.get()
                .onEach { overrideSwipeDown = it != prefs.swipeDownGestureHandler.defaultValue }
                .launchIn(this)
        }
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            noIntercept = !canInterceptTouch(ev)
            if (noIntercept) {
                return false
            }
            detector.setDetectableScrollConditions(getSwipeDirection(), false)
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
        if ((ev.edgeFlags and Utilities.EDGE_NAV_BAR) != 0) {
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
            if (velocity < 0) {
                gestureController.onSwipeUp()
            } else {
                gestureController.onSwipeDown()
            }
        }
        return true
    }

    override fun onDragEnd(velocity: PointF) {
        detector.finishedScrolling()
    }

    private fun getSwipeDirection(): Int {
        var directions = 0
        if (overrideSwipeUp) {
            directions = directions or BothAxesSwipeDetector.DIRECTION_UP
        }
        if (overrideSwipeDown) {
            directions = directions or BothAxesSwipeDetector.DIRECTION_DOWN
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
