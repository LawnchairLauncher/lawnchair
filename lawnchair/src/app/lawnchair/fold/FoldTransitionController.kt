package app.lawnchair.fold

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Choreographer
import android.view.ViewTreeObserver
import android.view.animation.Interpolator
import androidx.annotation.RequiresApi
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.hotseat.HotseatFoldController
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.app.animation.Interpolators
import com.android.launcher3.BuildConfig
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherState
import com.android.launcher3.util.ActivityLifecycleCallbacksAdapter
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Plays the fold/unfold transition of the launcher and drives the dock merge
 * ([HotseatFoldController]) with it.
 *
 * Trigger: the DeviceProfile change that a fold or unfold causes: the launcher moves to the other
 * screen and its layout flips between the cover and the large one (with no model reload and no
 * activity recreation). Any other profile change, like a rotation or a display size change that
 * keeps the layout, plays nothing. The style is started synchronously from that change, before
 * the new layout is drawn, so the first frame on the new screen is already the first frame of the
 * transition.
 *
 * Progress source:
 * - The hinge angle ([HingeAngleProvider]) when `foldTrackHinge` is on, the device has the sensor
 *   and the hinge moves within [FoldTunables.HINGE_WAIT_MILLIS] of the change. Progress maps
 *   [FoldTunables.INNER_DISPLAY_ON_ANGLE]..[FoldTunables.FULLY_OPEN_ANGLE] to 0..1, smoothed. It may go
 *   back when the user changes direction. A hinge that stops for [FoldTunables.HINGE_IDLE_MILLIS]
 *   settles to the open state. On the inner display a fold also starts from the hinge, before the
 *   cover screen takes over.
 * - Otherwise a timer ([FoldTunables.UNFOLD_DURATION_MILLIS] / [FoldTunables.FOLD_DURATION_MILLIS],
 *   standard decelerate), so every device gets a transition.
 *
 * Dock contract (when the hotseat has a [HotseatFoldController]): [HotseatFoldController.onFoldStart]
 * once, then [HotseatFoldController.onFoldProgress] once per frame (progress is the absolute fold
 * state of the current layout, it may go back), then [HotseatFoldController.onFoldEnd] once, also
 * when cancelled.
 * The `opening` of start and progress is the direction the transition started in; the `opening`
 * of end is the final state (true = the large layout is showing). Unfold: 0 -> 1 on the large
 * layout. Fold without hinge tracking: 1 -> 0 on the cover layout, after it is laid out. Fold
 * tracked on the inner display: 1 -> 0 on the large layout, ended by the switch to the cover.
 *
 * Inert on phones, tablets, API < 29, and when Lawnchair is the recents provider: there, AOSP's
 * LauncherUnfoldAnimationController gets progress from SystemUI and owns the unfold animation, so
 * the two never animate the same views. The debug replay (`FoldReplay`, debug builds only) works
 * everywhere, through [startRunForDebug].
 *
 * Reduced motion (animator duration scale 0): no motion, Simple's fade in
 * [FoldTunables.REDUCED_MOTION_DURATION_MILLIS]. Any failure ends the transition at once with the final layout showing; debug builds
 * then rethrow it.
 *
 * While the app drawer is open, the [DrawerFoldStyle] of the drawer layout (river, book or zoom,
 * after `foldDrawerLayout`) replaces the selected home style for that transition.
 */
class FoldTransitionController(
    private val launcher: LawnchairLauncher,
) : DeviceProfile.OnDeviceProfileChangeListener,
    ActivityLifecycleCallbacksAdapter,
    HingeAngleProvider.Listener {

    private val hinge = HingeAngleProvider(launcher, this)
    private val prefs2 by lazy { PreferenceManager2.getInstance(launcher) }
    private val handler = Handler(Looper.getMainLooper())
    private val frameCallback = Choreographer.FrameCallback { onFrame(it) }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        onPreDraw()
        true
    }
    private val overlay = OverlayDrawable()

    private var attached = false
    private var resumed = false
    private var previewRequested = false
    private val previewRunnable = Runnable { startPreview() }
    private var lastFoldScreen = FoldScreen.NONE
    private var restAngle = Float.NaN
    private var preDrawObserver: ViewTreeObserver? = null
    private var run: Run? = null

    /**
     * What the drawer showed before the profile change being dispatched: [FoldDrawerState.capture]
     * sets it before the launcher dispatches the change, and the next [onDeviceProfileChanged] takes it.
     */
    internal var drawerCapture: FoldDrawerState.Capture? = null

    /**
     * Registers this controller with the launcher. Call once from `onCreate`, after `super.onCreate`.
     *
     * Activity callbacks are used rather than the androidx Lifecycle: Launcher calls
     * `onSaveInstanceState` on every configuration change, which moves that Lifecycle to CREATED
     * (an ON_PAUSE/ON_STOP right when a fold transition starts).
     */
    fun attach() {
        if (attached || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        attached = true
        lastFoldScreen = foldScreenOf(launcher.deviceProfile)
        launcher.addOnDeviceProfileChangeListener(this)
        launcher.registerActivityLifecycleCallbacks(this)
    }

    // Lifecycle

    override fun onActivityResumed(activity: Activity) {
        resumed = true
        restAngle = Float.NaN
        updateHingeSensor()
        if (previewRequested) launcher.rootView.postDelayed(previewRunnable, PREVIEW_DELAY_MILLIS)
    }

    override fun onActivityPaused(activity: Activity) {
        resumed = false
        hinge.stop()
        launcher.rootView.removeCallbacks(previewRunnable)
        finishNow("pause")
    }

    /**
     * Plays the selected style once as an unfold, in place on the large screen: "Try it on the
     * home screen" in the Foldable screen settings, which brings the launcher to the front first.
     * Ignored on the cover, in a window and while a transition runs.
     */
    fun requestPreview() {
        if (!attached) return
        previewRequested = true
        if (resumed) launcher.rootView.postDelayed(previewRunnable, PREVIEW_DELAY_MILLIS)
    }

    private fun startPreview() {
        if (!previewRequested) return
        previewRequested = false
        if (run != null || !isLarge() || !isEngineEnabled() || !canRunNow()) return
        startRun("preview", opening = true, large = true)
    }

    // Registered by attach() only, on API 29+
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onActivityDestroyed(activity: Activity) {
        finishNow("destroy")
        hinge.stop()
        launcher.removeOnDeviceProfileChangeListener(this)
        launcher.unregisterActivityLifecycleCallbacks(this)
    }

    // Triggers

    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        val capture = drawerCapture
        drawerCapture = null
        val foldScreen = foldScreenOf(dp)
        val previous = lastFoldScreen
        lastFoldScreen = foldScreen
        restAngle = Float.NaN
        updateHingeSensor()
        // Same screen (insets, rotation, grid, a resolution change on one screen) or a window: a
        // running transition picks up the new geometry.
        if (foldScreen == FoldScreen.NONE || previous == FoldScreen.NONE || foldScreen == previous) return

        val large = isLarge()
        val current = run
        val foldTrackedOnInner = current != null && !large && current.scene.largeLayout &&
            !current.scene.opening && current.allowHinge
        finishNow(if (large) "unfolded" else "folded")
        if (foldTrackedOnInner) return
        if (!isEngineEnabled() || !canRunNow()) return
        startRun(if (large) "unfold" else "fold", opening = large, followHinge = large, capture = capture)
    }

    override fun onHingeAngleChanged(angle: Float) {
        // A posture-only sensor jumps from open to closed: there is no fold to follow on the inner
        // screen, and the cover plays the timer fold after the switch.
        if (run != null || !isLarge() || !hinge.isContinuous) return
        if (restAngle.isNaN() || angle > restAngle) {
            restAngle = angle
            return
        }
        if (hinge.isOpening || restAngle - angle < FoldTunables.CLOSING_THRESHOLD_ANGLE) return
        if (!isEngineEnabled() || !canRunNow() || isReducedMotion()) return
        startRun("fold (hinge, inner display)", opening = false, large = true, phase = Phase.HINGE)
    }

    /**
     * The debug replay's entry (`FoldReplay`, debug builds only): plays the selected style in place,
     * timer-driven, [slowFactor] times slower. [timerMillis] and [curve] replace the timer's duration
     * (before the time scale) and curve, for a fold that opens again.
     */
    internal fun startRunForDebug(
        reason: String,
        opening: Boolean,
        large: Boolean,
        notify: Boolean,
        slowFactor: Float,
        timerMillis: Long? = null,
        curve: Interpolator = Interpolators.STANDARD_DECELERATE,
    ) = startRun(reason, opening, large, notify, slowFactor = slowFactor, timerMillis = timerMillis, curve = curve)

    // Running a transition

    /**
     * Starts an unfold ([opening]: 0 -> 1 on the large layout) or a fold (1 -> 0: on the cover layout
     * after the switch, on the large one while the hinge closes or in a replay), timer-driven unless
     * it starts in [Phase.HINGE]. With [followHinge] an unfold follows the hinge if it moves within
     * [FoldTunables.HINGE_WAIT_MILLIS]; [capture] is the drawer before the profile change that starts
     * the run; [slowFactor], [timerMillis] and [curve] are the debug replay's (see [startRunForDebug]).
     * Every path here starts from a callback that attach() registers, and attach() needs API 29, or
     * from the debug replay, which needs API 29 too.
     */
    @SuppressLint("NewApi")
    private fun startRun(
        reason: String,
        opening: Boolean,
        large: Boolean = opening,
        notify: Boolean = true,
        phase: Phase = Phase.TIMER,
        followHinge: Boolean = false,
        capture: FoldDrawerState.Capture? = null,
        slowFactor: Float = 1f,
        timerMillis: Long? = null,
        curve: Interpolator = Interpolators.STANDARD_DECELERATE,
    ) {
        finishNow("restart")
        val styleType = prefs2.foldTransitionStyle.firstCached()
        val reduced = isReducedMotion()
        val scene = FoldScene(
            launcher.rootView,
            launcher.dragLayer,
            launcher.workspace,
            launcher.resources.displayMetrics.density,
            opening,
            large,
        )
        // The timer follows the system animator duration scale, times the replay's slow factor
        val timeScale = if (phase == Phase.HINGE || reduced) 1f else durationScale() * slowFactor
        val millis = when {
            phase == Phase.HINGE -> 0L
            timerMillis != null -> (timerMillis * timeScale).toLong()
            reduced -> FoldTunables.REDUCED_MOTION_DURATION_MILLIS
            else -> ((if (opening) FoldTunables.UNFOLD_DURATION_MILLIS else FoldTunables.FOLD_DURATION_MILLIS) * timeScale).toLong()
        }
        val style = when {
            // Simple's fade: nothing moves
            reduced -> SimpleFoldStyle()

            // The drawer has its own transitions, which replace the home style while it is open.
            isDrawerOpen() -> when (drawerLayout()) {
                FoldDrawerLayout.EXPAND -> DrawerRiverStyle(launcher.appsView, launcher.deviceProfile, capture)
                FoldDrawerLayout.PAGES -> if (isBookFirstPageOnMovingHalf()) SimpleFoldStyle() else DrawerBookStyle(launcher.appsView, launcher.deviceProfile, capture)
                FoldDrawerLayout.BIG_ICONS -> DrawerZoomStyle(launcher.appsView, launcher.deviceProfile, capture)
            }

            else -> FoldStyle.create(styleType)
        }
        val from = if (opening) 0f else 1f
        val allowHinge = phase == Phase.HINGE || (followHinge && !reduced && hinge.isStarted)
        val r = Run(reason, scene, style, notify, allowHinge, SystemClock.uptimeMillis(), hinge.angle, phase, from, 1f - from, millis * NANOS_PER_MILLI, curve)
        run = r
        Log.i(TAG, "start: $reason, style=$styleType (${style.javaClass.simpleName}), large=$large, reducedMotion=$reduced, source=$phase, ${millis}ms")
        try {
            if (notify) {
                dock?.onFoldStart(opening)
                dispatchProgress(r)
            }
            if (!style.onStart(scene)) {
                Log.w(TAG, "${style.javaClass.simpleName} can't run here, showing the final layout")
                r.style = FoldStyle()
            } else {
                attachToViews(r)
            }
            launcher.rootView.invalidate()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } catch (t: Throwable) {
            fail("start", t)
        }
    }

    private fun attachToViews(r: Run) {
        val root = launcher.rootView
        val observer = root.viewTreeObserver
        observer.addOnPreDrawListener(preDrawListener)
        preDrawObserver = observer
        if (r.style.usesOverlay(r.scene)) {
            overlay.setBounds(0, 0, root.width, root.height)
            root.overlay.add(overlay)
            r.overlayAttached = true
        }
    }

    private fun detachFromViews(r: Run) {
        preDrawObserver?.let {
            if (it.isAlive) it.removeOnPreDrawListener(preDrawListener)
        }
        launcher.rootView.viewTreeObserver.let {
            if (it.isAlive) it.removeOnPreDrawListener(preDrawListener)
        }
        preDrawObserver = null
        if (r.overlayAttached) {
            r.overlayAttached = false
            launcher.rootView.overlay.remove(overlay)
        }
    }

    private fun onFrame(frameTimeNanos: Long) {
        val r = run ?: return
        try {
            val step = if (r.lastFrameNanos < 0) 0L else (frameTimeNanos - r.lastFrameNanos).coerceIn(0L, MAX_FRAME_STEP_NANOS)
            r.lastFrameNanos = frameTimeNanos
            r.ageNanos += step
            var done = advance(r, step, SystemClock.uptimeMillis())
            if (r.ageNanos > FoldTunables.MAX_TRANSITION_MILLIS * NANOS_PER_MILLI) {
                Log.w(TAG, "transition took too long, ending it")
                done = true
            }
            if (r.notify) dispatchProgress(r)
            if (done) {
                finishNow("done")
                return
            }
            launcher.rootView.invalidate()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } catch (t: Throwable) {
            fail("frame", t)
        }
    }

    /** Moves the progress one frame forward. Returns true when it reached its end. */
    private fun advance(r: Run, step: Long, now: Long): Boolean {
        when (r.phase) {
            Phase.TIMER -> {
                r.elapsedNanos += step
                val t = if (r.durationNanos <= 0) 1f else (r.elapsedNanos.toFloat() / r.durationNanos).coerceIn(0f, 1f)
                r.progress = r.from + (r.to - r.from) * r.interpolator.getInterpolation(t)
                if (t >= 1f) return true
                if (r.allowHinge && now - r.startUptimeMillis <= FoldTunables.HINGE_WAIT_MILLIS && hingeMovedSince(r)) {
                    r.phase = Phase.HINGE
                    Log.i(TAG, "hinge is moving (${hinge.angle}°), following it")
                }
            }

            Phase.HINGE -> {
                val angle = hinge.angle
                val target = if (angle.isNaN()) r.progress else angleToProgress(angle)
                val follow = 1f - exp(-step / (FoldTunables.HINGE_SMOOTHING_MILLIS * NANOS_PER_MILLI))
                r.progress += (target - r.progress) * follow
                if (abs(target - r.progress) < PROGRESS_EPSILON) r.progress = target
                if (BuildConfig.DEBUG) Log.d(TAG, "hinge angle=$angle° -> target=$target, progress=${r.progress}")
                // An unfold ends fully open; a fold tracked on the inner screen starts at 1 and is
                // ended by the switch to the cover (or settles open if the hinge stops).
                if (r.scene.opening && r.progress >= 1f) return true
                if (!hinge.isStarted || now - hinge.lastMoveUptimeMillis > FoldTunables.HINGE_IDLE_MILLIS) {
                    Log.i(TAG, "hinge stopped at $angle°, settling open")
                    val settle = max(MIN_SETTLE_MILLIS, (FoldTunables.SETTLE_DURATION_MILLIS * (1f - r.progress)).toLong())
                    r.phase = Phase.TIMER
                    r.from = r.progress
                    r.to = 1f
                    r.durationNanos = settle * NANOS_PER_MILLI
                    r.elapsedNanos = 0L
                    r.interpolator = Interpolators.STANDARD
                }
            }
        }
        return false
    }

    /**
     * Whether the hinge moved since [r] started, while opening the inner screen. The sensor starts
     * with the unfold (large screen only), so with no reading at the start ([Run.startAngle] NaN)
     * its first reading counts when it lies between the inner screen's on angle and fully open.
     */
    private fun hingeMovedSince(r: Run): Boolean {
        val angle = hinge.angle
        if (!hinge.isContinuous || angle.isNaN() || hinge.lastMoveUptimeMillis <= r.startUptimeMillis) return false
        if (angle < FoldTunables.INNER_DISPLAY_ON_ANGLE - HINGE_START_MARGIN || angle >= FoldTunables.FULLY_OPEN_ANGLE) return false
        return r.startAngle.isNaN() || abs(angle - r.startAngle) >= 1f
    }

    private fun onPreDraw() {
        val r = run ?: return
        try {
            val root = launcher.rootView
            if (root.width <= 0 || root.height <= 0) return
            val edge = FoldGeometry.movingEdgeFor(launcher.deviceProfile.deviceProperties.rotationHint)
            r.scene.geometry.update(root.width, root.height, edge)
            if (r.overlayAttached) overlay.setBounds(0, 0, root.width, root.height)
            r.style.onPreDraw(r.scene, r.progress)
        } catch (t: Throwable) {
            fail("preDraw", t)
        }
    }

    /** Ends the running transition now and shows the final layout. Safe to call any time. */
    fun finishNow(reason: String) {
        val r = run ?: return
        run = null
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        val large = isLarge()
        var failure: Throwable? = null
        try {
            detachFromViews(r)
        } catch (t: Throwable) {
            Log.e(TAG, "detaching the transition failed", t)
            failure = t
        }
        try {
            r.style.onEnd(r.scene)
        } catch (t: Throwable) {
            Log.e(TAG, "${r.style.javaClass.simpleName}.onEnd failed", t)
            failure = failure ?: t
        } finally {
            // What a style that throws could leave on the whole launcher: the DragLayer's clip,
            // blur, animation matrix and transition alpha. Nothing else in the launcher sets any of them.
            val dragLayer = launcher.dragLayer
            dragLayer.clipBounds = null
            dragLayer.animationMatrix = null
            dragLayer.transitionAlpha = 1f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dragLayer.setRenderEffect(null)
        }
        if (r.notify) {
            try {
                r.progress = if (large) 1f else 0f
                dispatchProgress(r)
                dock?.onFoldEnd(large)
            } catch (t: Throwable) {
                Log.e(TAG, "the dock failed at the end", t)
                failure = failure ?: t
            }
        }
        launcher.rootView.invalidate()
        if (large) restAngle = hinge.angle
        Log.i(TAG, "end: ${r.reason} ($reason), large=$large, phase=${r.phase}, ${r.ageNanos / NANOS_PER_MILLI}ms")
        // Debug builds surface the failure once the final layout shows
        if (BuildConfig.DEBUG && failure != null) throw failure
    }

    private fun fail(where: String, t: Throwable) {
        Log.e(TAG, "fold transition failed in $where, showing the final layout", t)
        finishNow("failure")
        if (BuildConfig.DEBUG) throw t
    }

    private fun dispatchProgress(r: Run) {
        if (r.progress == r.dispatchedProgress) return
        r.dispatchedProgress = r.progress
        dock?.onFoldProgress(r.progress, r.scene.opening)
    }

    /** The dock merge, on foldables: the only view besides the style that follows the progress. */
    private val dock: HotseatFoldController? get() = launcher.hotseat?.foldController

    // Conditions

    private fun isEngineEnabled(): Boolean = InvariantDeviceProfile.deviceType == InvariantDeviceProfile.TYPE_MULTI_DISPLAY &&
        !LawnchairApp.isRecentsEnabled

    private fun canRunNow(): Boolean = resumed && launcher.isStarted && !launcher.isDestroyed &&
        !launcher.isInMultiWindowMode && launcher.rootView.isAttachedToWindow

    private fun shouldTrackHinge(): Boolean = isEngineEnabled() && hinge.hasSensor && prefs2.foldTrackHinge.firstCached()

    /**
     * The hinge sensor runs only while the launcher is resumed on the large screen: that is where a
     * fold starts and where an unfold is followed (it is started by the unfold's profile change,
     * before the transition). On the cover nothing reads it.
     */
    private fun updateHingeSensor() {
        if (resumed && isLarge() && shouldTrackHinge()) hinge.start() else hinge.stop()
    }

    /** Whether the launcher shows the large screen's layout. */
    private fun isLarge(): Boolean = launcher.deviceProfile.deviceProperties.foldScreen == FoldScreen.LARGE

    private fun isReducedMotion(): Boolean = !ValueAnimator.areAnimatorsEnabled()

    /**
     * The screen [dp] fills: a fold or unfold changes it; a rotation, the insets or a display size
     * change that keeps the layout do not. [FoldScreen.NONE] for a window (multi-window mode), and
     * on phones and tablets.
     */
    private fun foldScreenOf(dp: DeviceProfile): FoldScreen = if (dp.deviceProperties.isMultiWindowMode) FoldScreen.NONE else dp.deviceProperties.foldScreen

    private fun isDrawerOpen(): Boolean = launcher.appsView != null &&
        launcher.stateManager.state == LauncherState.ALL_APPS &&
        !launcher.stateManager.isInTransition

    /** The drawer layout of the large screen, also when the transition plays on the cover. */
    private fun drawerLayout(): FoldDrawerLayout {
        val large = FoldLayout.largeProfileOf(launcher.deviceProfile) ?: return FoldDrawerLayout.EXPAND
        return FoldDrawerLayouts.drawerLayoutOf(large) ?: FoldDrawerLayout.EXPAND
    }

    /**
     * Whether the large screen's book drawer has its first page on the moving half ("Page order").
     * Its transition keeps the first page still, which needs it on the fixed half: else Simple's fade.
     */
    private fun isBookFirstPageOnMovingHalf(): Boolean {
        val large = FoldLayout.largeProfileOf(launcher.deviceProfile) ?: return false
        return FoldDrawerLayouts.isBookFirstPageOnMovingHalf(large)
    }

    private fun durationScale(): Float {
        val scale = Settings.Global.getFloat(launcher.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        return if (scale > 0f) min(scale, MAX_DURATION_SCALE) else 1f
    }

    private fun angleToProgress(angle: Float): Float {
        val closed = FoldTunables.INNER_DISPLAY_ON_ANGLE
        return ((angle - closed) / (FoldTunables.FULLY_OPEN_ANGLE - closed)).coerceIn(0f, 1f)
    }

    /**
     * TIMER: the timer drives the progress (from the start, or settling open after the hinge
     * stopped). HINGE: the hinge angle does.
     */
    private enum class Phase { TIMER, HINGE }

    /**
     * One transition. [allowHinge]: the hinge may drive it (a fold that started from the hinge, or
     * an unfold while the sensor runs). [elapsedNanos]: time on the timer. [interpolator]: the
     * timer's curve.
     */
    private class Run(
        val reason: String,
        val scene: FoldScene,
        var style: FoldStyle,
        val notify: Boolean,
        val allowHinge: Boolean,
        val startUptimeMillis: Long,
        val startAngle: Float,
        var phase: Phase,
        var from: Float,
        var to: Float,
        var durationNanos: Long,
        var interpolator: Interpolator,
    ) {
        var progress = from
        var dispatchedProgress = Float.NaN
        var elapsedNanos = 0L
        var lastFrameNanos = -1L
        var ageNanos = 0L
        var overlayAttached = false
    }

    /** Draws the style above the whole launcher, in the root view overlay. */
    private inner class OverlayDrawable : Drawable() {
        override fun draw(canvas: Canvas) {
            val r = run ?: return
            if (!canvas.isHardwareAccelerated) return
            try {
                r.style.onDrawOverlay(r.scene, canvas, r.progress)
            } catch (t: Throwable) {
                handler.post { fail("draw", t) }
            }
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val TAG = "LC-Fold"

        /** The intent action that asks the launcher for [requestPreview]. */
        const val ACTION_PREVIEW = "app.lawnchair.fold.PREVIEW"

        // After the launcher is back in front, once the settings window has gone
        private const val PREVIEW_DELAY_MILLIS = 450L

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val MAX_FRAME_STEP_NANOS = 34L * NANOS_PER_MILLI
        private const val MIN_SETTLE_MILLIS = 120L
        private const val PROGRESS_EPSILON = 0.001f

        private const val HINGE_START_MARGIN = 10f
        private const val MAX_DURATION_SCALE = 10f
    }
}
