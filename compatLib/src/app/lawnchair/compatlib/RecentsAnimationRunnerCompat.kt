package app.lawnchair.compatlib;

import android.graphics.Rect
import android.view.IRecentsAnimationController
import android.view.RemoteAnimationTarget
import android.window.TaskSnapshot

interface RecentsAnimationRunnerCompat {

    fun onAnimationStart(
        controller: IRecentsAnimationController,
        apps: Array<RemoteAnimationTarget>,
        wallpapers: Array<RemoteAnimationTarget>,
        homeContentInsets: Rect,
        minimizedHomeBounds: Rect
    )

    /**
     * Called only in T platform
     */
    fun onAnimationCanceled(taskIds: IntArray, taskSnapshots: Array<TaskSnapshot>)

    /**
     * Called only in Q/R/S platform
     */
    fun onAnimationCanceled(taskSnapshot: Any)

    /**
     * Called only in R/S platform
     */
    fun onTaskAppeared(app: RemoteAnimationTarget)

    /**
     * Called only in T+ platform
     */
    fun onTasksAppeared(apps: Array<RemoteAnimationTarget>)
}
