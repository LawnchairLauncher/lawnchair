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
import com.android.launcher3.util.Executors
import kotlin.math.abs

/**
 * Reads the hinge angle sensor ([Sensor.TYPE_HINGE_ANGLE], API 30+) between [start] and [stop].
 *
 * The owner starts it only while the launcher is resumed on the large screen, so the sensor is never
 * on in the background or on the cover. [stop] forgets the last angle, so a reading never outlives
 * the session it came from.
 * Events can arrive faster than the display refreshes; they are coalesced and handed to [Listener]
 * at most once per frame, on the main thread. Nothing is allocated per event.
 *
 * What it learns about a posture-only sensor ([isContinuous]) is kept across restarts for the
 * settings ([isPostureOnly]).
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

    private val appContext = context.applicationContext
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

    /** Whether the hinge moved since [start]: a session that tells a posture-only sensor apart. */
    private var movedSinceStart = false

    val hasSensor: Boolean get() = sensor != null

    var isStarted = false
        private set

    /** Latest angle in degrees, or [Float.NaN] before the first event. */
    var angle = Float.NaN
        private set

    /** Whether the hinge is opening. It flips only after moving [DIRECTION_HYSTERESIS] degrees the other way. */
    var isOpening = false
        private set

    /**
     * [SystemClock.uptimeMillis] of the last event that moved the hinge by at least [MOVE_EPSILON]
     * degrees, or of the first event after [start]: the sensor starts with an unfold, so its first
     * reading may already be the hinge opening.
     */
    var lastMoveUptimeMillis = 0L
        private set

    /**
     * Whether the sensor reports a continuous angle. Some devices report only postures through it
     * (the Galaxy Z Fold3 on One UI 7 sends 0° closed and 90° open, nothing in between), which can't
     * drive a transition. It turns true with the first reading that isn't a multiple of
     * [POSTURE_STEP] degrees and stays true for this launcher instance.
     */
    var isContinuous = false
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
            // The hinge moved and every reading was a posture. A hinge at rest proves nothing: a
            // continuous sensor reads 180° flat too.
            if (movedSinceStart && !isContinuous) updatePostureOnly(appContext, postureSession = true)
        }
        if (framePending) {
            Choreographer.getInstance().removeFrameCallback(this)
            framePending = false
        }
        directionAnchor = Float.NaN
        moveAnchor = Float.NaN
        movedSinceStart = false
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
        if (!isContinuous && abs(value - POSTURE_STEP * Math.round(value / POSTURE_STEP)) >= MOVE_EPSILON) {
            isContinuous = true
            updatePostureOnly(appContext, postureSession = false)
        }
        if (moveAnchor.isNaN() || abs(value - moveAnchor) >= MOVE_EPSILON) {
            if (!moveAnchor.isNaN()) movedSinceStart = true
            lastMoveUptimeMillis = SystemClock.uptimeMillis()
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
        private const val POSTURE_STEP = 90f

        // Its own device-protected file, outside the backups
        private const val PREFS_NAME = "lc_hinge_sensor"
        private const val KEY_POSTURE_SESSIONS = "lc_posture_only_sessions"
        private const val POSTURE_ONLY_SESSIONS = 2

        /**
         * Whether this device's hinge sensor reports postures only: in [POSTURE_ONLY_SESSIONS]
         * large-screen sessions the hinge moved and no reading was continuous, and no continuous
         * reading came since. Reads a small file.
         */
        fun isPostureOnly(context: Context): Boolean = prefs(context).getInt(KEY_POSTURE_SESSIONS, 0) >= POSTURE_ONLY_SESSIONS

        /** Counts a posture-only session, or forgets them all after a continuous reading. Off the main thread. */
        private fun updatePostureOnly(context: Context, postureSession: Boolean) {
            Executors.UI_HELPER_EXECUTOR.execute {
                val prefs = prefs(context)
                val sessions = prefs.getInt(KEY_POSTURE_SESSIONS, 0)
                when {
                    !postureSession -> if (sessions > 0) prefs.edit().remove(KEY_POSTURE_SESSIONS).apply()
                    sessions < POSTURE_ONLY_SESSIONS -> prefs.edit().putInt(KEY_POSTURE_SESSIONS, sessions + 1).apply()
                }
            }
        }

        private fun prefs(context: Context) = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
