package app.lawnchair.gestures

import android.graphics.PointF
import android.view.MotionEvent
// Removed androidx.lifecycle.lifecycleScope as init block that used it is removed
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.config.GestureHandlerConfig // Added import
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.LauncherState
import com.android.launcher3.Utilities
import com.android.launcher3.touch.BothAxesSwipeDetector
import com.android.launcher3.util.TouchController
import com.patrykmichalik.opto.core.firstBlocking // Added import
import kotlin.math.absoluteValue
// Removed kotlinx.coroutines.flow.launchIn, .onEach, .launch as init block removed

class VerticalSwipeTouchController(
    private val launcher: LawnchairLauncher,
    private val gestureController: GestureController,
) : TouchController,
    BothAxesSwipeDetector.Listener {

    private val prefs = PreferenceManager2.getInstance(launcher)
    private val detector = BothAxesSwipeDetector(launcher, this)

    // overrideSwipeUp and overrideSwipeDown fields removed

    private var noIntercept = false
    private var currentMillis = 0L
    private var currentVelocity = 0f
    // currentDisplacement is effectively const 0 as it's not updated in onDrag in original logic,
    // and used in `computeVelocity(displacement.y - currentDisplacement, ...)`.
    // Keeping it to maintain the call structure to computeVelocity for now.
    private val currentDisplacement = 0f

    private var triggered = false

    // init block removed as overrideSwipeUp/Down are no longer used by getSwipeDirection

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
        // Explicitly reset state for velocity calculation at the start of a new drag
        currentMillis = 0L
        currentVelocity = 0f
    }

    override fun onDrag(displacement: PointF, motionEvent: MotionEvent): Boolean {
        if (triggered) return true
        // Original call was computeVelocity(displacement.y - currentDisplacement, motionEvent.eventTime)
        // As currentDisplacement is effectively 0, this simplifies to computeVelocity(displacement.y, motionEvent.eventTime)
        val velocity = computeVelocity(displacement.y, motionEvent.eventTime)
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
        // Reset persistent state for velocity calculation after drag ends
        currentMillis = 0L
        currentVelocity = 0f
    }

    // MODIFIED getSwipeDirection
    private fun getSwipeDirection(): Int {
        var directions = 0
        if (prefs.swipeUpGestureHandler.firstBlocking() !is GestureHandlerConfig.NoOp) {
            directions = directions or BothAxesSwipeDetector.DIRECTION_UP
        }
        if (prefs.swipeDownGestureHandler.firstBlocking() !is GestureHandlerConfig.NoOp) {
            directions = directions or BothAxesSwipeDetector.DIRECTION_DOWN
        }
        return directions
    }

    private fun computeVelocity(delta: Float, millis: Long): Float {
        val previousMillis = currentMillis
        currentMillis = millis

        // If this is the first call in a drag sequence, previousMillis might be from a previous drag or 0.
        // To make deltaTimeMillis correct for the first frame of a new drag,
        // currentMillis (from onDragStart) should ideally be the event down time.
        // However, adhering to original variable flow where currentMillis is updated here.
        val deltaTimeMillis = (currentMillis - previousMillis).toFloat()
        val frameVelocity = if (deltaTimeMillis > 0) delta / deltaTimeMillis else 0f

        // Using kotlin.math.abs for clarity
        currentVelocity = if (kotlin.math.abs(currentVelocity) < 0.001f) {
            frameVelocity
        } else {
            val alpha = computeDampeningFactor(deltaTimeMillis)
            Utilities.mapRange(alpha, currentVelocity, frameVelocity)
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
