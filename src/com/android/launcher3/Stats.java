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

package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class Stats {
    private static final boolean DEBUG_BROADCASTS = false;

    public static final String ACTION_LAUNCH = "com.android.launcher3.action.LAUNCH";
    public static final String EXTRA_INTENT = "intent";
    public static final String EXTRA_CONTAINER = "container";
    public static final String EXTRA_SCREEN = "screen";
    public static final String EXTRA_CELLX = "cellX";
    public static final String EXTRA_CELLY = "cellY";

    private final Launcher mLauncher;
    private final String mLaunchBroadcastPermission;

    public Stats(Launcher launcher) {
        mLauncher = launcher;
        mLaunchBroadcastPermission =
                launcher.getResources().getString(R.string.receive_launch_broadcasts_permission);

        if (DEBUG_BROADCASTS) {
            launcher.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Log.v("Stats", "got broadcast: " + intent + " for launched intent: "
                                    + intent.getStringExtra(EXTRA_INTENT));
                        }
                    },
                    new IntentFilter(ACTION_LAUNCH),
                    mLaunchBroadcastPermission,
                    null
            );
        }
    }

    public void recordLaunch(Intent intent) {
        recordLaunch(intent, null);
    }

    public void recordLaunch(Intent intent, ShortcutInfo shortcut) {
        intent = new Intent(intent);
        intent.setSourceBounds(null);

        final String flat = intent.toUri(0);
        Intent broadcastIntent = new Intent(ACTION_LAUNCH).putExtra(EXTRA_INTENT, flat);
        if (shortcut != null) {
            broadcastIntent.putExtra(EXTRA_CONTAINER, shortcut.container)
                    .putExtra(EXTRA_SCREEN, shortcut.screenId)
                    .putExtra(EXTRA_CELLX, shortcut.cellX)
                    .putExtra(EXTRA_CELLY, shortcut.cellY);
        }
        mLauncher.sendBroadcast(broadcastIntent, mLaunchBroadcastPermission);
    }
}
