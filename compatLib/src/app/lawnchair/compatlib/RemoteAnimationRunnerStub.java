package app.lawnchair.compatlib;

import android.view.RemoteAnimationTarget;
import android.view.IRemoteAnimationFinishedCallback;

public interface RemoteAnimationRunnerStub {

    void onAnimationStart(int transit, RemoteAnimationTarget[] apps,
                          RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
                          final IRemoteAnimationFinishedCallback finishedCallback);

    void onAnimationCancelled();
}
