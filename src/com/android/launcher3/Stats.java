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

import java.io.*;
import java.util.ArrayList;

public class Stats {
    private static final boolean DEBUG_BROADCASTS = false;
    private static final String TAG = "Launcher3/Stats";

    private static final boolean LOCAL_LAUNCH_LOG = true;

    public static final String ACTION_LAUNCH = "com.android.launcher3.action.LAUNCH";
    public static final String EXTRA_INTENT = "intent";
    public static final String EXTRA_CONTAINER = "container";
    public static final String EXTRA_SCREEN = "screen";
    public static final String EXTRA_CELLX = "cellX";
    public static final String EXTRA_CELLY = "cellY";

    private static final int LOG_VERSION = 1;
    private static final int LOG_TAG_VERSION = 0x1;
    private static final int LOG_TAG_LAUNCH = 0x1000;

    private static final int STATS_VERSION = 1;
    private static final int INITIAL_STATS_SIZE = 100;

    // TODO: delayed/batched writes
    private static final boolean FLUSH_IMMEDIATELY = true;

    private final Launcher mLauncher;

    private final String mLaunchBroadcastPermission;

    DataOutputStream mLog;

    ArrayList<String> mIntents;
    ArrayList<Integer> mHistogram;

    public Stats(Launcher launcher) {
        mLauncher = launcher;

        mLaunchBroadcastPermission =
                launcher.getResources().getString(R.string.receive_launch_broadcasts_permission);

        loadStats();

        if (LOCAL_LAUNCH_LOG) {
            try {
                mLog = new DataOutputStream(mLauncher.openFileOutput(
                        LauncherFiles.LAUNCHES_LOG, Context.MODE_APPEND));
                mLog.writeInt(LOG_TAG_VERSION);
                mLog.writeInt(LOG_VERSION);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "unable to create stats log: " + e);
                mLog = null;
            } catch (IOException e) {
                Log.e(TAG, "unable to write to stats log: " + e);
                mLog = null;
            }
        }

        if (DEBUG_BROADCASTS) {
            launcher.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            android.util.Log.v("Stats", "got broadcast: " + intent + " for launched intent: "
                                    + intent.getStringExtra(EXTRA_INTENT));
                        }
                    },
                    new IntentFilter(ACTION_LAUNCH),
                    mLaunchBroadcastPermission,
                    null
            );
        }
    }

    public void incrementLaunch(String intentStr) {
        int pos = mIntents.indexOf(intentStr);
        if (pos < 0) {
            mIntents.add(intentStr);
            mHistogram.add(1);
        } else {
            mHistogram.set(pos, mHistogram.get(pos) + 1);
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

        incrementLaunch(flat);

        if (FLUSH_IMMEDIATELY) {
            saveStats();
        }

        if (LOCAL_LAUNCH_LOG && mLog != null) {
            try {
                mLog.writeInt(LOG_TAG_LAUNCH);
                mLog.writeLong(System.currentTimeMillis());
                if (shortcut == null) {
                    mLog.writeShort(0);
                    mLog.writeShort(0);
                    mLog.writeShort(0);
                    mLog.writeShort(0);
                } else {
                    mLog.writeShort((short) shortcut.container);
                    mLog.writeShort((short) shortcut.screenId);
                    mLog.writeShort((short) shortcut.cellX);
                    mLog.writeShort((short) shortcut.cellY);
                }
                mLog.writeUTF(flat);
                if (FLUSH_IMMEDIATELY) {
                    mLog.flush(); // TODO: delayed writes
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveStats() {
        DataOutputStream stats = null;
        try {
            stats = new DataOutputStream(mLauncher.openFileOutput(
                    LauncherFiles.STATS_LOG + ".tmp", Context.MODE_PRIVATE));
            stats.writeInt(STATS_VERSION);
            final int N = mHistogram.size();
            stats.writeInt(N);
            for (int i=0; i<N; i++) {
                stats.writeUTF(mIntents.get(i));
                stats.writeInt(mHistogram.get(i));
            }
            stats.close();
            stats = null;
            mLauncher.getFileStreamPath(LauncherFiles.STATS_LOG + ".tmp")
                     .renameTo(mLauncher.getFileStreamPath(LauncherFiles.STATS_LOG));
        } catch (FileNotFoundException e) {
            Log.e(TAG, "unable to create stats data: " + e);
        } catch (IOException e) {
            Log.e(TAG, "unable to write to stats data: " + e);
        } finally {
            if (stats != null) {
                try {
                    stats.close();
                } catch (IOException e) { }
            }
        }
    }

    private void loadStats() {
        mIntents = new ArrayList<String>(INITIAL_STATS_SIZE);
        mHistogram = new ArrayList<Integer>(INITIAL_STATS_SIZE);
        DataInputStream stats = null;
        try {
            stats = new DataInputStream(mLauncher.openFileInput(LauncherFiles.STATS_LOG));
            final int version = stats.readInt();
            if (version == STATS_VERSION) {
                final int N = stats.readInt();
                for (int i=0; i<N; i++) {
                    final String pkg = stats.readUTF();
                    final int count = stats.readInt();
                    mIntents.add(pkg);
                    mHistogram.add(count);
                }
            }
        } catch (FileNotFoundException e) {
            // not a problem
        } catch (IOException e) {
            // more of a problem

        } finally {
            if (stats != null) {
                try {
                    stats.close();
                } catch (IOException e) { }
            }
        }
    }
}
