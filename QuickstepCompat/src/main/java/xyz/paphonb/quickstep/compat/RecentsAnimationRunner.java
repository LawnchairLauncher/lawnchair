package xyz.paphonb.quickstep.compat;

import android.graphics.Rect;
import android.view.IRecentsAnimationController;
import android.view.RemoteAnimationTarget;
import android.app.ActivityManager.TaskSnapshot;

public interface RecentsAnimationRunner {

    void onAnimationStart(IRecentsAnimationController controller,
            RemoteAnimationTarget[] apps,
            RemoteAnimationTarget[] wallpaperTargets,
            Rect homeContentInsets,
            Rect minimizedHomeBounds);

    void onAnimationCanceled(TaskSnapshot taskSnapshot);

    void onTaskAppeared(RemoteAnimationTarget app);
}
