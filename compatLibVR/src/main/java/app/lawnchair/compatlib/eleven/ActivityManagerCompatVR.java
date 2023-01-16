package app.lawnchair.compatlib.eleven;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;
import static android.app.ActivityTaskManager.getService;
import static android.graphics.Bitmap.Config.ARGB_8888;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.GraphicBuffer;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;

import java.util.ArrayList;
import java.util.List;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.RecentsAnimationRunnerStub;

public class ActivityManagerCompatVR extends ActivityManagerCompat {

    private static final String TAG = "ActivityManagerCompatVR";

    @Override
    public void startRecentsActivity(Intent intent, long eventTime, RecentsAnimationRunnerStub runner) throws RemoteException {
        IRecentsAnimationRunner wrappedRunner = null;
        if (runner != null) {
            wrappedRunner = new IRecentsAnimationRunner.Stub() {
                @Override
                public void onAnimationStart(IRecentsAnimationController controller,
                                             RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers,
                                             Rect homeContentInsets, Rect minimizedHomeBounds) {
                    runner.onAnimationStart(controller, apps, wallpapers, homeContentInsets, minimizedHomeBounds);
                }

                @Override
                public void onAnimationCanceled(ActivityManager.TaskSnapshot taskSnapshot) {
                    runner.onAnimationCanceled(taskSnapshot);
                }

                @Override
                public void onTaskAppeared(RemoteAnimationTarget app) {
                    runner.onTaskAppeared(app);
                }
            };
        }
        getService().startRecentsActivity(intent, null, wrappedRunner);
    }

    @Override
    public ActivityManager.RunningTaskInfo getRunningTask(boolean filterOnlyVisibleRecents) {
        // Note: The set of running tasks from the system is ordered by recency
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    ActivityTaskManager.getService().getFilteredTasks(1, filterOnlyVisibleRecents);
            if (tasks.isEmpty()) {
                return null;
            }
            return tasks.get(0);
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public List<ActivityManager.RecentTaskInfo> getRecentTasks(int numTasks, int userId) {
        try {
            return ActivityTaskManager.getService().getRecentTasks(numTasks,
                    RECENT_IGNORE_UNAVAILABLE, userId).getList();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get recent tasks", e);
            return new ArrayList<>();
        }
    }

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

    public ThumbnailData takeScreenshot(IRecentsAnimationController animationController, int taskId) {
        try {
            ActivityManager.TaskSnapshot snapshot = animationController.screenshotTask(taskId);
            return snapshot != null ? makeThumbnailData(snapshot) : new ThumbnailData();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to screenshot task", e);
            return new ThumbnailData();
        }
    }

    public ThumbnailData makeThumbnailData(ActivityManager.TaskSnapshot snapshot) {
        ThumbnailData data = new ThumbnailData();
        final GraphicBuffer buffer = snapshot.getSnapshot();
        if (buffer == null || (buffer.getUsage() & HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE) == 0) {
            // TODO(b/157562905): Workaround for a crash when we get a snapshot without this state
            Log.e("ThumbnailData", "Unexpected snapshot without USAGE_GPU_SAMPLED_IMAGE: "
                    + buffer);
            Point taskSize = snapshot.getTaskSize();
            data.thumbnail = Bitmap.createBitmap(taskSize.x, taskSize.y, ARGB_8888);
            data.thumbnail.eraseColor(Color.BLACK);
        } else {
            data.thumbnail = Bitmap.wrapHardwareBuffer(buffer, snapshot.getColorSpace());
        }
        data.insets = new Rect(snapshot.getContentInsets());
        data.orientation = snapshot.getOrientation();
        data.rotation = snapshot.getRotation();
        data.reducedResolution = snapshot.isLowResolution();
        // TODO(b/149579527): Pass task size instead of computing scale.
        // Assume width and height were scaled the same; compute scale only for width
        data.scale = (float) data.thumbnail.getWidth() / snapshot.getTaskSize().x;
        data.isRealSnapshot = snapshot.isRealSnapshot();
        data.isTranslucent = snapshot.isTranslucent();
        data.windowingMode = snapshot.getWindowingMode();
        data.systemUiVisibility = snapshot.getSystemUiVisibility();
        data.snapshotId = snapshot.getId();
        return data;
    }

    public ThumbnailData convertTaskSnapshotToThumbnailData(Object taskSnapshot) {
        if (taskSnapshot != null) {
            return makeThumbnailData((ActivityManager.TaskSnapshot) taskSnapshot);
        } else {
            return null;
        }
    }

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
