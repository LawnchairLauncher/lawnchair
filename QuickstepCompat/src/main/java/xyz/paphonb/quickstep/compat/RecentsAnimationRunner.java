package xyz.paphonb.quickstep.compat;

import android.graphics.Rect;
import android.view.IRecentsAnimationController;
import android.view.RemoteAnimationTarget;

public interface RecentsAnimationRunner {

    void onAnimationStart(IRecentsAnimationController controller,
                          RemoteAnimationTarget[] apps, Rect homeContentInsets,
                          Rect minimizedHomeBounds);

    void onAnimationCanceled(boolean deferredWithScreenshot);
}
