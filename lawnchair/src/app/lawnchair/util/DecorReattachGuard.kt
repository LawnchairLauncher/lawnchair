package app.lawnchair.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.view.Window
import com.android.launcher3.logging.FileLog
import java.util.WeakHashMap

/**
 * Workaround for a framework crash on Android 15 and below:
 *
 * `IllegalArgumentException: View=DecorView[…] not attached to window manager`
 * thrown from `ActivityThread.handleResumeActivity` → `WindowManagerImpl.updateViewLayout`.
 *
 * The activity-side bookkeeping (`mWindowAdded`) can desync from `WindowManagerGlobal`:
 * a preserved-window relaunch (`mPreserveWindow`, used for config changes) forces
 * `mWindowAdded = true` even if the decor view was removed from the window manager in
 * the meantime, so the resume path calls `updateViewLayout` on a detached decor view and
 * the process dies. Current AOSP no longer calls `updateViewLayout` there — it routes
 * through `Activity.onWindowAttributesChanged`, which skips the update when the decor
 * has no parent — but older platform versions still make the raw call.
 *
 * `handleResumeActivity` runs `performResumeActivity` (which dispatches `onResume` and
 * the application lifecycle callbacks) before touching the window, so `onActivityResumed`
 * gets a chance to re-attach a stranded decor view first, using the same
 * `addView(decor, window.attributes)` call that `Activity.makeVisible()` makes.
 *
 * "Ever attached" is tracked per [Window] rather than per activity: a local relaunch
 * creates a new activity instance but reuses the same window and decor view, so per-
 * activity tracking would misread the desync as a first launch.
 *
 * The flag is armed on pause, not on the first resume: `onActivityResumed` runs before
 * the framework calls `addView`, so on the very first resume of a window its decor is
 * still detached and must not be marked. Pausing always happens after that first
 * `addView` and before a relaunch can strand the decor, so a window whose first
 * configuration change follows its first visible frame is still covered. A window that is
 * never attached (e.g. an activity that finishes during resume) is never marked and is
 * left alone, since adding its decor would be wrong.
 */
object DecorReattachGuard {

    private const val TAG = "DecorReattachGuard"

    /**
     * Windows whose decor view has been seen attached at least once. Weakly keyed so a destroyed
     * window is not leaked; only touched from lifecycle callbacks, i.e. on the main thread.
     */
    private val windowsEverAttached = WeakHashMap<Window, Boolean>()

    /**
     * Registers the callbacks that arm the guard and heal a stranded decor view. Call once from
     * [Application.onCreate] so every activity of the process is covered.
     */
    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                // Runs before the framework calls addView: a decor that is attached here was added
                // earlier, so it is safe to mark. A detached decor is only re-added when this window
                // has been seen attached, otherwise the framework is about to add it itself.
                override fun onActivityResumed(activity: Activity) {
                    val window = activity.window
                    if (markIfAttached(window)) return
                    val decor = window.peekDecorView() ?: return
                    if (windowsEverAttached[window] != true) return
                    if (activity.isFinishing || activity.isDestroyed) return
                    try {
                        activity.windowManager.addView(decor, window.attributes)
                        Log.w(TAG, "Re-attached stranded decor view of ${activity.javaClass.name}")
                        FileLog.w(TAG, "Re-attached stranded decor view of ${activity.javaClass.name}")
                    } catch (e: Exception) {
                        // If the view cannot be re-attached (e.g. its token is already dead),
                        // leave things as they are; the framework crash follows as before.
                        FileLog.w(TAG, "Failed to re-attach decor view of ${activity.javaClass.name}", e)
                    }
                }

                // The first resume of a window runs before the framework adds its decor, so it cannot
                // mark the window. Pause runs after that addView and before a configuration relaunch
                // can detach the decor again, which makes it the point where the guard gets armed.
                override fun onActivityPaused(activity: Activity) {
                    markIfAttached(activity.window)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {}
            },
        )
    }

    /** Marks [window] as ever attached when its decor currently is; returns that attached state. */
    private fun markIfAttached(window: Window): Boolean {
        val decor = window.peekDecorView() ?: return false
        if (!decor.isAttachedToWindow) return false
        windowsEverAttached[window] = true
        return true
    }
}
