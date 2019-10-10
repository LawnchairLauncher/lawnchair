package xyz.paphonb.quickstep.compat;

import android.app.ActivityManager;
import android.app.ActivityManager.TaskSnapshot;
import android.app.IAssistDataReceiver;
import android.app.ITaskStackListener;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.RemoteException;

import java.util.List;

public abstract class ActivityManagerCompat {

    public abstract List<ActivityManager.RunningTaskInfo> getFilteredTasks(
            int maxNum,  int ignoreActivityType, int ignoreWindowingMode) throws RemoteException;

    public abstract List<ActivityManager.RecentTaskInfo> getRecentTasks(
            int maxNum, int flags, int userId) throws RemoteException;

    public abstract ActivityManager.TaskSnapshot getTaskSnapshot(
            int taskId, boolean reducedResolution) throws RemoteException;

    public abstract void startRecentsActivity(Intent intent, IAssistDataReceiver assistDataReceiver,
                                              RecentsAnimationRunner recentsAnimationRunner) throws RemoteException;

    public abstract void cancelRecentsAnimation(boolean restoreHomeStackPosition) throws RemoteException;

    public abstract int startActivityFromRecents(int taskId, Bundle bOptions) throws RemoteException;

    public abstract boolean setTaskWindowingModeSplitScreenPrimary(
            int taskId, int createMode, boolean toTop, boolean animate,
            Rect initialBounds, boolean showRecents) throws RemoteException;

    public abstract boolean removeTask(int taskId) throws RemoteException;

    public abstract void removeAllVisibleRecentTasks() throws RemoteException;

    public abstract void cancelTaskWindowTransition(int taskId) throws RemoteException;

    public abstract int getLockTaskModeState() throws RemoteException;

    public abstract void registerTaskStackListener(ITaskStackListener listener) throws RemoteException;

    public abstract void unregisterTaskStackListener(ITaskStackListener listener) throws RemoteException;

    public abstract boolean supportsMultiWindow(Context context);

    public abstract int getDisplayId(Context context);
}
