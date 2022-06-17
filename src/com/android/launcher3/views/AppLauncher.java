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
package com.android.launcher3.views;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.model.WidgetsModel.GO_DISABLE_SHORTCUTS;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.StrictMode;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.RunnableList;

/** An {@link ActivityContext} that can also launch app activities and shortcuts safely. */
public interface AppLauncher extends ActivityContext {

    String TAG = "AppLauncher";

    /**
     * Safely starts an activity.
     *
     * @param v View starting the activity.
     * @param intent Base intent being launched.
     * @param item Item associated with the view.
     * @return {@code true} if the activity starts successfully.
     */
    default boolean startActivitySafely(
            View v, Intent intent, @Nullable ItemInfo item) {

        Context context = (Context) this;
        if (isAppBlockedForSafeMode() && !PackageManagerHelper.isSystemApp(context, intent)) {
            Toast.makeText(context, R.string.safemode_shortcut_error, Toast.LENGTH_SHORT).show();
            return false;
        }

        Bundle optsBundle = (v != null) ? getActivityLaunchOptions(v, item).toBundle() : null;
        UserHandle user = item == null ? null : item.user;

        // Prepare intent
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (v != null) {
            intent.setSourceBounds(Utilities.getViewBounds(v));
        }
        try {
            boolean isShortcut = (item instanceof WorkspaceItemInfo)
                    && (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                    || item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT)
                    && !((WorkspaceItemInfo) item).isPromise();
            if (isShortcut) {
                // Shortcuts need some special checks due to legacy reasons.
                startShortcutIntentSafely(intent, optsBundle, item);
            } else if (user == null || user.equals(Process.myUserHandle())) {
                // Could be launching some bookkeeping activity
                context.startActivity(intent, optsBundle);
            } else {
                context.getSystemService(LauncherApps.class).startMainActivity(
                        intent.getComponent(), user, intent.getSourceBounds(), optsBundle);
            }
            if (item != null) {
                InstanceId instanceId = new InstanceIdSequence().newInstanceId();
                logAppLaunch(getStatsLogManager(), item, instanceId);
            }
            return true;
        } catch (NullPointerException | ActivityNotFoundException | SecurityException e) {
            Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + item + " intent=" + intent, e);
        }
        return false;
    }

    /** Returns {@code true} if an app launch is blocked due to safe mode. */
    default boolean isAppBlockedForSafeMode() {
        return false;
    }

    /**
     * Creates and logs a new app launch event.
     */
    default void logAppLaunch(StatsLogManager statsLogManager, ItemInfo info,
            InstanceId instanceId) {
        statsLogManager.logger().withItemInfo(info).withInstanceId(instanceId)
                .log(LAUNCHER_APP_LAUNCH_TAP);
    }

    /**
     * Returns launch options for an Activity.
     *
     * @param v View initiating a launch.
     * @param item Item associated with the view.
     */
    default ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        int left = 0, top = 0;
        int width = v.getMeasuredWidth(), height = v.getMeasuredHeight();
        if (v instanceof BubbleTextView) {
            // Launch from center of icon, not entire view
            Drawable icon = ((BubbleTextView) v).getIcon();
            if (icon != null) {
                Rect bounds = icon.getBounds();
                left = (width - bounds.width()) / 2;
                top = v.getPaddingTop();
                width = bounds.width();
                height = bounds.height();
            }
        }
        ActivityOptions options =
                ActivityOptions.makeClipRevealAnimation(v, left, top, width, height);

        options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : Display.DEFAULT_DISPLAY);
        RunnableList callback = new RunnableList();
        return new ActivityOptionsWrapper(options, callback);
    }

    /**
     * Safely launches an intent for a shortcut.
     *
     * @param intent Intent to start.
     * @param optsBundle Optional launch arguments.
     * @param info Shortcut information.
     */
    default void startShortcutIntentSafely(Intent intent, Bundle optsBundle, ItemInfo info) {
        try {
            StrictMode.VmPolicy oldPolicy = StrictMode.getVmPolicy();
            try {
                // Temporarily disable deathPenalty on all default checks. For eg, shortcuts
                // containing file Uri's would cause a crash as penaltyDeathOnFileUriExposure
                // is enabled by default on NYC.
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll()
                        .penaltyLog().build());

                if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                    String id = ((WorkspaceItemInfo) info).getDeepShortcutId();
                    String packageName = intent.getPackage();
                    startShortcut(packageName, id, intent.getSourceBounds(), optsBundle, info.user);
                } else {
                    // Could be launching some bookkeeping activity
                    ((Context) this).startActivity(intent, optsBundle);
                }
            } finally {
                StrictMode.setVmPolicy(oldPolicy);
            }
        } catch (SecurityException e) {
            if (!onErrorStartingShortcut(intent, info)) {
                throw e;
            }
        }
    }

    /**
     * A wrapper around the platform method with Launcher specific checks.
     */
    default void startShortcut(String packageName, String id, Rect sourceBounds,
            Bundle startActivityOptions, UserHandle user) {
        if (GO_DISABLE_SHORTCUTS) {
            return;
        }
        try {
            ((Context) this).getSystemService(LauncherApps.class).startShortcut(packageName, id,
                    sourceBounds, startActivityOptions, user);
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Failed to start shortcut", e);
        }
    }

    /**
     * Invoked when a shortcut fails to launch.
     * @param intent Shortcut intent that failed to start.
     * @param info Shortcut information.
     * @return {@code true} if the error is handled by this callback.
     */
    default boolean onErrorStartingShortcut(Intent intent, ItemInfo info) {
        return false;
    }
}
