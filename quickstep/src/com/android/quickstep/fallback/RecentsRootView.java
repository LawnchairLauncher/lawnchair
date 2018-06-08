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
package com.android.quickstep.fallback;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.RecentsActivity;

public class RecentsRootView extends BaseDragLayer<RecentsActivity> {

    private final RecentsActivity mActivity;

    private final Point mLastKnownSize = new Point(10, 10);

    public RecentsRootView(Context context, AttributeSet attrs) {
        super(context, attrs, 1 /* alphaChannelCount */);
        mActivity = (RecentsActivity) BaseActivity.fromContext(context);
        setSystemUiVisibility(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    public Point getLastKnownSize() {
        return mLastKnownSize;
    }

    public void setup() {
        mControllers = new TouchController[] { new RecentsTaskController(mActivity) };
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Check size changes before the actual measure, to avoid multiple measure calls.
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (mLastKnownSize.x != width || mLastKnownSize.y != height) {
            mLastKnownSize.set(width, height);
            mActivity.onRootViewSizeChanged();
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @TargetApi(23)
    @Override
    protected boolean fitSystemWindows(Rect insets) {
        // Update device profile before notifying the children.
        mActivity.getDeviceProfile().updateInsets(insets);
        setInsets(insets);
        return true; // I'll take it from here
    }

    @Override
    public void setInsets(Rect insets) {
        // If the insets haven't changed, this is a no-op. Avoid unnecessary layout caused by
        // modifying child layout params.
        if (!insets.equals(mInsets)) {
            super.setInsets(insets);
        }
        setBackground(insets.top == 0 ? null
                : Themes.getAttrDrawable(getContext(), R.attr.workspaceStatusBarScrim));
    }

    public void dispatchInsets() {
        mActivity.getDeviceProfile().updateInsets(mInsets);
        super.setInsets(mInsets);
    }
}