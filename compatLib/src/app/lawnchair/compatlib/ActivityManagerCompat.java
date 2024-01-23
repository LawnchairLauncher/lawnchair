package app.lawnchair.compatlib;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.window.TaskSnapshot;
import androidx.annotation.Nullable;
import java.util.List;

public abstract class ActivityManagerCompat {

    public static final int NUM_RECENT_ACTIVITIES_REQUEST = 3;

    public abstract void invalidateHomeTaskSnapshot(final Activity homeActivity);

    /**
     * Called only in S+ platform
     *
     * @param taskId
     * @param isLowResolution
     * @param takeSnapshotIfNeeded
     * @return
     */
    @Nullable
    public TaskSnapshot getTaskSnapshot(
            int taskId, boolean isLowResolution, boolean takeSnapshotIfNeeded) {
        return null;
    }

    public abstract void startRecentsActivity(
            Intent intent, long eventTime, RecentsAnimationRunnerCompat runnerCompat);

    public abstract ActivityManager.RunningTaskInfo getRunningTask(
            boolean filterOnlyVisibleRecents);

    public abstract List<ActivityManager.RecentTaskInfo> getRecentTasks(int numTasks, int userId);

    public abstract ActivityManager.RunningTaskInfo[] getRunningTasks(
            boolean filterOnlyVisibleRecents);
}
