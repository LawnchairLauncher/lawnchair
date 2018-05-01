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

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

public class ClearAllButton extends TextView {
    RecentsView mRecentsView;

    public ClearAllButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setRecentsView(RecentsView recentsView) {
        mRecentsView = recentsView;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        // Should be visible to accessibility even when completely covered by the task.
        // Otherwise, we won't be able to scroll to it.
        info.setVisibleToUser(true);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        final boolean res = super.performAccessibilityAction(action, arguments);
        if (action == ACTION_ACCESSIBILITY_FOCUS) {
            mRecentsView.revealClearAllButton();
        }
        return res;
    }
}
