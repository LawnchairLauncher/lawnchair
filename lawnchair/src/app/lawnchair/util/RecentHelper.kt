package app.lawnchair.util

import LawnchairLockedStateController
import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.content.Context
import android.os.Process
import android.os.UserHandle
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import com.android.launcher3.BuildConfig
import com.android.quickstep.views.RecentsView
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.system.ActivityManagerWrapper

object RecentHelper {

    fun clearAllTaskStacks(context: Context) {
        try {
            val launcher = context.launcher
            val recentsView = launcher.getOverviewPanel<RecentsView<LawnchairLauncher, *>>()
            val taskViewCount = recentsView.getTaskViewCount()
            val currentUserId = Process.myUid()
            for (i in 0..taskViewCount) {
                try {
                    val rawTasks = ActivityTaskManager.getInstance()
                        .getRecentTasks(i, ActivityManager.RECENT_IGNORE_UNAVAILABLE, currentUserId)
                    for (recentTaskInfo in rawTasks) {
                        var packageName = recentTaskInfo.baseIntent.component?.packageName
                        val taskKey = Task.TaskKey(recentTaskInfo)
                        val taskId = taskKey.id
                        packageName = packageName?.replace("unknown", "")
                        if (!packageName.isNullOrEmpty()) {
                            packageName += "#" + UserHandle.getUserHandleForUid(taskId)
                            val taskLockState = taskKey.baseIntent.component?.let {
                                TaskUtilLockState.getTaskLockState(
                                    context,
                                    it,
                                    taskKey,
                                )
                            }
                            if (!isAppLocked(packageName, context) &&
                                !packageName.contains(BuildConfig.APPLICATION_ID) &&
                                !taskLockState!!
                            ) {
                                ActivityManagerWrapper.getInstance().removeTask(taskId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (exception: Exception) {
            ActivityManagerWrapper.getInstance().removeAllRecentTasks()
        }
    }

    fun isAppLocked(packageName: String, context: Context): Boolean {
        val pref = context.getSharedPreferences(
            LawnchairLockedStateController.TASK_LOCK_STATE,
            Context.MODE_PRIVATE,
        )

        val lockedApps = pref.getStringSet(
            LawnchairLockedStateController.TASK_LOCK_LIST_KEY_WITH_USERID,
            emptySet(),
        )

        return lockedApps?.any { it.contains(packageName) } ?: false
    }
}
