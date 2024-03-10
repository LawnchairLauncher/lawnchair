package app.lawnchair.compatlib;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.IRecentsAnimationController;
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

    default ThumbnailData getTaskThumbnail(int taskId, boolean isLowResolution) {
        return null;
    }

    default ThumbnailData takeScreenshot(
            IRecentsAnimationController animationController, int taskId) {
        return null;
    }

    default ThumbnailData convertTaskSnapshotToThumbnailData(Object taskSnapshot) {
        return null;
    }

    @Nullable
    ActivityManager.RunningTaskInfo getRunningTask(boolean filterOnlyVisibleRecents);

    @NonNull
    List<ActivityManager.RunningTaskInfo> getRunningTasks(boolean filterOnlyVisibleRecents);

    @NonNull
    List<ActivityManager.RecentTaskInfo> getRecentTasks(int numTasks, int userId);

    public static class ThumbnailData {
        public Bitmap thumbnail;
        public int orientation;
        public int rotation;
        public Rect insets;
        public boolean reducedResolution;
        public boolean isRealSnapshot;
        public boolean isTranslucent;
        public int windowingMode;
        public int systemUiVisibility;
        public float scale;
        public long snapshotId;
    }
}
