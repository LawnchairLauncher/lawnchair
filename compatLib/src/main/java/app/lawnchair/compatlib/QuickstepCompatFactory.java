package app.lawnchair.compatlib;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.AppTransitionAnimationSpec;
import android.view.IRemoteAnimationRunner;

import androidx.annotation.NonNull;

public abstract class QuickstepCompatFactory {

    @NonNull
    public abstract ActivityManagerCompat getActivityManagerCompat();

    public abstract IRemoteAnimationRunner.Stub wrapRemoteAnimationRunnerStub(RemoteAnimationRunnerStub compatStub);

    public abstract AppTransitionAnimationSpec createAppTransitionAnimationSpec(int taskId, Bitmap buffer, Rect rect);
}
