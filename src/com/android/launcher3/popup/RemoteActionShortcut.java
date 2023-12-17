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

package com.android.launcher3.popup;

import static com.android.launcher3.Utilities.allowBGLaunch;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_PAUSE_TAP;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfo;

import java.lang.ref.WeakReference;

@TargetApi(Build.VERSION_CODES.Q)
public class RemoteActionShortcut extends SystemShortcut<BaseDraggingActivity> {
    private static final String TAG = "RemoteActionShortcut";
    private static final boolean DEBUG = Utilities.IS_DEBUG_DEVICE;

    private final RemoteAction mAction;

    public RemoteActionShortcut(RemoteAction action,
            BaseDraggingActivity activity, ItemInfo itemInfo, View originalView) {
        super(0, R.id.action_remote_action_shortcut, activity, itemInfo, originalView);
        mAction = action;
    }

    @Override
    public void setIconAndLabelFor(View iconView, TextView labelView) {
        mAction.getIcon().loadDrawableAsync(iconView.getContext(),
                iconView::setBackground,
                MAIN_EXECUTOR.getHandler());
        labelView.setText(mAction.getTitle());
    }

    @Override
    public void setIconAndContentDescriptionFor(ImageView view) {
        mAction.getIcon().loadDrawableAsync(view.getContext(),
                view::setImageDrawable,
                MAIN_EXECUTOR.getHandler());
        view.setContentDescription(mAction.getContentDescription());
    }

    @Override
    public AccessibilityNodeInfo.AccessibilityAction createAccessibilityAction(Context context) {
        return new AccessibilityNodeInfo.AccessibilityAction(
                R.id.action_remote_action_shortcut, mAction.getContentDescription());
    }

    @Override
    public void onClick(View view) {
        AbstractFloatingView.closeAllOpenViews(mTarget);
        mTarget.getStatsLogManager().logger().withItemInfo(mItemInfo)
                .log(LAUNCHER_SYSTEM_SHORTCUT_PAUSE_TAP);

        final WeakReference<BaseDraggingActivity> weakTarget = new WeakReference<>(mTarget);
        final String actionIdentity = mAction.getTitle() + ", "
                + mItemInfo.getTargetComponent().getPackageName();

        ActivityOptions options = allowBGLaunch(ActivityOptions.makeBasic());
        try {
            if (DEBUG) Log.d(TAG, "Sending action: " + actionIdentity);
            mAction.getActionIntent().send(
                    mTarget,
                    0,
                    new Intent().putExtra(
                            Intent.EXTRA_PACKAGE_NAME,
                            mItemInfo.getTargetComponent().getPackageName()),
                    (pendingIntent, intent, resultCode, resultData, resultExtras) -> {
                        if (DEBUG) Log.d(TAG, "Action is complete: " + actionIdentity);
                        final BaseDraggingActivity target = weakTarget.get();
                        if (resultData != null && !resultData.isEmpty()) {
                            Log.e(TAG, "Remote action returned result: " + actionIdentity
                                    + " : " + resultData);
                            if (target != null) {
                                Toast.makeText(target, resultData, Toast.LENGTH_SHORT).show();
                            }
                        }
                    },
                    MAIN_EXECUTOR.getHandler(),
                    null,
                    options.toBundle());
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Remote action canceled: " + actionIdentity, e);
            Toast.makeText(mTarget, mTarget.getString(
                    R.string.remote_action_failed,
                    mAction.getTitle()),
                    Toast.LENGTH_SHORT)
                    .show();
        }
    }

    @Override
    public boolean isLeftGroup() {
        return true;
    }
}
