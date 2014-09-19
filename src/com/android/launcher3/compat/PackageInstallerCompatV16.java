/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.compat;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.LauncherAppState;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.HashSet;

public class PackageInstallerCompatV16 extends PackageInstallerCompat {

    private static final String TAG = "PackageInstallerCompatV16";
    private static final boolean DEBUG = false;

    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_STATE = "state";

    private static final String PREFS =
            "com.android.launcher3.compat.PackageInstallerCompatV16.queue";

    protected final SharedPreferences mPrefs;

    boolean mUseQueue;
    boolean mFinishedBind;
    boolean mReplayPending;

    PackageInstallerCompatV16(Context context) {
        mPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void onPause() {
        mUseQueue = true;
        if (DEBUG) Log.d(TAG, "updates paused");
    }

    @Override
    public void onResume() {
        mUseQueue = false;
        if (mFinishedBind) {
            replayUpdates();
        }
    }

    @Override
    public void onFinishBind() {
        mFinishedBind = true;
        if (!mUseQueue) {
            replayUpdates();
        }
    }

    @Override
    public void onStop() { }

    private void replayUpdates() {
        if (DEBUG) Log.d(TAG, "updates resumed");
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app == null) {
            mReplayPending = true; // try again later
            if (DEBUG) Log.d(TAG, "app is null, delaying send");
            return;
        }
        mReplayPending = false;
        ArrayList<PackageInstallInfo> updates = new ArrayList<PackageInstallInfo>();
        for (String packageName: mPrefs.getAll().keySet()) {
            final String json = mPrefs.getString(packageName, null);
            if (!TextUtils.isEmpty(json)) {
                updates.add(infoFromJson(packageName, json));
            }
        }
        if (!updates.isEmpty()) {
            sendUpdate(app, updates);
        }
    }

    /**
     * This should be called by the implementations to register a package update.
     */
    @Override
    public synchronized void recordPackageUpdate(String packageName, int state, int progress) {
        SharedPreferences.Editor editor = mPrefs.edit();
        PackageInstallInfo installInfo = new PackageInstallInfo(packageName);
        installInfo.progress = progress;
        installInfo.state = state;
        if (state == STATUS_INSTALLED) {
            // no longer necessary to track this package
            editor.remove(packageName);
            if (DEBUG) Log.d(TAG, "no longer tracking " + packageName);
        } else {
            editor.putString(packageName, infoToJson(installInfo));
            if (DEBUG)
                Log.d(TAG, "saved state: " + infoToJson(installInfo)
                        + " for package: " + packageName);

        }
        editor.commit();

        if (!mUseQueue) {
            if (mReplayPending) {
                replayUpdates();
            } else if (state != STATUS_INSTALLED) {
                LauncherAppState app = LauncherAppState.getInstanceNoCreate();
                ArrayList<PackageInstallInfo> update = new ArrayList<PackageInstallInfo>();
                update.add(installInfo);
                sendUpdate(app, update);
            }
        }
    }

    private void sendUpdate(LauncherAppState app, ArrayList<PackageInstallInfo> updates) {
        if (app == null) {
            mReplayPending = true; // try again later
            if (DEBUG) Log.d(TAG, "app is null, delaying send");
        } else {
            app.setPackageState(updates);
        }
    }

    private static PackageInstallInfo infoFromJson(String packageName, String json) {
        PackageInstallInfo info = new PackageInstallInfo(packageName);
        try {
            JSONObject object = (JSONObject) new JSONTokener(json).nextValue();
            info.state = object.getInt(KEY_STATE);
            info.progress = object.getInt(KEY_PROGRESS);
        } catch (JSONException e) {
            Log.e(TAG, "failed to deserialize app state update", e);
        }
        return info;
    }

    private static String infoToJson(PackageInstallInfo info) {
        String value = null;
        try {
            JSONStringer json = new JSONStringer()
                    .object()
                    .key(KEY_STATE).value(info.state)
                    .key(KEY_PROGRESS).value(info.progress)
                    .endObject();
            value = json.toString();
        } catch (JSONException e) {
            Log.e(TAG, "failed to serialize app state update", e);
        }
        return value;
    }

    @Override
    public HashSet<String> updateAndGetActiveSessionCache() {
        return new HashSet<String>();
    }
}
