package app.lawnchair.fold

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import kotlin.math.abs

/**
 * Reads the hinge angle sensor ([Sensor.TYPE_HINGE_ANGLE], API 30+) between [start] and [stop].
 *
 * The owner starts it only while the launcher is resumed on the large screen, so the sensor is never
 * on in the background or on the cover. [stop] forgets the last angle, so a reading never outlives
 * the session it came from.
 * Events can arrive faster than the display refreshes; they are coalesced and handed to [Listener]
 * at most once per frame, on the main thread. Nothing is allocated per event.
 */
class HingeAngleProvider(
    context: Context,
    private val listener: Listener,
) : SensorEventListener,
    Choreographer.FrameCallback {

    fun interface Listener {
        /** Latest hinge angle in degrees, 0 (closed) to 180 (flat). Called at most once per frame. */
        fun onHingeAngleChanged(angle: Float)
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
    } else {
        null
    }
    private val handler = Handler(Looper.getMainLooper())

    private var framePending = false
    private var directionAnchor = Float.NaN
    private var moveAnchor = Float.NaN

    val hasSensor: Boolean get() = sensor != null

    var isStarted = false
        private set

    /** Latest angle in degrees, or [Float.NaN] before the first event. */
    var angle = Float.NaN
        private set

    /** Whether the hinge is opening. It flips only after moving [DIRECTION_HYSTERESIS] degrees the other way. */
    var isOpening = false
        private set

    /** [SystemClock.uptimeMillis] of the last event that moved the hinge by at least [MOVE_EPSILON] degrees. */
    var lastMoveUptimeMillis = 0L
        private set

    fun start(): Boolean {
        if (isStarted) return true
        val manager = sensorManager ?: return false
        val sensor = sensor ?: return false
        isStarted = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME, handler)
        return isStarted
    }

    fun stop() {
        if (isStarted) {
            sensorManager?.unregisterListener(this)
            isStarted = false
        }
        if (framePending) {
            Choreographer.getInstance().removeFrameCallback(this)
            framePending = false
        }
        directionAnchor = Float.NaN
        moveAnchor = Float.NaN
        angle = Float.NaN
        isOpening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values[0].coerceIn(0f, 180f)
        when {
            directionAnchor.isNaN() -> directionAnchor = value

            isOpening == (value > directionAnchor) -> directionAnchor = value

            abs(value - directionAnchor) >= DIRECTION_HYSTERESIS -> {
                isOpening = !isOpening
                directionAnchor = value
            }
        }
        if (moveAnchor.isNaN() || abs(value - moveAnchor) >= MOVE_EPSILON) {
            if (!moveAnchor.isNaN()) lastMoveUptimeMillis = SystemClock.uptimeMillis()
            moveAnchor = value
        }
        angle = value
        if (!framePending) {
            framePending = true
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    override fun doFrame(frameTimeNanos: Long) {
        framePending = false
        if (isStarted && !angle.isNaN()) listener.onHingeAngleChanged(angle)
    }

    companion object {
        const val DIRECTION_HYSTERESIS = 2f
        const val MOVE_EPSILON = 0.5f
    }
}
