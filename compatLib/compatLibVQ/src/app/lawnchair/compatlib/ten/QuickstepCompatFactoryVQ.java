package app.lawnchair.compatlib.ten;

import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.eleven.QuickstepCompatFactoryVR;

public class QuickstepCompatFactoryVQ extends QuickstepCompatFactoryVR {


    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVQ();
    }

    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVQ();
    }

    @Override
    public RemoteAnimationTarget createRemoteAnimationTarget(int taskId, int mode, SurfaceControl leash, boolean isTranslucent, Rect clipRect, Rect contentInsets, int prefixOrderIndex, Point position, Rect localBounds, Rect screenSpaceBounds, WindowConfiguration windowConfig, boolean isNotInRecents, SurfaceControl startLeash, Rect startBounds, ActivityManager.RunningTaskInfo taskInfo, boolean allowEnterPip, int windowType) {
        return new RemoteAnimationTarget(taskId, mode, leash, isTranslucent,
                clipRect, contentInsets, prefixOrderIndex, position,
                screenSpaceBounds, windowConfig, isNotInRecents,
                startLeash, startBounds);
    }
}
