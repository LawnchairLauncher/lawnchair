package com.android.launcher3.logging;

import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.DEFAULT_CONTAINERTYPE;

import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import androidx.annotation.Nullable;


public class StatsLogUtils {

    // Defined in android.stats.launcher.nano
    // As they cannot be linked in this file, defining again.
    public final static int LAUNCHER_STATE_BACKGROUND = 0;
    public final static int LAUNCHER_STATE_HOME = 1;
    public final static int LAUNCHER_STATE_OVERVIEW = 2;
    public final static int LAUNCHER_STATE_ALLAPPS = 3;

    private final static int MAXIMUM_VIEW_HIERARCHY_LEVEL = 5;

    public interface LogStateProvider {
        int getCurrentState();
    }

    /**
     * Implemented by containers to provide a container source for a given child.
     *
     * Currently,
     */
    public interface LogContainerProvider {

        /**
         * Copies data from the source to the destination proto.
         *
         * @param v            source of the data
         * @param info         source of the data
         * @param target       dest of the data
         * @param targetParent dest of the data
         */
        void fillInLogContainerData(View v, ItemInfo info, Target target, Target targetParent);
    }

    /**
     * Recursively finds the parent of the given child which implements IconLogInfoProvider
     */
    public static LogContainerProvider getLaunchProviderRecursive(@Nullable View v) {
        ViewParent parent;
        if (v != null) {
            parent = v.getParent();
        } else {
            return null;
        }

        // Optimization to only check up to 5 parents.
        int count = MAXIMUM_VIEW_HIERARCHY_LEVEL;
        while (parent != null && count-- > 0) {
            if (parent instanceof LogContainerProvider) {
                return (LogContainerProvider) parent;
            } else {
                parent = parent.getParent();
            }
        }
        return null;
    }

    public static int getContainerTypeFromState(int state) {
        int containerType = DEFAULT_CONTAINERTYPE;
        switch (state) {
            case StatsLogUtils.LAUNCHER_STATE_ALLAPPS:
                containerType = ContainerType.ALLAPPS;
                break;
            case StatsLogUtils.LAUNCHER_STATE_HOME:
                containerType = ContainerType.WORKSPACE;
                break;
            case StatsLogUtils.LAUNCHER_STATE_OVERVIEW:
                containerType = ContainerType.OVERVIEW;
                break;
        }
        return containerType;
    }
}
