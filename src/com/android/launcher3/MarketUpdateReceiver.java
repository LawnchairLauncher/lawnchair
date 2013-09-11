/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class MarketUpdateReceiver extends BroadcastReceiver {
    private static final String TAG = "MarketUpdateReceiver";

    private static final String ACTION_PACKAGE_ENQUEUED =
            "com.android.launcher.action.ACTION_PACKAGE_ENQUEUED";
    private static final String ACTION_PACKAGE_DOWNLOADING =
            "com.android.launcher.action.ACTION_PACKAGE_DOWNLOADING";
    private static final String ACTION_PACKAGE_INSTALLING =
            "com.android.launcher.action.ACTION_PACKAGE_INSTALLING";
    private static final String ACTION_PACKAGE_DEQUEUED =
            "com.android.launcher.action.ACTION_PACKAGE_DEQUEUED";

    /** extra for {@link #ACTION_PACKAGE_ENQUEUED}, send on of the following values **/
    private static final String EXTRA_KEY_REASON = "reason";
    private static final String EXTRA_VALUE_REASON_INSTALL = "install";
    private static final String EXTRA_VALUE_REASON_UPDATE = "update";
    private static final String EXTRA_VALUE_REASON_RESTORE = "restore";

    /** extra for {@link #ACTION_PACKAGE_DOWNLOADING}, send an int in the range [0-100]. **/
    private static final String EXTRA_KEY_PROGRESS = "progress";

    /**
     * extra for {@link #ACTION_PACKAGE_DEQUEUED}
     * send {@link android.app.Activity#RESULT_OK} on success.
     * or {@link android.app.Activity#RESULT_CANCELED} if the package was abandoned.
     * **/
    private static final String EXTRA_KEY_STATUS =
            "com.android.launcher.action.EXTRA_STATUS";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        String pkgName = "none";
        Uri uri = intent.getData();
        if (uri != null) {
            pkgName = uri.getSchemeSpecificPart();;
        }
        if (ACTION_PACKAGE_ENQUEUED.equals(action)) {
            String reason = "unknown";
            if (intent.hasExtra(EXTRA_KEY_REASON)) {
                reason = intent.getStringExtra(EXTRA_KEY_REASON);
            }
            Log.d(TAG, "market has promised to " + reason + ": " + pkgName);
        } else if (ACTION_PACKAGE_DOWNLOADING.equals(action)) {
            int progress = intent.getIntExtra(EXTRA_KEY_PROGRESS, 0);
            Log.d(TAG, "market is downloading (" + progress + "%): " + pkgName);
        } else if (ACTION_PACKAGE_INSTALLING.equals(action)) {
            Log.d(TAG, "market is installing: " + pkgName);
        } else if ( ACTION_PACKAGE_DEQUEUED.equals(action)) {
            boolean success = Activity.RESULT_OK == intent.getIntExtra(EXTRA_KEY_STATUS,
                    Activity.RESULT_CANCELED);
            if (success) {
                Log.d(TAG, "market has installed: " + pkgName);
            } else {
                Log.d(TAG, "market has decided not to install: " + pkgName);
            }
        } else {
            Log.d(TAG, "unknown message " + action);
        }
    }
}
