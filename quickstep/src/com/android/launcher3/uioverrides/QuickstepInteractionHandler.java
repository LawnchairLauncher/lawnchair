/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_WIDGET_ATTEMPT;

import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.RemoteViews;
import android.window.SplashScreen;

import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.widget.LauncherAppWidgetHostView;

/** Provides a Quickstep specific animation when launching an activity from an app widget. */
class QuickstepInteractionHandler implements RemoteViews.InteractionHandler {

    private static final String TAG = "QuickstepInteractionHandler";

    private final QuickstepLauncher mLauncher;

    QuickstepInteractionHandler(QuickstepLauncher launcher) {
        mLauncher = launcher;
    }

    @SuppressWarnings("NewApi")
    @Override
    public boolean onInteraction(View view, PendingIntent pendingIntent,
            RemoteViews.RemoteResponse remoteResponse) {
        LauncherAppWidgetHostView hostView = findHostViewAncestor(view);
        if (hostView == null) {
            Log.e(TAG, "View did not have a LauncherAppWidgetHostView ancestor.");
            return RemoteViews.startPendingIntent(hostView, pendingIntent,
                    remoteResponse.getLaunchOptions(view));
        }
        if (mLauncher.isSplitSelectionActive()) {
            // Log metric
            StatsLogManager.StatsLogger logger = mLauncher.getStatsLogManager().logger();
            logger.log(LAUNCHER_SPLIT_WIDGET_ATTEMPT);
            mLauncher.handleIncorrectSplitTargetSelection();
            return true;
        }
        Pair<Intent, ActivityOptions> options = remoteResponse.getLaunchOptions(view);
        ActivityOptionsWrapper activityOptions = mLauncher.getAppTransitionManager()
                .getActivityLaunchOptions(hostView);
        Object itemInfo = hostView.getTag();
        IBinder launchCookie = null;
        if (itemInfo instanceof ItemInfo) {
            launchCookie = mLauncher.getLaunchCookie((ItemInfo) itemInfo);
            activityOptions.options.setLaunchCookie(launchCookie);
        }
        if (Utilities.ATLEAST_S && !pendingIntent.isActivity()) {
            // In the event this pending intent eventually launches an activity, i.e. a trampoline,
            // use the Quickstep transition animation.
            try {
                ActivityTaskManager.getService()
                        .registerRemoteAnimationForNextActivityStart(
                                pendingIntent.getCreatorPackage(),
                                activityOptions.options.getRemoteAnimationAdapter(),
                                launchCookie);
            } catch (RemoteException e) {
                // Do nothing.
            }
        }
        activityOptions.options.setPendingIntentLaunchFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityOptions.options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);
        activityOptions.options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        options = Pair.create(options.first, activityOptions.options);
        if (pendingIntent.isActivity()) {
            logAppLaunch(itemInfo);
        }
        return RemoteViews.startPendingIntent(hostView, pendingIntent, options);
    }

    /**
     * Logs that the app was launched from the widget.
     * @param itemInfo the widget info.
     */
    private void logAppLaunch(Object itemInfo) {
        StatsLogManager.StatsLogger logger = mLauncher.getStatsLogManager().logger();
        if (itemInfo instanceof ItemInfo) {
            logger.withItemInfo((ItemInfo) itemInfo);
        }
        logger.log(LAUNCHER_APP_LAUNCH_TAP);
    }

    private LauncherAppWidgetHostView findHostViewAncestor(View v) {
        while (v != null) {
            if (v instanceof LauncherAppWidgetHostView) return (LauncherAppWidgetHostView) v;
            v = (View) v.getParent();
        }
        return null;
    }
}
