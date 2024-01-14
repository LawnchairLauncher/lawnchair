package app.lawnchair.compat;

import static app.lawnchair.compatlib.eleven.WindowManagerCompatVR.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static app.lawnchair.compatlib.eleven.WindowManagerCompatVR.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;

import android.app.ActivityOptions;

import com.android.systemui.shared.recents.model.Task;

public class ActivityOptionsCompatExt {
    public static void addTaskInfo(ActivityOptions opts, Task.TaskKey taskKey) {
        if (taskKey.windowingMode == WINDOWING_MODE_SPLIT_SCREEN_PRIMARY) {
            // We show non-visible docked tasks in Recents, but we always want to launch
            // them in the fullscreen stack.
            opts.setLaunchWindowingMode(WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        }
    }
}
