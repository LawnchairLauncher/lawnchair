/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.content.Context.RECEIVER_EXPORTED;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.util.SimpleBroadcastReceiver;

import java.io.PrintWriter;

/**
 * NudgeViewController handles the broadcasts sent to launcher which will then update the view.
 */
public class NudgeViewController implements TaskbarControllers.LoggableTaskbarController{

    private static final String NAV_UPDATE_ACTION = "android.app.action.UPDATE_NAVBAR";
    private static final String GAME = "game";
    private static final String TRANSLATE = "translate";
    private static final String SHOW_NUDGE = "showNudge";
    private static final String NUDGE_ICON = "nudgeIcon";
    private final TaskbarActivityContext mActivity;
    @Nullable
    private final NudgeView mNudgeView;
    private final Drawable mTranslateIcon;
    private final Drawable mGameIcon;

    @Nullable
    private SimpleBroadcastReceiver mNudgeReceiver;

    public NudgeViewController(TaskbarActivityContext activity,
            @Nullable NudgeView nudgeView) {
        mActivity = activity;
        mNudgeView = nudgeView;
        final Resources resources = mActivity.getResources();
        if (Flags.nudgePill() && mNudgeView != null) {
            mNudgeReceiver = new SimpleBroadcastReceiver(
                    mActivity, UI_HELPER_EXECUTOR, this::shouldChangeNavBar);
            mNudgeReceiver.register(RECEIVER_EXPORTED, NAV_UPDATE_ACTION);
        }
        mTranslateIcon = resources.getDrawable(R.drawable.ic_translate);
        mGameIcon = resources.getDrawable(R.drawable.ic_game);
    }

    private void shouldChangeNavBar(Intent i) {
        if (!mActivity.isPhoneGestureNavMode() || !Flags.nudgePill()) {
            return;
        }
        Bundle bundle = i.getExtras();
        boolean showNudge = bundle.getBoolean(SHOW_NUDGE, false);
        String iconToUse = bundle.getString(NUDGE_ICON, "");
        Drawable icon = null;
        if (GAME.equals(iconToUse)) {
            icon = mGameIcon;
        } else if (TRANSLATE.equals(iconToUse)) {
            icon = mTranslateIcon;
        }
        // Nudge icon will only show if there is a valid icon to show.
        mNudgeView.updateNudgeIcon(icon != null && showNudge /* showNudge */, icon,
                mActivity.getControllers().stashedHandleViewController.getStashedHandleAlpha());
    }

    public void onDestroy() {
        if (mNudgeReceiver != null) {
            mNudgeReceiver.unregisterReceiverSafely();
        }
        mNudgeReceiver = null;
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "NudgeViewController:");
        pw.println(prefix + "\tmNudgeView=" + mNudgeView);
        pw.println(prefix + "\tmNudgeReceiver=" + mNudgeReceiver);
    }
}
