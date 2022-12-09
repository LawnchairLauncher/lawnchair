/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.taskbar;

import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.R;

// TODO: This would be replaced by the thing that has the role and provides the intent.
/**
 * Helper to determine what intent should be used to display in a floating window, if one
 * exists.
 */
public class FloatingTaskIntentResolver {
    private static final String TAG = FloatingTaskIntentResolver.class.getSimpleName();

    @Nullable
    /** Gets an intent for a floating task, if one exists. */
    public static Intent getIntent(Context context) {
        PackageManager pm = context.getPackageManager();
        String pkg = context.getString(R.string.floating_task_package);
        String action = context.getString(R.string.floating_task_action);
        if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(action)) {
            Log.d(TAG, "intent could not be found, pkg= " + pkg + " action= " + action);
            return null;
        }
        Intent intent = createIntent(pm, null, pkg, action);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return intent;
        }
        Log.d(TAG, "No valid intent found!");
        return null;
    }

    @Nullable
    private static Intent createIntent(PackageManager pm, @Nullable String activityName,
            String packageName, String action) {
        if (TextUtils.isEmpty(activityName)) {
            activityName = queryActivityForAction(pm, packageName, action);
        }
        if (TextUtils.isEmpty(activityName)) {
            Log.d(TAG, "Activity name is empty even after action search: " + action);
            return null;
        }
        ComponentName component = new ComponentName(packageName, activityName);
        Intent intent = new Intent(action).setComponent(component).setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.d(TAG, "createIntent returning: " + intent);
        return intent;
    }

    @Nullable
    private static String queryActivityForAction(PackageManager pm, String packageName,
            String action) {
        Intent intent = new Intent(action).setPackage(packageName);
        ResolveInfo resolveInfo = pm.resolveActivity(intent, MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            Log.d(TAG, "queryActivityForAction: + " + resolveInfo);
            return null;
        }
        ActivityInfo info = resolveInfo.activityInfo;
        if (!info.exported) {
            Log.d(TAG, "queryActivityForAction: + " + info + " not exported");
            return null;
        }
        if (!info.enabled) {
            Log.d(TAG, "queryActivityForAction: + " + info + " not enabled");
            return null;
        }
        return resolveInfo.activityInfo.name;
    }
}
