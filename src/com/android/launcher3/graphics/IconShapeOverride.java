/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.graphics;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.SystemClock;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.LooperExecuter;

import java.lang.reflect.Field;

/**
 * Utility class to override shape of {@link android.graphics.drawable.AdaptiveIconDrawable}.
 */
@TargetApi(Build.VERSION_CODES.O)
public class IconShapeOverride {

    private static final String TAG = "IconShapeOverride";

    public static final String KEY_PREFERENCE = "pref_override_icon_shape";

    // Time to wait before killing the process this ensures that the progress bar is visible for
    // sufficient time so that there is no flicker.
    private static final long PROCESS_KILL_DELAY_MS = 1000;

    private static final int RESTART_REQUEST_CODE = 42; // the answer to everything

    public static boolean isSupported(Context context) {
        if (!Utilities.isAtLeastO()) {
            return false;
        }
        try {
            if (getSystemResField().get(null) != Resources.getSystem()) {
                // Our assumption that mSystem is the system resource is not true.
                return false;
            }
        } catch (Exception e) {
            // Ignore, not supported
            return false;
        }

        return getConfigResId() != 0;
    }

    public static void apply(Context context) {
        if (!Utilities.isAtLeastO()) {
            return;
        }
        String path = getAppliedValue(context);
        if (TextUtils.isEmpty(path)) {
            return;
        }
        if (!isSupported(context)) {
            return;
        }

        // magic
        try {
            Resources override =
                    new ResourcesOverride(Resources.getSystem(), getConfigResId(), path);
            getSystemResField().set(null, override);
        } catch (Exception e) {
            Log.e(TAG, "Unable to override icon shape", e);
            // revert value.
            prefs(context).edit().remove(KEY_PREFERENCE).apply();
        }
    }

    private static Field getSystemResField() throws Exception {
        Field staticField = Resources.class.getDeclaredField("mSystem");
        staticField.setAccessible(true);
        return staticField;
    }

    private static int getConfigResId() {
        return Resources.getSystem().getIdentifier("config_icon_mask", "string", "android");
    }

    private static String getAppliedValue(Context context) {
        return prefs(context).getString(KEY_PREFERENCE, "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(LauncherFiles.DEVICE_PREFERENCES_KEY, 0);
    }

    public static void handlePreferenceUi(ListPreference preference) {
        Context context = preference.getContext();
        preference.setValue(getAppliedValue(context));
        preference.setOnPreferenceChangeListener(new PreferenceChangeHandler(context));
    }

    private static class ResourcesOverride extends Resources {

        private final int mOverrideId;
        private final String mOverrideValue;

        @SuppressWarnings("deprecation")
        public ResourcesOverride(Resources parent, int overrideId, String overrideValue) {
            super(parent.getAssets(), parent.getDisplayMetrics(), parent.getConfiguration());
            mOverrideId = overrideId;
            mOverrideValue = overrideValue;
        }

        @NonNull
        @Override
        public String getString(int id) throws NotFoundException {
            if (id == mOverrideId) {
                return mOverrideValue;
            }
            return super.getString(id);
        }
    }

    private static class PreferenceChangeHandler implements OnPreferenceChangeListener {

        private final Context mContext;

        private PreferenceChangeHandler(Context context) {
            mContext = context;
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            String newValue = (String) o;
            if (!getAppliedValue(mContext).equals(newValue)) {
                // Value has changed
                ProgressDialog.show(mContext,
                        null /* title */,
                        mContext.getString(R.string.icon_shape_override_progress),
                        true /* indeterminate */,
                        false /* cancelable */);
                new LooperExecuter(LauncherModel.getWorkerLooper()).execute(
                        new OverrideApplyHandler(mContext, newValue));
            }
            return false;
        }
    }

    private static class OverrideApplyHandler implements Runnable {

        private final Context mContext;
        private final String mValue;

        private OverrideApplyHandler(Context context, String value) {
            mContext = context;
            mValue = value;
        }

        @Override
        public void run() {
            // Synchronously write the preference.
            prefs(mContext).edit().putString(KEY_PREFERENCE, mValue).commit();
            // Clear the icon cache.
            LauncherAppState.getInstance(mContext).getIconCache().clear();

            // Wait for it
            try {
                Thread.sleep(PROCESS_KILL_DELAY_MS);
            } catch (Exception e) {
                Log.e(TAG, "Error waiting", e);
            }

            // Schedule an alarm before we kill ourself.
            Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(mContext.getPackageName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(mContext, RESTART_REQUEST_CODE,
                    homeIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            mContext.getSystemService(AlarmManager.class).setExact(
                    AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 50, pi);

            // Kill process
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
