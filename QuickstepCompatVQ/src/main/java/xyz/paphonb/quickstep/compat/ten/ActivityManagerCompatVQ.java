package xyz.paphonb.quickstep.compat.ten;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;

import android.app.ActivityManager;
import android.app.ActivityManager.TaskSnapshot;
import android.app.ActivityTaskManager;
import android.app.IAssistDataReceiver;
import android.app.ITaskStackListener;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.IRecentsAnimationController;
import android.view.IRecentsAnimationRunner;
import android.view.RemoteAnimationTarget;

import java.util.List;

import xyz.paphonb.quickstep.compat.ActivityManagerCompat;
import xyz.paphonb.quickstep.compat.RecentsAnimationRunner;

public class ActivityManagerCompatVQ extends ActivityManagerCompat {

    @Override
    public List<ActivityManager.RunningTaskInfo> getFilteredTasks(int maxNum, int ignoreActivityType, int ignoreWindowingMode) throws RemoteException {
        return ActivityTaskManager.getService().getFilteredTasks(maxNum, ignoreActivityType, ignoreWindowingMode);
    }

    @Override
    public List<ActivityManager.RecentTaskInfo> getRecentTasks(int maxNum, int flags, int userId) throws RemoteException {
        return ActivityTaskManager.getService().getRecentTasks(maxNum, flags, userId).getList();
    }

    @Override
    public ActivityManager.TaskSnapshot getTaskSnapshot(int taskId, boolean reducedResolution) throws RemoteException {
        return ActivityTaskManager.getService().getTaskSnapshot(taskId, reducedResolution);
    }

    @Override
    public void startRecentsActivity(Intent intent, IAssistDataReceiver assistDataReceiver, final RecentsAnimationRunner recentsAnimationRunner) throws RemoteException {
        IRecentsAnimationRunner runner = null;
        if (recentsAnimationRunner != null) {
            runner = new IRecentsAnimationRunner.Stub() {
                @Override
                public void onAnimationStart(IRecentsAnimationController controller,
                                             RemoteAnimationTarget[] apps, Rect homeContentInsets,
                                             Rect minimizedHomeBounds) {
                    recentsAnimationRunner.onAnimationStart(controller, apps, homeContentInsets, minimizedHomeBounds);
                }

                @Override
                public void onAnimationCanceled(boolean deferredWithScreenshot) {
                    recentsAnimationRunner.onAnimationCanceled(deferredWithScreenshot);
                }
            };
        }
        ActivityTaskManager.getService().startRecentsActivity(intent, assistDataReceiver, runner);
    }

    @Override
    public void cancelRecentsAnimation(boolean restoreHomeStackPosition) throws RemoteException {
        ActivityTaskManager.getService().cancelRecentsAnimation(restoreHomeStackPosition);
    }

    @Override
    public int startActivityFromRecents(int taskId, Bundle bOptions) throws RemoteException {
        return ActivityTaskManager.getService().startActivityFromRecents(taskId, bOptions);
    }

    @Override
    public boolean setTaskWindowingModeSplitScreenPrimary(
            int taskId, int createMode, boolean toTop, boolean animate,
            Rect initialBounds, boolean showRecents) throws RemoteException {
        return ActivityTaskManager.getService().setTaskWindowingModeSplitScreenPrimary(taskId,
                createMode, toTop, animate, initialBounds, showRecents);
    }

    @Override
    public boolean removeTask(int taskId) throws RemoteException {
        return ActivityTaskManager.getService().removeTask(taskId);
    }

    @Override
    public void removeAllVisibleRecentTasks() throws RemoteException {
        ActivityTaskManager.getService().removeAllVisibleRecentTasks();
    }

    @Override
    public void cancelTaskWindowTransition(int taskId) throws RemoteException {
        ActivityTaskManager.getService().cancelTaskWindowTransition(taskId);
    }

    @Override
    public int getLockTaskModeState() throws RemoteException {
        return ActivityTaskManager.getService().getLockTaskModeState();
    }

    @Override
    public void registerTaskStackListener(ITaskStackListener listener) throws RemoteException {
        ActivityTaskManager.getService().registerTaskStackListener(listener);
    }

    @Override
    public void unregisterTaskStackListener(ITaskStackListener listener) throws RemoteException {
        ActivityTaskManager.getService().unregisterTaskStackListener(listener);
    }

    @Override
    public boolean supportsMultiWindow(Context context) {
        return ActivityTaskManager.supportsMultiWindow(context);
    }

    @Override
    public int getDisplayId(Context context) {
        return context.getDisplayId();
    }
}
