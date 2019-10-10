package com.android.systemui.shared.system;

import android.app.ActivityManager;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

public class TaskStackChangeListenersVQ extends TaskStackChangeListeners {

    public TaskStackChangeListenersVQ(Looper looper) {
        super(looper);
    }

    @Override
    public void onActivityLaunchOnSecondaryDisplayFailed(ActivityManager.RunningTaskInfo taskInfo,
                                                         int requestedDisplayId) throws RemoteException {
        mHandler.obtainMessage(H.ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_FAILED, requestedDisplayId,
                0 /* unused */,
                taskInfo).sendToTarget();
    }

    @Override
    public void onActivityLaunchOnSecondaryDisplayRerouted(ActivityManager.RunningTaskInfo taskInfo,
                                                           int requestedDisplayId) throws RemoteException {
        mHandler.obtainMessage(H.ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_REROUTED,
                requestedDisplayId, 0 /* unused */, taskInfo).sendToTarget();
    }

    @Override
    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo)
            throws RemoteException {
        mHandler.obtainMessage(H.ON_TASK_MOVED_TO_FRONT, taskInfo).sendToTarget();
    }

    @Override
    public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo taskInfo) throws RemoteException {
        mHandler.obtainMessage(H.ON_BACK_PRESSED_ON_TASK_ROOT, taskInfo).sendToTarget();
    }

    @Override
    public void onSizeCompatModeActivityChanged(int displayId, IBinder activityToken) {
        mHandler.obtainMessage(H.ON_SIZE_COMPAT_MODE_ACTIVITY_CHANGED, displayId, 0 /* unused */,
                activityToken).sendToTarget();
    }

    @Override
    public void onTaskDisplayChanged(int taskId, int newDisplayId) throws RemoteException {
        mHandler.obtainMessage(H.ON_TASK_DISPLAY_CHANGED, taskId, newDisplayId).sendToTarget();
    }
}
