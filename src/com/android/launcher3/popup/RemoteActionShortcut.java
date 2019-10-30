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

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.userevent.nano.LauncherLogProto;

public class RemoteActionShortcut extends SystemShortcut<BaseDraggingActivity> {
    private static final String TAG = "RemoteActionShortcut";
    private static final boolean DEBUG = Utilities.IS_DEBUG_DEVICE;

    private final RemoteAction mAction;

    public RemoteActionShortcut(RemoteAction action) {
        super(action.getIcon(), action.getTitle(), action.getContentDescription(),
                R.id.action_remote_action_shortcut);
        mAction = action;
    }

    @Override
    public View.OnClickListener getOnClickListener(
            final BaseDraggingActivity activity, final ItemInfo itemInfo) {
        return view -> {
            AbstractFloatingView.closeAllOpenViews(activity);

            final String actionIdentity = mAction.getTitle() + ", " +
                    itemInfo.getTargetComponent().getPackageName();
            try {
                if (DEBUG) Log.d(TAG, "Sending action: " + actionIdentity);
                mAction.getActionIntent().send(
                        activity,
                        0,
                        new Intent().putExtra(
                                Intent.EXTRA_PACKAGE_NAME,
                                itemInfo.getTargetComponent().getPackageName()),
                        (pendingIntent, intent, resultCode, resultData, resultExtras) -> {
                            if (DEBUG) Log.d(TAG, "Action is complete: " + actionIdentity);
                            if (resultData != null && !resultData.isEmpty()) {
                                Log.e(TAG, "Remote action returned result: " + actionIdentity
                                        + " : " + resultData);
                                Toast.makeText(activity, resultData, Toast.LENGTH_SHORT).show();
                            }
                        },
                        new Handler(Looper.getMainLooper()));
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "Remote action canceled: " + actionIdentity, e);
                Toast.makeText(activity, activity.getString(
                        R.string.remote_action_failed,
                        mAction.getTitle()),
                        Toast.LENGTH_SHORT)
                        .show();
            }

            activity.getUserEventDispatcher().logActionOnControl(LauncherLogProto.Action.Touch.TAP,
                    LauncherLogProto.ControlType.REMOTE_ACTION_SHORTCUT, view);
        };
    }

    @Override
    public boolean isLeftGroup() {
        return true;
    }
}
