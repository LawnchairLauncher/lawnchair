package app.lawnchair.compatlib.thirteen;

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
import app.lawnchair.compatlib.twelve.ActivityManagerCompatVS;

open class ActivityManagerCompatVT : ActivityManagerCompatVS() {

    companion object {
        private const val TAG = "ActivityManagerCompatVT"
    }

    override fun startRecentsActivity(
        intent: Intent,
        eventTime: Long,
        runnerCompat: RecentsAnimationRunnerCompat
    ) {
        val runner: IRecentsAnimationRunner = object : IRecentsAnimationRunner.Stub() {
            override fun onAnimationStart(
                controller: IRecentsAnimationController?,
                apps: Array<RemoteAnimationTarget>?,
                wallpapers: Array<RemoteAnimationTarget>?,
                homeContentInsets: Rect?,
                minimizedHomeBounds: Rect?
            ) {
                runnerCompat.onAnimationStart(controller!!, apps!!, wallpapers!!, homeContentInsets!!, minimizedHomeBounds!!)
            }

            override fun onAnimationCanceled(taskIds: IntArray, taskSnapshots: Array<TaskSnapshot>) {
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

    override fun getTaskSnapshot(taskId: Int, isLowResolution: Boolean, takeSnapshotIfNeeded: Boolean): TaskSnapshot? {
        return try {
            // android13 qpr1
            ActivityTaskManager.getService().getTaskSnapshot(taskId, isLowResolution, true /* takeSnapshotIfNeeded */)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to getTaskSnapshot", e)
            null
        } catch (e: NoSuchMethodError) {
            // android13/12
            super.getTaskSnapshot(taskId, isLowResolution, takeSnapshotIfNeeded)
        }
    }
}
