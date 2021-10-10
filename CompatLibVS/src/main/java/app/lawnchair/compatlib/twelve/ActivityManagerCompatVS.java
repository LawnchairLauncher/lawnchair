package app.lawnchair.compatlib.twelve;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;
import static android.app.ActivityTaskManager.getService;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.Intent;
import android.graphics.Rect;
import android.os.RemoteException;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.window.TaskSnapshot;

import java.util.List;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.RecentsAnimationRunnerStub;

public class ActivityManagerCompatVS extends ActivityManagerCompat {

    private final ActivityTaskManager mAtm = ActivityTaskManager.getInstance();

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
                public void onAnimationCanceled(TaskSnapshot taskSnapshot) {
                    runner.onAnimationCanceled(taskSnapshot);
                }

                @Override
                public void onTaskAppeared(RemoteAnimationTarget app) {
                    runner.onTaskAppeared(app);
                }
            };
        }
        getService().startRecentsActivity(intent, eventTime, wrappedRunner);
    }

    @Override
    public ActivityManager.RunningTaskInfo getRunningTask(boolean filterOnlyVisibleRecents) {
        // Note: The set of running tasks from the system is ordered by recency
        List<ActivityManager.RunningTaskInfo> tasks =
                mAtm.getTasks(1, filterOnlyVisibleRecents);
        if (tasks.isEmpty()) {
            return null;
        }
        return tasks.get(0);
    }

    @Override
    public List<ActivityManager.RecentTaskInfo> getRecentTasks(int numTasks, int userId) {
        return mAtm.getRecentTasks(numTasks, RECENT_IGNORE_UNAVAILABLE, userId);
    }
}
