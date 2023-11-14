package app.lawnchair.overview

import android.app.ActivityManagerNative
import android.os.Process
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.android.launcher3.BaseDraggingActivity
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.popup.SystemShortcut
import com.android.quickstep.TaskShortcutFactory
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer

object TaskShortcutFactory {
    class KillSystemShortcut(
        activity: BaseDraggingActivity,
        taskContainer: TaskIdAttributeContainer,
    ) : SystemShortcut<BaseDraggingActivity>(
        R.drawable.ic_close, R.string.task_menu_force_stop,
        activity, taskContainer.itemInfo, taskContainer.taskView
    ) {
        private val mTaskView: TaskView = taskContainer.taskView
        private val mActivity: BaseDraggingActivity = activity
        override fun onClick(view: View) {
            val iam = ActivityManagerNative.getDefault()
            val task = mTaskView.task
            if (task != null) {
                try {
                    iam.forceStopPackage(task.key.packageName, UserHandle.USER_CURRENT)
                    val appKilled = Toast.makeText(
                        mActivity, R.string.task_menu_force_stop,
                        Toast.LENGTH_SHORT
                    )
                    appKilled.show()
                    val rv: RecentsView<Launcher, *> = mActivity.getOverviewPanel()
                    rv.dismissTask(mTaskView, true, true)
                } catch (e: RemoteException) {
                    Log.e("KillSystemShortcut", "onClick: ", e.cause)
                }
            }
            dismissTaskMenuView(mActivity)
        }
    }

    @JvmField
    var KILL_APP: TaskShortcutFactory = object : TaskShortcutFactory {
        override fun getShortcuts(
            activity: BaseDraggingActivity,
            taskContainer: TaskIdAttributeContainer
        ): List<SystemShortcut<*>> {
            return listOf(
                KillSystemShortcut(activity, taskContainer)
            )
        }
    }
}