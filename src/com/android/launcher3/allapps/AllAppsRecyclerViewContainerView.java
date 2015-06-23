/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.BubbleTextView.BubbleTextShadowHandler;
import com.android.launcher3.ClickShadowView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

/**
 * A container for RecyclerView to allow for the click shadow view to be shown behind an icon that
 * is launching.
 */
public class AllAppsRecyclerViewContainerView extends FrameLayout
        implements BubbleTextShadowHandler {

    private final ClickShadowView mTouchFeedbackView;
    private View mPredictionBarView;

    public AllAppsRecyclerViewContainerView(Context context) {
        this(context, null);
    }

    public AllAppsRecyclerViewContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsRecyclerViewContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        Launcher launcher = (Launcher) context;
        DeviceProfile grid = launcher.getDeviceProfile();

        mTouchFeedbackView = new ClickShadowView(context);

        // Make the feedback view large enough to hold the blur bitmap.
        int size = grid.allAppsIconSizePx + mTouchFeedbackView.getExtraSize();
        addView(mTouchFeedbackView, size, size);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPredictionBarView = findViewById(R.id.prediction_bar);
    }

    @Override
    public void setPressedIcon(BubbleTextView icon, Bitmap background) {
        if (icon == null || background == null) {
            mTouchFeedbackView.setBitmap(null);
            mTouchFeedbackView.animate().cancel();
        } else if (mTouchFeedbackView.setBitmap(background)) {
            mTouchFeedbackView.alignWithIconView(icon, (ViewGroup) icon.getParent());
            mTouchFeedbackView.animateShadow();
        }
    }

    /**
     * This allows us to have custom drawing order, while keeping touch handling in correct z-order.
     */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        final long drawingTime = getDrawingTime();

        // Draw the click feedback first (since it is always on the bottom)
        if (mTouchFeedbackView != null && mTouchFeedbackView.getVisibility() == View.VISIBLE) {
            drawChild(canvas, mTouchFeedbackView, drawingTime);
        }

        // Then draw the prediction bar, since it needs to be "under" the recycler view to get the
        // right edge effect to be drawn over it
        if (mPredictionBarView != null && mPredictionBarView.getVisibility() == View.VISIBLE) {
            drawChild(canvas, mPredictionBarView, drawingTime);
        }

        // Draw the remaining views
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            if (v != mTouchFeedbackView && v != mPredictionBarView &&
                    v.getVisibility() == View.VISIBLE) {
                drawChild(canvas, v, drawingTime);
            }
        }
    }
}
