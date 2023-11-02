package app.lawnchair.util

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.android.systemui.shared.recents.model.Task
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TaskUtilLockState private constructor() {
    private val mLockedApps: MutableList<String> = ArrayList()
    private val mIoExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun setTaskLockState(context: Context, componentName: ComponentName, isState: Boolean, taskKey: Task.TaskKey) {
        LawnchairLockedStateController.getInstance(context).setTaskLockState(componentName.toShortString(), isState, taskKey.userId)
        val formatLockedAppStr = toFormatLockedAppStr(componentName.packageName, taskKey.userId)
        if (isState) {
            addLockedApp(formatLockedAppStr)
        } else {
            removeLockedApp(formatLockedAppStr)
        }
    }

    private fun toFormatLockedAppStr(packageName: String, userId: Int): String {
        return "$packageName$SEPARATOR$userId"
    }

    private fun saveLockedApps(lockedApps: List<String>?) {
        lockedApps?.let {
            mIoExecutor.execute { saveListToFileSync(it) }
        }
    }

    private fun saveListToFileSync(lockedApps: List<String>) {
        val bundle = Bundle()
        bundle.putStringArrayList(RECENT_LOCK_LIST, ArrayList(lockedApps))
    }

    private fun addLockedApp(appStr: String) {
        if (!mLockedApps.contains(appStr)) {
            Log.d(TAG, "addLockedApp: $appStr")
            mLockedApps.add(appStr)
            saveLockedApps(mLockedApps)
        }
    }

    private fun removeLockedApp(appStr: String) {
        if (mLockedApps.contains(appStr)) {
            Log.d(TAG, "removeLockedApp: $appStr")
            mLockedApps.remove(appStr)
            saveLockedApps(mLockedApps)
        }
    }

    fun getTaskLockState(context: Context, componentName: ComponentName, taskKey: Task.TaskKey): Boolean {
        return updateSpecifiedTaskLockState(context, componentName, taskKey)
    }

    private fun updateSpecifiedTaskLockState(context: Context, componentName: ComponentName, taskKey: Task.TaskKey): Boolean {
        val taskLockState = LawnchairLockedStateController.getInstance(context)
            .getTaskLockState(componentName.toShortString(), taskKey.userId)
        Log.d(TAG, "updateSpecifiedTaskLockState: Checking if the task is locked: $taskLockState")
        if (taskLockState) {
            setTaskLockState(context, taskKey.baseIntent.component, taskLockState, taskKey)
            Log.i(TAG, "updateSpecifiedTaskLockState: Task is locked, clearing the lock state.")
        }
        return taskLockState
    }

    companion object {
        private const val TAG = "TaskUtilLockState"
        private const val SEPARATOR = "#"
        private const val RECENT_LOCK_LIST = "recent_lock_list"

        @JvmStatic
        private var sInstance: TaskUtilLockState? = null

        @JvmStatic
        fun getInstance(): TaskUtilLockState {
            if (sInstance == null) {
                sInstance = TaskUtilLockState()
            }
            return sInstance as TaskUtilLockState
        }
    }
}
