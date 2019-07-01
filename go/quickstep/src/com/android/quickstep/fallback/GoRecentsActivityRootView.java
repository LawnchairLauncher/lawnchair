/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.fallback;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.WindowInsets;

import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.RecentsActivity;

/**
 * Minimal implementation of {@link BaseDragLayer} for Go's fallback recents activity.
 */
public final class GoRecentsActivityRootView extends BaseDragLayer<RecentsActivity> {
    public GoRecentsActivityRootView(Context context, AttributeSet attrs) {
        super(context, attrs, 1 /* alphaChannelCount */);
        // Go leaves touch control to the view itself.
        mControllers = new TouchController[0];
        setSystemUiVisibility(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void setInsets(Rect insets) {
        if (insets.equals(mInsets)) {
            return;
        }
        super.setInsets(insets);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        Insets sysInsets = insets.getSystemWindowInsets();
        setInsets(new Rect(sysInsets.left, sysInsets.top, sysInsets.right, sysInsets.bottom));
        return insets.consumeSystemWindowInsets();
    }
}
