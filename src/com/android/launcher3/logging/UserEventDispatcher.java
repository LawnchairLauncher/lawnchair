/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.logging;

import android.content.ComponentName;
import android.content.Intent;
import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ComponentKey;

import java.util.List;

/**
 * Manages the creation of {@link LauncherEvent}.
 */
public abstract class UserEventDispatcher {

    private final static int MAXIMUM_VIEW_HIERARCHY_LEVEL = 5;
    /**
     * Implemented by containers to provide a launch source for a given child.
     */
    public interface LaunchSourceProvider {

        /**
         * Copies data from the source to the destination proto.
         * @param v                 source of the data
         * @param info          source of the data
         * @param target            dest of the data
         * @param targetParent      dest of the data
         */
        void fillInLaunchSourceData(View v, ItemInfo info, Target target, Target targetParent);
    }

    /**
     * Recursively finds the parent of the given child which implements IconLogInfoProvider
     */
    public static LaunchSourceProvider getLaunchProviderRecursive(View v) {
        ViewParent parent = null;
        if (v != null) {
            parent = v.getParent();
        } else {
            return null;
        }

        // Optimization to only check up to 5 parents.
        int count = MAXIMUM_VIEW_HIERARCHY_LEVEL;
        while (parent != null && count-- > 0) {
            if (parent instanceof LaunchSourceProvider) {
                return (LaunchSourceProvider) parent;
            } else {
                parent = parent.getParent();
            }
        }
        return null;
    }

    private String TAG = "UserEvent";

    private long mElapsedContainerMillis;
    private long mElapsedSessionMillis;
    private long mActionDurationMillis;

    // Used for filling in predictedRank on {@link Target}s.
    private List<ComponentKey> mPredictedApps;

    //                      APP_ICON    SHORTCUT    WIDGET
    // --------------------------------------------------------------
    // packageNameHash      required    optional    required
    // componentNameHash    required                required
    // intentHash                       required
    // --------------------------------------------------------------

    protected LauncherEvent createLauncherEvent(View v, Intent intent) {
        LauncherEvent event = LoggerUtils.initLauncherEvent(
                Action.TOUCH, Target.ITEM, Target.CONTAINER);
        event.action.touch = Action.TAP;

        // Fill in grid(x,y), pageIndex of the child and container type of the parent
        // TODO: make this percolate up the view hierarchy if needed.
        int idx = 0;
        LaunchSourceProvider provider = getLaunchProviderRecursive(v);
        ItemInfo itemInfo = (ItemInfo) v.getTag();
        provider.fillInLaunchSourceData(v, itemInfo, event.srcTarget[idx], event.srcTarget[idx + 1]);

        event.srcTarget[idx].intentHash = intent.hashCode();
        ComponentName cn = intent.getComponent();
        if (cn != null) {
            event.srcTarget[idx].packageNameHash = cn.getPackageName().hashCode();
            event.srcTarget[idx].componentHash = cn.hashCode();
            event.srcTarget[idx].predictedRank = mPredictedApps.indexOf(
                    new ComponentKey(cn, itemInfo.user));
        }

        // Fill in the duration of time spent navigating in Launcher and the container.
        event.elapsedContainerMillis = System.currentTimeMillis() - mElapsedContainerMillis;
        event.elapsedSessionMillis = System.currentTimeMillis() - mElapsedSessionMillis;
        return event;
    }

    public void logAppLaunch(View v, Intent intent) {
        dispatchUserEvent(createLauncherEvent(v, intent), intent);
    }

    public void logTap(View v) {
        // TODO
    }

    public void logLongPress() {
        // TODO
    }

    public void logDragNDrop() {
        // TODO
    }

    public void setPredictedApps(List<ComponentKey> predictedApps) {
        mPredictedApps = predictedApps;
    }

    /**
     * Currently logs following containers: workspace, allapps, widget tray.
     */
    public final void resetElapsedContainerMillis() {
        mElapsedContainerMillis = System.currentTimeMillis();
    }

    public final void resetElapsedSessionMillis() {
        mElapsedSessionMillis = System.currentTimeMillis();
        mElapsedContainerMillis = System.currentTimeMillis();

    }

    public final void resetActionDurationMillis() {
        mActionDurationMillis = System.currentTimeMillis();
    }

    public abstract void dispatchUserEvent(LauncherEvent ev, Intent intent);

    public int getPredictedRank(ComponentKey key) {
        if (mPredictedApps == null) return -1;
        return mPredictedApps.indexOf(key);
    }
}
