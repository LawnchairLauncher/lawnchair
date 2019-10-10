package com.android.systemui.shared.system;

import android.app.ActivityManager;
import android.os.Looper;
import android.os.RemoteException;

public class TaskStackChangeListenersVP extends TaskStackChangeListeners {

    public TaskStackChangeListenersVP(Looper looper) {
        super(looper);
    }

    @Override
    public void onActivityLaunchOnSecondaryDisplayFailed() throws RemoteException {
        mHandler.sendEmptyMessage(H.ON_ACTIVITY_LAUNCH_ON_SECONDARY_DISPLAY_FAILED);
    }

    @Override
    public void onTaskMovedToFront(int taskId) throws RemoteException {
        mHandler.obtainMessage(H.ON_TASK_MOVED_TO_FRONT, taskId, 0).sendToTarget();
    }
}
