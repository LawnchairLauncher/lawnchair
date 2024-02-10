package app.lawnchair.overview

import android.app.ActivityManagerNative
import android.app.ActivityOptions
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManagerGlobal
import android.widget.Toast
import app.lawnchair.LawnchairLauncher
import app.lawnchair.compat.LawnchairQuickstepCompat
import app.lawnchair.compatlib.eleven.WindowManagerCompatVR
import com.android.launcher3.BaseDraggingActivity
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.popup.SystemShortcut
import com.android.quickstep.TaskShortcutFactory
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.TaskThumbnailView
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture
import com.android.systemui.shared.recents.view.RecentsTransition
import com.android.systemui.shared.system.ActivityManagerWrapper

object TaskShortcutFactory {

    class LegacySplitSystemShortcut(
        iconRes: Int,
        textRes: Int,
        activity: BaseDraggingActivity,
        taskContainer: TaskIdAttributeContainer,
        private val mLauncherEvent: LauncherEvent,
    ) : SystemShortcut<BaseDraggingActivity?>(
        iconRes,
        textRes,
        activity,
        taskContainer.itemInfo,
        taskContainer.taskView,
    ) {
        private val mHandler: Handler = Handler(Looper.getMainLooper())
        private val mRecentsView: RecentsView<*, *> = activity.getOverviewPanel()
        private val mThumbnailView: TaskThumbnailView = taskContainer.thumbnailView
        private val mTaskView: TaskView = taskContainer.taskView

        private fun makeLaunchOptions(activity: BaseDraggingActivity): ActivityOptions? {
            val navBarPosition: Int = WindowManagerCompatVR.getNavBarPosition(activity.displayId)
            if (navBarPosition == WindowManagerCompatVR.NAV_BAR_POS_INVALID) {
                return null
            }
            val options = ActivityOptions.makeBasic()
            options.launchWindowingMode = WindowManagerCompatVR.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY
            return options
        }

        override fun onClick(view: View) {
            val taskKey = mTaskView.task!!.key
            val taskId = taskKey.id
            dismissTaskMenuView(mTarget)
            val options = makeLaunchOptions(mTarget!!)
            if (options != null && ActivityManagerWrapper.getInstance().startActivityFromRecents(taskId, options)) {
                val animStartedListener =
                    Runnable { mRecentsView.dismissTask(mTaskView, false, false) }
                val position = IntArray(2)
                mThumbnailView.getLocationOnScreen(position)
                val width = (mThumbnailView.width * mTaskView.scaleX).toInt()
                val height = (mThumbnailView.height * mTaskView.scaleY).toInt()
                val taskBounds = Rect(
                    position[0],
                    position[1],
                    position[0] + width,
                    position[1] + height,
                )

                // Take the thumbnail of the task without a scrim and apply it back after
                val alpha = mThumbnailView.dimAlpha
                mThumbnailView.dimAlpha = 0f
                val thumbnail = RecentsTransition.drawViewIntoHardwareBitmap(
                    taskBounds.width(),
                    taskBounds.height(),
                    mThumbnailView,
                    1f,
                    Color.BLACK,
                )
                mThumbnailView.dimAlpha = alpha
                val future: AppTransitionAnimationSpecsFuture = object : AppTransitionAnimationSpecsFuture(mHandler) {
                    override fun composeSpecs(): List<AppTransitionAnimationSpecCompat> {
                        return listOf(
                            AppTransitionAnimationSpecCompat(
                                taskId,
                                thumbnail,
                                taskBounds,
                            ),
                        )
                    }
                }
                try {
                    WindowManagerGlobal.getWindowManagerService()
                        .overridePendingAppTransitionMultiThumbFuture(
                            future.future,
                            RecentsTransition.wrapStartedListener(
                                mHandler,
                                animStartedListener,
                            ),
                            true,
                            taskKey.displayId,
                        )
                } catch (e: RemoteException) {
                    Log.w("TAG", "Failed to override pending app transition (multi-thumbnail future): ", e)
                }
                mTarget.statsLogManager.logger().withItemInfo(mTaskView.itemInfo).log(mLauncherEvent)
            }
        }
    }

    @JvmField
    var legacySplit: TaskShortcutFactory = object : TaskShortcutFactory {

        override fun getShortcuts(
            activity: BaseDraggingActivity,
            taskContainer: TaskIdAttributeContainer,
        ): List<SystemShortcut<*>>? {
            val task = taskContainer.task
            if (!task.isDockable) {
                return null
            }
            return if (isAvailable(activity, task.key.displayId)) {
                listOf(
                    LegacySplitSystemShortcut(
                        R.drawable.ic_split_vertical,
                        R.string.recent_task_option_split_screen,
                        activity,
                        taskContainer,
                        LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_SPLIT_SCREEN_TAP,
                    ),
                )
            } else {
                null
            }
        }

        private fun isAvailable(activity: BaseDraggingActivity, displayId: Int): Boolean {
            return if (LawnchairQuickstepCompat.ATLEAST_T) {
                false
            } else {
                (
                    !activity.deviceProfile.isMultiWindowMode &&
                        (displayId == -1 || displayId == Display.DEFAULT_DISPLAY)
                    )
            }
        }
    }

    class KillSystemShortcut(
        activity: BaseDraggingActivity,
        taskContainer: TaskIdAttributeContainer,
    ) : SystemShortcut<BaseDraggingActivity>(
        R.drawable.ic_close,
        R.string.task_menu_force_stop,
        activity,
        taskContainer.itemInfo,
        taskContainer.taskView,
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
                        mActivity,
                        R.string.task_menu_force_stop,
                        Toast.LENGTH_SHORT,
                    )
                    appKilled.show()
                    val rv: RecentsView<LawnchairLauncher, *> = mActivity.getOverviewPanel()
                    rv.dismissTask(mTaskView, true, true)
                } catch (e: RemoteException) {
                    Log.e("KillSystemShortcut", "onClick: ", e.cause)
                }
            }
            dismissTaskMenuView(mActivity)
        }
    }

    @JvmField
    var killApp: TaskShortcutFactory = object : TaskShortcutFactory {
        override fun getShortcuts(
            activity: BaseDraggingActivity,
            taskContainer: TaskIdAttributeContainer,
        ): List<SystemShortcut<*>> {
            return listOf(
                KillSystemShortcut(activity, taskContainer),
            )
        }
    }
}
