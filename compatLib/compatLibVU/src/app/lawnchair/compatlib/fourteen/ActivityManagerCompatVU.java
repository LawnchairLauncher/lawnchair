package app.lawnchair.compatlib.fourteen;

import static android.app.ActivityTaskManager.getService;

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

public class ActivityManagerCompatVU extends ActivityManagerCompatVT {

    private static final String TAG = "ActivityManagerCompatVU";

    @Override
    public void startRecentsActivity(Intent intent, long eventTime, RecentsAnimationRunnerCompat runnerCompat) {
        IRecentsAnimationRunner runner = null;
        if (runnerCompat != null) {
            runner = new IRecentsAnimationRunner.Stub() {
                @Override
                public void onAnimationStart(IRecentsAnimationController controller,
                                             RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers,
                                             Rect homeContentInsets, Rect minimizedHomeBounds) {
                    runnerCompat.onAnimationStart(controller, apps, wallpapers, homeContentInsets, minimizedHomeBounds);
                }

                @Override
                public void onAnimationCanceled(int[] taskIds, TaskSnapshot[] taskSnapshots) {
                    runnerCompat.onAnimationCanceled(taskIds, taskSnapshots);
                }

                @Override
                public void onTasksAppeared(RemoteAnimationTarget[] apps) {
                    runnerCompat.onTasksAppeared(apps);
                }
            };
        }
        try {
            getService().startRecentsActivity(intent, eventTime, runner);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to cancel recents animation", e);
        }
    }

    @Override
    public TaskSnapshot getTaskSnapshot(int taskId, boolean isLowResolution, boolean takeSnapshotIfNeeded) {
        try {
            return getService().getTaskSnapshot(taskId, isLowResolution,
                    true /* takeSnapshotIfNeeded */);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to getTaskSnapshot", e);
            return null;
        } catch (NoSuchMethodError e) {
            return super.getTaskSnapshot(taskId, isLowResolution, takeSnapshotIfNeeded);
        }

    }
}
