package app.lawnchair.compatlib.ten;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.WindowConfiguration;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.RemoteException;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;

import java.util.List;

import app.lawnchair.compatlib.RecentsAnimationRunnerCompat;
import app.lawnchair.compatlib.eleven.ActivityManagerCompatVR;

public class ActivityManagerCompatVQ extends ActivityManagerCompatVR {


    @Override
    public void invalidateHomeTaskSnapshot(Activity homeActivity) {
        //do nothing ,android Q not support
    }


    @Override
    public ActivityManager.RunningTaskInfo[] getRunningTasks(boolean filterOnlyVisibleRecents) {


        int ignoreActivityType = WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
        if (filterOnlyVisibleRecents) {
            ignoreActivityType = WindowConfiguration.ACTIVITY_TYPE_RECENTS;
        }

        try {

            List<ActivityManager.RunningTaskInfo> tasks =
                    ActivityTaskManager.getService().getFilteredTasks(NUM_RECENT_ACTIVITIES_REQUEST, ignoreActivityType, WindowConfiguration.WINDOWING_MODE_PINNED);
            if (tasks.isEmpty()) {
                return null;
            }
            return tasks.toArray(new ActivityManager.RunningTaskInfo[tasks.size()]);
        } catch (RemoteException e) {
            return null;
        }
    }

    @Override
    public void startRecentsActivity(Intent intent, long eventTime, RecentsAnimationRunnerCompat runnerCompat) {

        IRecentsAnimationRunner runner = null;
        if (runnerCompat != null) {
            runner = new IRecentsAnimationRunner.Stub() {
                @Override
                public void onAnimationStart(IRecentsAnimationController controller,
                                             RemoteAnimationTarget[] apps, Rect homeContentInsets,
                                             Rect minimizedHomeBounds) {
                    runnerCompat.onAnimationStart(controller, apps, null, homeContentInsets, minimizedHomeBounds);
                }

                public void reportAllDrawn() {}

                @Override
                public void onAnimationCanceled(boolean deferredWithScreenshot) {
                    runnerCompat.onAnimationCanceled(deferredWithScreenshot);
                }
            };
        }
        try {
            ActivityTaskManager.getService().startRecentsActivity(intent, null, runner);
        } catch (RemoteException e) {

        }
    }

    @Override
    public ActivityManager.RunningTaskInfo getRunningTask(boolean filterOnlyVisibleRecents) {

        int ignoreActivityType = WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
        if (filterOnlyVisibleRecents) {
            ignoreActivityType = WindowConfiguration.ACTIVITY_TYPE_RECENTS;
        }
        try {
            List<ActivityManager.RunningTaskInfo> tasks =
                    ActivityTaskManager.getService().getFilteredTasks(1, ignoreActivityType, WindowConfiguration.WINDOWING_MODE_PINNED);
            if (tasks.isEmpty()) {
                return null;
            }
            return tasks.get(0);
        } catch (RemoteException e) {
            return null;
        }

    }

    @Override
    public ThumbnailData makeThumbnailData(ActivityManager.TaskSnapshot snapshot) {
        ThumbnailData data = new ThumbnailData();
        data.thumbnail = Bitmap.wrapHardwareBuffer(snapshot.getSnapshot(), snapshot.getColorSpace());
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
