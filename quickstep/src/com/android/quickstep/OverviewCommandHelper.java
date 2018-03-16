/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.quickstep.TouchInteractionService.DEBUG_SHOW_OVERVIEW_BUTTON;

import android.annotation.TargetApi;
import android.app.ActivityManager.RecentTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.states.InternalStateHandler;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.ActivityManagerWrapper;

/**
 * Helper class to handle various atomic commands for switching between Overview.
 */
@TargetApi(Build.VERSION_CODES.P)
public class OverviewCommandHelper extends InternalStateHandler {

    private static final boolean DEBUG_START_FALLBACK_ACTIVITY = DEBUG_SHOW_OVERVIEW_BUTTON;

    private final Context mContext;
    private final ActivityManagerWrapper mAM;

    public final Intent homeIntent;
    public final ComponentName launcher;

    private long mLastToggleTime;

    public OverviewCommandHelper(Context context) {
        mContext = context;
        mAM = ActivityManagerWrapper.getInstance();

        homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = context.getPackageManager().resolveActivity(homeIntent, 0);
        launcher = new ComponentName(context.getPackageName(), info.activityInfo.name);
        // Clear the packageName as system can fail to dedupe it b/64108432
        homeIntent.setComponent(launcher).setPackage(null);
    }

    private void openRecents() {
        Intent intent = addToIntent(new Intent(homeIntent));
        mContext.startActivity(intent);
        initWhenReady();
    }

    public void onOverviewToggle() {
        if (DEBUG_START_FALLBACK_ACTIVITY) {
            mContext.startActivity(new Intent(mContext, RecentsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            return;
        }

        long elapsedTime = SystemClock.elapsedRealtime() - mLastToggleTime;
        mLastToggleTime = SystemClock.elapsedRealtime();

        if (isOverviewAlmostVisible()) {
            boolean isQuickTap = elapsedTime < ViewConfiguration.getDoubleTapTimeout();
            startNonLauncherTask(isQuickTap ? 2 : 1);
        } else {
            openRecents();
        }
    }

    public void onOverviewShown() {
        if (isOverviewAlmostVisible()) {
            final RecentsView rv = getLauncher().getOverviewPanel();
            rv.selectNextTask();
        } else {
            openRecents();
        }
    }

    public void onOverviewHidden() {
        final RecentsView rv = getLauncher().getOverviewPanel();
        rv.launchCurrentTask();
    }

    private void startNonLauncherTask(int backStackCount) {
        for (RecentTaskInfo rti : mAM.getRecentTasks(backStackCount, UserHandle.myUserId())) {
            backStackCount--;
            if (backStackCount == 0) {
                mAM.startActivityFromRecents(rti.id, null);
                break;
            }
        }
    }

    private boolean isOverviewAlmostVisible() {
        if (clearReference()) {
            return true;
        }
        if (!mAM.getRunningTask().topActivity.equals(launcher)) {
            return false;
        }
        Launcher launcher = getLauncher();
        return launcher != null && launcher.isStarted() && launcher.isInState(OVERVIEW);
    }

    private Launcher getLauncher() {
        return (Launcher) LauncherAppState.getInstance(mContext).getModel().getCallback();
    }

    @Override
    protected boolean init(Launcher launcher, boolean alreadyOnHome) {
        AbstractFloatingView.closeAllOpenViews(launcher, alreadyOnHome);
        launcher.getStateManager().goToState(OVERVIEW, alreadyOnHome);
        clearReference();
        return false;
    }

}
