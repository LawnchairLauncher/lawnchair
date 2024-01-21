package app.lawnchair.compatlib.fourteen;


import android.app.ActivityTaskManager;
import android.content.Intent;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.window.TaskSnapshot;

import app.lawnchair.compatlib.RecentsAnimationRunnerCompat;
import app.lawnchair.compatlib.thirteen.ActivityManagerCompatVT;

open class ActivityManagerCompatVU : ActivityManagerCompatVT() {

    companion object {
        private const val TAG = "ActivityManagerCompatVU"
    }

    override fun startRecentsActivity(
        intent: Intent,
        eventTime: Long,
        runnerCompat: RecentsAnimationRunnerCompat
    ) {
        val runner: IRecentsAnimationRunner = object : IRecentsAnimationRunner.Stub() {
            override fun onAnimationStart(
                controller: IRecentsAnimationController,
                apps: Array<RemoteAnimationTarget>,
                wallpapers: Array<RemoteAnimationTarget>,
                homeContentInsets: Rect,
                minimizedHomeBounds: Rect
            ) {
                runnerCompat.onAnimationStart(
                    controller,
                    apps,
                    wallpapers,
                    homeContentInsets,
                    minimizedHomeBounds
                )
            }

            override fun onAnimationCanceled(
                taskIds: IntArray,
                taskSnapshots: Array<TaskSnapshot>
            ) {
                runnerCompat.onAnimationCanceled(taskIds, taskSnapshots)
            }

            override fun onTasksAppeared(apps: Array<RemoteAnimationTarget>?) {
                runnerCompat.onTasksAppeared(apps!!)
            }
        }

        try {
            ActivityTaskManager.getService().startRecentsActivity(intent, eventTime, runner)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to start recents activity", e)
        }
    }

    override fun getTaskSnapshot(
        taskId: Int,
        isLowResolution: Boolean,
        takeSnapshotIfNeeded: Boolean
    ): TaskSnapshot? {
        return try {
            ActivityTaskManager.getService()
                .getTaskSnapshot(taskId, isLowResolution, true /* takeSnapshotIfNeeded */)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to getTaskSnapshot", e)
            null
        } catch (e: NoSuchMethodError) {
            super.getTaskSnapshot(taskId, isLowResolution, takeSnapshotIfNeeded)
        }
    }
}
