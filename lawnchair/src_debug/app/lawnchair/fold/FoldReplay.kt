package app.lawnchair.fold

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.Interpolator
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import com.android.app.animation.Interpolators
import com.android.launcher3.LauncherState
import com.android.launcher3.util.ActivityLifecycleCallbacksAdapter

/**
 * Debug builds only (release builds have a no-op twin in `lawnchair/src_release`): replays the
 * selected fold transition in place, timer-driven, on any device, while the debug menu is on.
 * Started from the debug menu ([Preferences]) or with
 * `adb shell am start -n app.lawnchair.debug/app.lawnchair.LawnchairLauncher -a app.lawnchair.fold.REPLAY --es mode <key>`,
 * where the key is one of [ReplayMode]'s.
 *
 * On the large layout: unfold = 0 -> 1; fold = 1 -> 0, a short hold, then back to 1, so the
 * layout ends as it started. On the cover layout (or a phone): fold = the cover-side 1 -> 0;
 * unfold = the large-layout visuals drawn on this screen, without dock events (the layout does not
 * actually change, so the dock must not be told it is unfolded).
 */
object FoldReplay {

    private const val TAG = "LC-Fold"
    private const val ACTION_REPLAY = "app.lawnchair.fold.REPLAY"
    private const val EXTRA_MODE = "mode"

    /** Pause between folding and opening again in the "fold" replay. */
    private const val HOLD_MILLIS = 250L

    /** Slow motion factor of the slow replays. */
    private const val SLOW_FACTOR = 5f

    private const val DELAY_AFTER_RESUME_MS = 500L
    private const val DELAY_WHILE_RESUMED_MS = 250L
    private const val DELAY_AFTER_DRAWER_MS = 400L

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Pending? = null

    /** Modes of the replay. The drawer ones open the app drawer first. */
    private enum class ReplayMode(val key: String, val opening: Boolean, val slow: Boolean, val drawer: Boolean = false) {
        UNFOLD("unfold", true, false),
        FOLD("fold", false, false),
        UNFOLD_SLOW("unfold_slow", true, true),
        FOLD_SLOW("fold_slow", false, true),
        UNFOLD_DRAWER("unfold_drawer", true, false, true),
        FOLD_DRAWER("fold_drawer", false, false, true),
        UNFOLD_DRAWER_SLOW("unfold_drawer_slow", true, true, true),
        FOLD_DRAWER_SLOW("fold_drawer_slow", false, true, true),
        ;

        companion object {
            fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: UNFOLD
        }
    }

    /** Handles the replay intent. Returns true if the intent was ours. */
    fun onNewIntent(launcher: LawnchairLauncher, intent: Intent): Boolean {
        if (intent.action != ACTION_REPLAY) return false
        if (!PreferenceManager.getInstance(launcher).enableDebugMenu.get()) {
            Log.w(TAG, "Replay ignored: the debug menu is off")
            return true
        }
        // The engine needs API 29
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        pending?.cancel()
        pending = Pending(launcher, ReplayMode.fromKey(intent.getStringExtra(EXTRA_MODE))).also { it.request() }
        return true
    }

    /** The replays in the debug menu. */
    @Composable
    internal fun Preferences() {
        val context = LocalContext.current
        PreferenceGroup(heading = "Foldable") {
            ClickablePreference(
                label = "Replay fold transition",
                subtitle = "Unfold, with the selected animation, on the current layout",
                onClick = { request(context, ReplayMode.UNFOLD) },
            )
            ClickablePreference(
                label = "Replay fold transition (fold)",
                subtitle = "Fold, then open again",
                onClick = { request(context, ReplayMode.FOLD) },
            )
            ClickablePreference(
                label = "Replay fold transition (slow)",
                subtitle = "Unfold at a fifth of the speed",
                onClick = { request(context, ReplayMode.UNFOLD_SLOW) },
            )
            ClickablePreference(
                label = "Replay fold transition (fold, slow)",
                subtitle = "Fold and open again at a fifth of the speed",
                onClick = { request(context, ReplayMode.FOLD_SLOW) },
            )
            ClickablePreference(
                label = "Replay fold transition (drawer, slow)",
                subtitle = "Opens the app drawer, then unfolds it at a fifth of the speed",
                onClick = { request(context, ReplayMode.UNFOLD_DRAWER_SLOW) },
            )
            ClickablePreference(
                label = "Replay fold transition (drawer, fold, slow)",
                subtitle = "Opens the app drawer, then folds and opens it again slowly",
                onClick = { request(context, ReplayMode.FOLD_DRAWER_SLOW) },
            )
        }
    }

    /** Brings the launcher to the front and replays the transition there. */
    private fun request(context: Context, mode: ReplayMode) {
        val intent = Intent(context, LawnchairLauncher::class.java)
            .setAction(ACTION_REPLAY)
            .putExtra(EXTRA_MODE, mode.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun play(launcher: LawnchairLauncher, mode: ReplayMode) {
        val engine = launcher.foldTransitionController
        val large = launcher.deviceProfile.deviceProperties.isTablet
        val reason = "replay $mode"
        val slowFactor = if (mode.slow) SLOW_FACTOR else 1f
        when {
            mode.opening -> engine.startRunForDebug(reason, opening = true, large = true, notify = large, slowFactor = slowFactor)

            !large -> engine.startRunForDebug(reason, opening = false, large = false, notify = true, slowFactor = slowFactor)

            else -> {
                // Fold, hold, open again, along one timer
                val fold = if (ValueAnimator.areAnimatorsEnabled()) FoldTunables.FOLD_DURATION_MILLIS else FoldTunables.REDUCED_MOTION_DURATION_MILLIS
                val millis = 2 * fold + HOLD_MILLIS
                val curve = foldAndOpen(fold / millis.toFloat(), HOLD_MILLIS / millis.toFloat())
                engine.startRunForDebug(reason, opening = false, large = true, notify = true, slowFactor = slowFactor, timerMillis = millis, curve = curve)
            }
        }
    }

    /**
     * How far from open the fold replay on the large layout is, over its timer: it folds for the
     * first [fold] of it, stays folded for [hold], then opens again.
     */
    private fun foldAndOpen(fold: Float, hold: Float) = Interpolator { x ->
        when {
            x < fold -> Interpolators.STANDARD_DECELERATE.getInterpolation(x / fold)
            x < fold + hold -> 1f
            else -> 1f - Interpolators.STANDARD.getInterpolation((x - fold - hold) / (1f - fold - hold))
        }
    }

    /** A replay waiting for the launcher to be resumed, and for the drawer modes, the drawer to open. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private class Pending(
        private val launcher: LawnchairLauncher,
        private val mode: ReplayMode,
    ) : ActivityLifecycleCallbacksAdapter,
        Runnable {

        private var openedDrawer = false

        fun request() {
            launcher.registerActivityLifecycleCallbacks(this)
            if (launcher.hasBeenResumed()) schedule(DELAY_WHILE_RESUMED_MS)
        }

        override fun onActivityResumed(activity: Activity) = schedule(DELAY_AFTER_RESUME_MS)

        override fun onActivityPaused(activity: Activity) = cancel()

        override fun run() {
            if (mode.drawer && !openedDrawer && launcher.stateManager.state != LauncherState.ALL_APPS) {
                // Open the drawer, then replay once it has settled
                openedDrawer = true
                launcher.stateManager.goToState(LauncherState.ALL_APPS, false)
                schedule(DELAY_AFTER_DRAWER_MS)
                return
            }
            cancel()
            play(launcher, mode)
        }

        fun cancel() {
            handler.removeCallbacks(this)
            launcher.unregisterActivityLifecycleCallbacks(this)
            if (pending === this) pending = null
        }

        private fun schedule(delayMillis: Long) {
            handler.removeCallbacks(this)
            handler.postDelayed(this, delayMillis)
        }
    }
}
