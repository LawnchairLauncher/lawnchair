package app.lawnchair.compatlib;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.window.TaskSnapshot;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.List;

public abstract class ActivityManagerCompat {

    public static final int NUM_RECENT_ACTIVITIES_REQUEST = 3;

    public abstract void invalidateHomeTaskSnapshot(final Activity homeActivity);

    public abstract void startRecentsActivity(
            Intent intent, long eventTime, RecentsAnimationRunnerCompat runnerCompat);

    /** Called only in S+ platform */
    @Nullable
    @RequiresApi(31)
    public TaskSnapshot getTaskSnapshot(
            int taskId, boolean isLowResolution, boolean takeSnapshotIfNeeded) {
        return null;
    }

    @Nullable
    public abstract ActivityManager.RunningTaskInfo getRunningTask(
            boolean filterOnlyVisibleRecents);

    @NonNull
    public abstract ActivityManager.RunningTaskInfo[] getRunningTasks(
            boolean filterOnlyVisibleRecents);

    @NonNull
    public abstract List<ActivityManager.RecentTaskInfo> getRecentTasks(int numTasks, int userId);
}
