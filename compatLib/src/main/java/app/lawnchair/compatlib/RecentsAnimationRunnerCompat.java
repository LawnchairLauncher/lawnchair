package app.lawnchair.compatlib;

import android.graphics.Rect;
import android.view.IRecentsAnimationController;
import android.view.RemoteAnimationTarget;
import android.window.TaskSnapshot;

public interface RecentsAnimationRunnerCompat {

    void onAnimationStart(
            IRecentsAnimationController controller,
            RemoteAnimationTarget[] apps,
            RemoteAnimationTarget[] wallpapers,
            Rect homeContentInsets,
            Rect minimizedHomeBounds);

    /** Called only in T platform */
    void onAnimationCanceled(int[] taskIds, TaskSnapshot[] taskSnapshots);

    /** Called only in Q/R/S platform */
    void onAnimationCanceled(Object taskSnapshot);

    /** Called only in R/S platform */
    void onTaskAppeared(RemoteAnimationTarget app);

    /** Called only in T+ platform */
    void onTasksAppeared(RemoteAnimationTarget[] apps);
}
