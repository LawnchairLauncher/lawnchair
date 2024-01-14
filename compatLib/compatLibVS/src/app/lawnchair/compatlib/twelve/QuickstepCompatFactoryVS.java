package app.lawnchair.compatlib.twelve;

import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.AppTransitionAnimationSpec;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.QuickstepCompatFactory;
import app.lawnchair.compatlib.RemoteAnimationRunnerStub;

public class QuickstepCompatFactoryVS extends QuickstepCompatFactory {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVS();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVS();
    }

    @Override
    public AppTransitionAnimationSpec createAppTransitionAnimationSpec(int taskId, Bitmap buffer, Rect rect) {
        return new AppTransitionAnimationSpec(taskId,
                buffer != null ? buffer.getHardwareBuffer() : null, rect);
    }

    @Override
    public RemoteAnimationTarget createRemoteAnimationTarget(int taskId, int mode, SurfaceControl leash, boolean isTranslucent, Rect clipRect, Rect contentInsets, int prefixOrderIndex, Point position, Rect localBounds, Rect screenSpaceBounds, WindowConfiguration windowConfig, boolean isNotInRecents, SurfaceControl startLeash, Rect startBounds, ActivityManager.RunningTaskInfo taskInfo, boolean allowEnterPip, int windowType) {
        return new RemoteAnimationTarget(
                taskId, mode, leash, isTranslucent, clipRect, contentInsets,
                prefixOrderIndex, position, localBounds, screenSpaceBounds, windowConfig,
                isNotInRecents, startLeash, startBounds, taskInfo, windowType
        );
    }

    @Override
    public IRemoteAnimationRunner.Stub wrapRemoteAnimationRunnerStub(RemoteAnimationRunnerStub compatStub) {
        return null;
    }
}
