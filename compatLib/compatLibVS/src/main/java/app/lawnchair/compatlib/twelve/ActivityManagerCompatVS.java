package app.lawnchair.compatlib.twelve;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;

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
import androidx.annotation.RequiresApi;
import app.lawnchair.compatlib.RecentsAnimationRunnerCompat;
import app.lawnchair.compatlib.eleven.ActivityManagerCompatVR;
import java.util.List;

@RequiresApi(31)
public class ActivityManagerCompatVS extends ActivityManagerCompatVR {

    @Override
    public void invalidateHomeTaskSnapshot(Activity homeActivity) {
        try {
            ActivityClient.getInstance()
                    .invalidateHomeTaskSnapshot(
                            homeActivity == null ? null : homeActivity.getActivityToken());
        } catch (Throwable ignored) {
            super.invalidateHomeTaskSnapshot(homeActivity);
        }
    }

    @Nullable
    @Override
    public TaskSnapshot getTaskSnapshot(
            int taskId, boolean isLowResolution, boolean takeSnapshotIfNeeded) {
        try {
            return ActivityTaskManager.getService().getTaskSnapshot(taskId, isLowResolution);
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

                        public void onTasksAppeared(RemoteAnimationTarget[] apps) {
                            runnerCompat.onTasksAppeared(apps);
                        }
                    };
        }
        try {
            ActivityTaskManager.getService().startRecentsActivity(intent, eventTime, runner);
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

    @NonNull
    @Override
    public List<ActivityManager.RunningTaskInfo> getRunningTasks(boolean filterOnlyVisibleRecents) {
        return ActivityTaskManager.getInstance()
                .getTasks(NUM_RECENT_ACTIVITIES_REQUEST, filterOnlyVisibleRecents);
    }
}
