package app.lawnchair.util

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.util.Log
import com.android.launcher3.BuildConfig
import com.android.launcher3.Launcher
import com.android.quickstep.views.RecentsView
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.system.ActivityManagerWrapper

class RecentHelper private constructor() {

    companion object {
        @JvmStatic
        private var sInstance: RecentHelper? = null
        private val TAG = RecentHelper::class.simpleName

        @Synchronized
        @JvmStatic
        fun getInstance(): RecentHelper {
            if (sInstance == null) {
                sInstance = RecentHelper()
            }
            return sInstance as RecentHelper
        }
    }

    fun clearAllTaskStacks(context: Context) {
        try {
            val launcher = Launcher.getLauncher(context)
            val recentsView = launcher.getOverviewPanel<RecentsView<Launcher, *>>()
            val taskViewCount = recentsView.getTaskViewCount()
            val currentUserId = Process.myUserHandle().getIdentifier()
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
                            packageName += "#" + UserHandle.getUserId(taskId)
                            val taskLockState = taskKey.baseIntent.component?.let {
                                TaskUtilLockState.getInstance().getTaskLockState(
                                    context,
                                    it,
                                    taskKey
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
            Log.e(TAG, "clearAllTaskStacks: ", exception)
            ActivityManagerWrapper.getInstance().removeAllRecentTasks()
        }
    }

    fun isAppLocked(packageName: String, context: Context): Boolean {
        val pref = context.getSharedPreferences(
            LawnchairLockedStateController.TASK_LOCK_STATE,
            Context.MODE_PRIVATE
        )

        val lockedApps = pref.getStringSet(
            LawnchairLockedStateController.TASK_LOCK_LIST_KEY_WITH_USERID,
            emptySet()
        )

        return lockedApps?.any { it.contains(packageName) } ?: false
    }

}

