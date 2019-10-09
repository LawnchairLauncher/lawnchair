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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.quickstep.RecentsActivity;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;

public class FallbackRecentsView extends RecentsView<RecentsActivity> {

    public FallbackRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FallbackRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOverviewStateEnabled(true);
        getQuickScrubController().onFinishedTransitionToQuickScrub();
    }

    @Override
    protected void startHome() {
        mActivity.startHome();
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        updateEmptyMessage();
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        updateEmptyMessage();
    }

    @Override
    public void draw(Canvas canvas) {
        maybeDrawEmptyMessage(canvas);
        super.draw(canvas);
    }

    @Override
    protected void getTaskSize(DeviceProfile dp, Rect outRect) {
        LayoutUtils.calculateFallbackTaskSize(getContext(), dp, outRect);
    }

    @Override
    public boolean shouldUseMultiWindowTaskSizeStrategy() {
        // Just use the activity task size for multi-window as well.
        return false;
    }
}
