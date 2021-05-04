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

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.RemoteViews;

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

    @Override
    public boolean onInteraction(View view, PendingIntent pendingIntent,
            RemoteViews.RemoteResponse remoteResponse) {
        LauncherAppWidgetHostView hostView = findHostViewAncestor(view);
        if (hostView == null) {
            Log.e(TAG, "View did not have a LauncherAppWidgetHostView ancestor.");
            return RemoteViews.startPendingIntent(hostView, pendingIntent,
                    remoteResponse.getLaunchOptions(view));
        }
        Pair<Intent, ActivityOptions> options = remoteResponse.getLaunchOptions(hostView);
        ActivityOptionsWrapper activityOptions = mLauncher.getAppTransitionManager()
                .getActivityLaunchOptions(mLauncher, hostView);
        activityOptions.options.setPendingIntentLaunchFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Object itemInfo = hostView.getTag();
        if (itemInfo instanceof ItemInfo) {
            mLauncher.addLaunchCookie((ItemInfo) itemInfo, activityOptions.options);
        }
        options = Pair.create(options.first, activityOptions.options);
        return RemoteViews.startPendingIntent(hostView, pendingIntent, options);
    }

    private LauncherAppWidgetHostView findHostViewAncestor(View v) {
        while (v != null) {
            if (v instanceof LauncherAppWidgetHostView) return (LauncherAppWidgetHostView) v;
            v = (View) v.getParent();
        }
        return null;
    }
}
