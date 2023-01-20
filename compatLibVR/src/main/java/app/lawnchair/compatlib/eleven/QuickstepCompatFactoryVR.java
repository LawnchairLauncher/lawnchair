package app.lawnchair.compatlib.eleven;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.RemoteException;
import android.view.AppTransitionAnimationSpec;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;

import androidx.annotation.NonNull;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.QuickstepCompatFactory;
import app.lawnchair.compatlib.RemoteAnimationRunnerStub;

public class QuickstepCompatFactoryVR extends QuickstepCompatFactory {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVR();
    }

    @Override
    public IRemoteAnimationRunner.Stub wrapRemoteAnimationRunnerStub(RemoteAnimationRunnerStub compatStub) {
        return new IRemoteAnimationRunner.Stub() {
            @Override
            public void onAnimationStart(RemoteAnimationTarget[] apps,
                                         RemoteAnimationTarget[] wallpapers,
                                         final IRemoteAnimationFinishedCallback finishedCallback) {
                compatStub.onAnimationStart(0 /* transit */, apps, wallpapers, null, finishedCallback);
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
                buffer != null ? buffer.createGraphicBufferHandle() : null, rect);
    }
}
