package app.lawnchair.compatlib;

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.window.TaskSnapshot

abstract class ActivityManagerCompat {

    companion object {
        const val NUM_RECENT_ACTIVITIES_REQUEST = 3
    }

    abstract fun invalidateHomeTaskSnapshot(homeActivity: Activity)

    /**
     * Called only in S+ platform
     * @param taskId
     * @param isLowResolution
     * @param takeSnapshotIfNeeded
     * @return
     */
    open fun getTaskSnapshot(taskId: Int, isLowResolution: Boolean, takeSnapshotIfNeeded: Boolean): TaskSnapshot? {
        return null
    }

    abstract fun startRecentsActivity(intent: Intent, eventTime: Long, runnerCompat: RecentsAnimationRunnerCompat)

    abstract fun getRunningTask(filterOnlyVisibleRecents: Boolean): ActivityManager.RunningTaskInfo?

    abstract fun getRecentTasks(numTasks: Int, userId: Int): List<ActivityManager.RecentTaskInfo>

    abstract fun getRunningTasks(filterOnlyVisibleRecents: Boolean): Array<ActivityManager.RunningTaskInfo>?
}
