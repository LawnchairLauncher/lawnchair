package com.android.launcher3.logging;

import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.DEFAULT_CONTAINERTYPE;

import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

import java.util.ArrayList;

public class StatsLogUtils {
    private final static int MAXIMUM_VIEW_HIERARCHY_LEVEL = 5;

    /**
     * Implemented by containers to provide a container source for a given child.
     */
    public interface LogContainerProvider {

        /**
         * Populates parent container targets for an item
         */
        void fillInLogContainerData(ItemInfo childInfo, Target child, ArrayList<Target> parents);
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
