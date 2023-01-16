package app.lawnchair.compatlib;

import android.graphics.Rect;
import android.view.IRecentsAnimationController;
import android.view.RemoteAnimationTarget;

public interface RecentsAnimationRunnerStub {
    void onAnimationStart(IRecentsAnimationController controller,
                          RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers,
                          Rect homeContentInsets, Rect minimizedHomeBounds);

    void onAnimationCanceled(Object taskSnapshot);

    void onTaskAppeared(RemoteAnimationTarget app);
}
