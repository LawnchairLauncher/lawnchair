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
package com.android.quickstep.views;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;
import com.android.launcher3.views.LauncherDragIndicator;

public class QuickstepDragIndicator extends LauncherDragIndicator {

    public QuickstepDragIndicator(Context context) {
        super(context);
    }

    public QuickstepDragIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public QuickstepDragIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private boolean isInOverview() {
        return mLauncher.isInState(OVERVIEW);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setContentDescription(getContext().getString(R.string.all_apps_button_label));
    }

    @Override
    protected void initCustomActions(AccessibilityNodeInfo info) {
        if (!isInOverview()) {
            super.initCustomActions(info);
        }
    }

    @Override
    public void onClick(View view) {
        mLauncher.getUserEventDispatcher().logActionOnControl(Action.Touch.TAP,
                ControlType.ALL_APPS_BUTTON,
                isInOverview() ? ContainerType.TASKSWITCHER : ContainerType.WORKSPACE);
        mLauncher.getStateManager().goToState(ALL_APPS);
    }
}
