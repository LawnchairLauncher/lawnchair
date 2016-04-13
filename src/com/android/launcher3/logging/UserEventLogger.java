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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.util.ComponentKey;

import com.google.protobuf.nano.MessageNano;

import java.util.List;

public abstract class UserEventLogger {

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

    private String TAG = "UserEventLogger";
    private static final boolean DEBUG_BROADCASTS = true;

    public static final String ACTION_LAUNCH = "com.android.launcher3.action.LAUNCH";
    public static final String EXTRA_INTENT = "intent";;
    public static final String EXTRA_SOURCE = "source";

    private final Launcher mLauncher;
    private final String mLaunchBroadcastPermission;

    private long mElapsedContainerMillis;
    private long mElapsedSessionMillis;
    private long mActionDurationMillis;

    // Used for filling in predictedRank on {@link Target}s.
    private List<ComponentKey> mPredictedApps;

    public UserEventLogger(Launcher launcher) {
        mLauncher = launcher;
        mLaunchBroadcastPermission =
                launcher.getResources().getString(R.string.receive_launch_broadcasts_permission);

        if (DEBUG_BROADCASTS) {
            launcher.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Log.v(TAG, "got broadcast: " + intent + " for launched intent: "
                                    + intent.getStringExtra(EXTRA_INTENT));
                        }
                    },
                    new IntentFilter(ACTION_LAUNCH),
                    mLaunchBroadcastPermission,
                    null
            );
        }
    }

    //                      APP_ICON    SHORTCUT    WIDGET
    // --------------------------------------------------------------
    // packageNameHash      required    optional    required
    // componentNameHash    required                required
    // intentHash                       required
    // --------------------------------------------------------------

    /**
     * Prepare {@link LauncherEvent} and {@link Intent} and then attach the event
     * to the intent and then broadcast.
     */
    public final void broadcastEvent(LauncherEvent ev, Intent intent) {
        intent = new Intent(intent);
        intent.setSourceBounds(null);

        final String flat = intent.toUri(0);
        Intent broadcastIntent = new Intent(ACTION_LAUNCH).putExtra(EXTRA_INTENT, flat);

        broadcastIntent.putExtra(EXTRA_SOURCE, MessageNano.toByteArray(ev));
        String[] packages = ((Context)mLauncher).getResources().getStringArray(R.array.launch_broadcast_targets);
        for(String p: packages) {
            broadcastIntent.setPackage(p);
            mLauncher.sendBroadcast(broadcastIntent, mLaunchBroadcastPermission);
        }
    }

    public final void logLaunch(View v, Intent intent) {
        LauncherEvent event = LoggerUtils.initLauncherEvent(
                Action.TOUCH, Target.ITEM, Target.CONTAINER);
        event.action.touch = Action.TAP;

        // Fill in grid(x,y), pageIndex of the child and container type of the parent
        // TODO: make this percolate up the view hierarchy if needed.
        int idx = 0;
        LaunchSourceProvider provider = getLaunchProviderRecursive(v);
        provider.fillInLaunchSourceData(v, (ItemInfo) v.getTag(), event.srcTarget[idx], event.srcTarget[idx + 1]);

        // TODO: Fill in all the hashes and the predictedRank

        // Fill in the duration of time spent navigating in Launcher and the container.
        event.elapsedContainerMillis = System.currentTimeMillis() - mElapsedContainerMillis;
        event.elapsedSessionMillis = System.currentTimeMillis() - mElapsedSessionMillis;
        processEvent(event);

        broadcastEvent(event, intent);
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

    public abstract void processEvent(LauncherLogProto.LauncherEvent ev);

    public int getPredictedRank(ComponentKey key) {
        if (mPredictedApps == null) return -1;
        return mPredictedApps.indexOf(key);
    }
}
