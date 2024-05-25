package app.lawnchair.compatlib.ten;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.WindowConfiguration;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.RecentsAnimationRunnerCompat;
import java.util.ArrayList;
import java.util.List;

@RequiresApi(29)
public class ActivityManagerCompatVQ implements ActivityManagerCompat {
    protected final String TAG = getClass().getCanonicalName();

    @Override
    public void invalidateHomeTaskSnapshot(Activity homeActivity) {
        // Do nothing, Android Q doesn't support this.
    }

    @NonNull
    @Override
    public List<ActivityManager.RunningTaskInfo> getRunningTasks(boolean filterOnlyVisibleRecents) {
        int ignoreActivityType = WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
        if (filterOnlyVisibleRecents) {
            ignoreActivityType = WindowConfiguration.ACTIVITY_TYPE_RECENTS;
        }

        try {
            return ActivityTaskManager.getService()
                    .getFilteredTasks(
                            NUM_RECENT_ACTIVITIES_REQUEST,
                            ignoreActivityType,
                            WindowConfiguration.WINDOWING_MODE_PINNED);
        } catch (RemoteException e) {
            return new ArrayList<>();
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
                                Rect homeContentInsets,
                                Rect minimizedHomeBounds) {
                            runnerCompat.onAnimationStart(
                                    controller, apps, null, homeContentInsets, minimizedHomeBounds);
                        }

                        @Override
                        public void onAnimationCanceled(boolean deferredWithScreenshot) {
                            runnerCompat.onAnimationCanceled(deferredWithScreenshot);
                        }

                        // Samsung OneUi
                        public void reportAllDrawn() {}
                    };
        }
        try {
            ActivityTaskManager.getService().startRecentsActivity(intent, null, runner);
        } catch (RemoteException ignored) {
        }
    }

    @Nullable
    @Override
    public ActivityManager.RunningTaskInfo getRunningTask(boolean filterOnlyVisibleRecents) {
        int ignoreActivityType = WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
        if (filterOnlyVisibleRecents) {
            ignoreActivityType = WindowConfiguration.ACTIVITY_TYPE_RECENTS;
        }
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    ActivityTaskManager.getService()
                            .getFilteredTasks(
                                    1,
                                    ignoreActivityType,
                                    WindowConfiguration.WINDOWING_MODE_PINNED);
            if (tasks.isEmpty()) {
                return null;
            }
            return tasks.get(0);
        } catch (RemoteException e) {
            return null;
        }
    }

    @NonNull
    @Override
    public List<ActivityManager.RecentTaskInfo> getRecentTasks(int numTasks, int userId) {
        try {
            return ActivityTaskManager.getService()
                    .getRecentTasks(numTasks, RECENT_IGNORE_UNAVAILABLE, userId)
                    .getList();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get recent tasks", e);
            return new ArrayList<>();
        }
    }

    @Override
    public ThumbnailData getTaskThumbnail(int taskId, boolean isLowResolution) {
        ActivityManager.TaskSnapshot snapshot = null;
        try {
            snapshot = ActivityTaskManager.getService().getTaskSnapshot(taskId, isLowResolution);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed to retrieve task snapshot", e);
        }
        if (snapshot != null) {
            return makeThumbnailData(snapshot);
        } else {
            return null;
        }
    }

    @Override
    public ThumbnailData takeScreenshot(
            IRecentsAnimationController animationController, int taskId) {
        try {
            ActivityManager.TaskSnapshot snapshot = animationController.screenshotTask(taskId);
            return snapshot != null ? makeThumbnailData(snapshot) : new ThumbnailData();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to screenshot task", e);
            return new ThumbnailData();
        }
    }

    @Override
    public ThumbnailData convertTaskSnapshotToThumbnailData(Object taskSnapshot) {
        if (taskSnapshot != null) {
            return makeThumbnailData((ActivityManager.TaskSnapshot) taskSnapshot);
        } else {
            return null;
        }
    }

    public ThumbnailData makeThumbnailData(ActivityManager.TaskSnapshot snapshot) {
        ThumbnailData data = new ThumbnailData();
        data.thumbnail =
                Bitmap.wrapHardwareBuffer(snapshot.getSnapshot(), snapshot.getColorSpace());
        data.insets = new Rect(snapshot.getContentInsets());
        data.orientation = snapshot.getOrientation();
        data.reducedResolution = snapshot.isReducedResolution();
        // TODO(b/149579527): Pass task size instead of computing scale.
        // Assume width and height were scaled the same; compute scale only for width
        data.scale = snapshot.getScale();
        data.isRealSnapshot = snapshot.isRealSnapshot();
        data.isTranslucent = snapshot.isTranslucent();
        data.windowingMode = snapshot.getWindowingMode();
        data.systemUiVisibility = snapshot.getSystemUiVisibility();
        return data;
    }
}
