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
 * activity tracking would misread the desync as a first launch. A brand-new window
 * (cold start or full relaunch) is never marked and is left alone, since the framework
 * runs `addView` itself right after and adding the view twice would throw.
 */
object DecorReattachGuard {

    private const val TAG = "DecorReattachGuard"

    private val windowsEverAttached = WeakHashMap<Window, Boolean>()

    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    val window = activity.window
                    val decor = window.peekDecorView() ?: return
                    if (decor.isAttachedToWindow) {
                        windowsEverAttached[window] = true
                        return
                    }
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

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {}
            },
        )
    }
}
