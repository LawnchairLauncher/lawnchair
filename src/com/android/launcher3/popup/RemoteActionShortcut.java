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
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto;

public class RemoteActionShortcut extends SystemShortcut<Launcher> {
    private static final String TAG = "RemoteActionShortcut";

    private final RemoteAction mAction;

    public RemoteActionShortcut(RemoteAction action) {
        super(action.getIcon(), action.getTitle(), action.getContentDescription(),
                R.id.action_remote_action_shortcut);
        mAction = action;
    }

    @Override
    public View.OnClickListener getOnClickListener(
            final Launcher launcher, final ItemInfo itemInfo) {
        return view -> {
            AbstractFloatingView.closeAllOpenViews(launcher);

            try {
                mAction.getActionIntent().send(
                        launcher,
                        0,
                        new Intent().putExtra(
                                Intent.EXTRA_PACKAGE_NAME,
                                itemInfo.getTargetComponent().getPackageName()),
                        (pendingIntent, intent, resultCode, resultData, resultExtras) -> {
                            if (resultData != null && !resultData.isEmpty()) {
                                Log.e(TAG, "Remote action returned result: " + mAction.getTitle()
                                        + " : " + resultData);
                                Toast.makeText(launcher, resultData, Toast.LENGTH_SHORT).show();
                            }
                        },
                        new Handler(Looper.getMainLooper()));
            } catch (PendingIntent.CanceledException e) {
                Log.e(TAG, "Remote action canceled: " + mAction.getTitle(), e);
                Toast.makeText(launcher, launcher.getString(
                        R.string.remote_action_failed,
                        mAction.getTitle()),
                        Toast.LENGTH_SHORT)
                        .show();
            }

            launcher.getUserEventDispatcher().logActionOnControl(LauncherLogProto.Action.Touch.TAP,
                    LauncherLogProto.ControlType.REMOTE_ACTION_SHORTCUT, view);
        };
    }
}
