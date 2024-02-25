package app.lawnchair.compatlib;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.window.TaskSnapshot;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.util.List;

public interface ActivityManagerCompat {
    int NUM_RECENT_ACTIVITIES_REQUEST = 3;

    void invalidateHomeTaskSnapshot(final Activity homeActivity);

    void startRecentsActivity(
            Intent intent, long eventTime, RecentsAnimationRunnerCompat runnerCompat);

    @Nullable
    @RequiresApi(31)
    default TaskSnapshot getTaskSnapshot(
            int taskId, boolean isLowResolution, boolean takeSnapshotIfNeeded) {
        return null;
    }

    @Nullable
    ActivityManager.RunningTaskInfo getRunningTask(boolean filterOnlyVisibleRecents);

    @NonNull
    List<ActivityManager.RunningTaskInfo> getRunningTasks(boolean filterOnlyVisibleRecents);

    @NonNull
    List<ActivityManager.RecentTaskInfo> getRecentTasks(int numTasks, int userId);
}
