package app.lawnchair.compatlib.twelve;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;
import static android.app.ActivityTaskManager.getService;

import android.app.Activity;
import android.app.ActivityClient;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.Intent;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.window.TaskSnapshot;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.lawnchair.compatlib.RecentsAnimationRunnerCompat;
import app.lawnchair.compatlib.eleven.ActivityManagerCompatVR;
import java.util.List;

public class ActivityManagerCompatVS extends ActivityManagerCompatVR {

    @Override
    public void invalidateHomeTaskSnapshot(Activity homeActivity) {
        try {
            ActivityClient.getInstance()
                    .invalidateHomeTaskSnapshot(
                            homeActivity == null ? null : homeActivity.getActivityToken());
        } catch (Throwable e) {
            Log.w(TAG, "Failed to invalidate home snapshot", e);
        }
    }

    @Nullable
    @Override
    public TaskSnapshot getTaskSnapshot(
            int taskId, boolean isLowResolution, boolean takeSnapshotIfNeeded) {
        try {
            return getService().getTaskSnapshot(taskId, isLowResolution);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to getTaskSnapshot", e);
            return null;
        }
    }

    @Override
    public void startRecentsActivity(
            Intent intent, long eventTime, RecentsAnimationRunnerCompat runnerCompat) {
        IRecentsAnimationRunner runner = null;
        if (runnerCompat != null) {
            runner =
                    new IRecentsAnimationRunner.Stub() {
                        @Override
                        public void onAnimationStart(
                                IRecentsAnimationController controller,
                                RemoteAnimationTarget[] apps,
                                RemoteAnimationTarget[] wallpapers,
                                Rect homeContentInsets,
                                Rect minimizedHomeBounds) {
                            runnerCompat.onAnimationStart(
                                    controller,
                                    apps,
                                    wallpapers,
                                    homeContentInsets,
                                    minimizedHomeBounds);
                        }

                        @Override
                        public void onAnimationCanceled(TaskSnapshot taskSnapshot) {
                            runnerCompat.onAnimationCanceled(taskSnapshot);
                        }

                        @Override
                        public void onTaskAppeared(RemoteAnimationTarget app) {
                            runnerCompat.onTaskAppeared(app);
                        }
                    };
        }
        try {
            getService().startRecentsActivity(intent, eventTime, runner);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to cancel recents animation", e);
        }
    }

    @Nullable
    @Override
    public ActivityManager.RunningTaskInfo getRunningTask(boolean filterOnlyVisibleRecents) {
        // Note: The set of running tasks from the system is ordered by recency
        List<ActivityManager.RunningTaskInfo> tasks =
                ActivityTaskManager.getInstance().getTasks(1, filterOnlyVisibleRecents);
        if (tasks.isEmpty()) {
            return null;
        }
        return tasks.get(0);
    }

    @NonNull
    @Override
    public List<ActivityManager.RecentTaskInfo> getRecentTasks(int numTasks, int userId) {
        return ActivityTaskManager.getInstance()
                .getRecentTasks(numTasks, RECENT_IGNORE_UNAVAILABLE, userId);
    }

    @Nullable
    @Override
    public ActivityManager.RunningTaskInfo[] getRunningTasks(boolean filterOnlyVisibleRecents) {
        List<ActivityManager.RunningTaskInfo> tasks =
                ActivityTaskManager.getInstance()
                        .getTasks(NUM_RECENT_ACTIVITIES_REQUEST, filterOnlyVisibleRecents);
        return tasks.toArray(new ActivityManager.RunningTaskInfo[0]);
    }
}
