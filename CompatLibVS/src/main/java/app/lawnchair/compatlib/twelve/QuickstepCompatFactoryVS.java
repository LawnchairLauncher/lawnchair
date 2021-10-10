package app.lawnchair.compatlib.twelve;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.AppTransitionAnimationSpec;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.QuickstepCompatFactory;
import app.lawnchair.compatlib.RemoteAnimationRunnerStub;
import app.lawnchair.compatlib.RemoteTransitionStub;

public class QuickstepCompatFactoryVS extends QuickstepCompatFactory {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVS();
    }

    @Override
    public IRemoteAnimationRunner.Stub wrapRemoteAnimationRunnerStub(RemoteAnimationRunnerStub compatStub) {
        return new IRemoteAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(int transit,
                                         RemoteAnimationTarget[] apps,
                                         RemoteAnimationTarget[] wallpapers,
                                         RemoteAnimationTarget[] nonApps,
                                         final IRemoteAnimationFinishedCallback finishedCallback) {
                compatStub.onAnimationStart(transit, apps, wallpapers, nonApps, finishedCallback);
            }

            @Override
            public void onAnimationCancelled() {
                compatStub.onAnimationCancelled();
            }
        };
    }

    @Override
    public AppTransitionAnimationSpec createAppTransitionAnimationSpec(int taskId, Bitmap buffer, Rect rect) {
        return new AppTransitionAnimationSpec(taskId,
                buffer != null ? buffer.getHardwareBuffer() : null, rect);
    }
}
