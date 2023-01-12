/*
 * Copyright 2022 The Android Open Source Project
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

import static com.android.launcher3.util.NavigationMode.THREE_BUTTONS;

import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.util.DisplayController;

/**
 * A rounded rectangular component containing a single TextView.
 * Appears when a split is in progress, and tells the user to select a second app to initiate
 * splitscreen.
 *
 * Appears and disappears concurrently with a FloatingTaskView.
 */
public class SplitInstructionsView extends FrameLayout {
    private final StatefulActivity mLauncher;
    private AppCompatTextView mTextView;

    public static final FloatProperty<SplitInstructionsView> UNFOLD =
            new FloatProperty<SplitInstructionsView>("SplitInstructionsUnfold") {
                @Override
                public void setValue(SplitInstructionsView splitInstructionsView, float v) {
                    splitInstructionsView.setScaleY(v);
                }

                @Override
                public Float get(SplitInstructionsView splitInstructionsView) {
                    return splitInstructionsView.getScaleY();
                }
            };

    public SplitInstructionsView(Context context) {
        this(context, null);
    }

    public SplitInstructionsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SplitInstructionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = (StatefulActivity) context;
    }

    static SplitInstructionsView getSplitInstructionsView(StatefulActivity launcher) {
        ViewGroup dragLayer = launcher.getDragLayer();
        final SplitInstructionsView splitInstructionsView =
                (SplitInstructionsView) launcher.getLayoutInflater().inflate(
                        R.layout.split_instructions_view,
                        dragLayer,
                        false
                );

        splitInstructionsView.mTextView = splitInstructionsView.findViewById(
                R.id.split_instructions_text);

        // Since textview overlays base view, and we sometimes manipulate the alpha of each
        // simultaneously, force overlapping rendering to false prevents redrawing of pixels,
        // improving performance at the cost of some accuracy.
        splitInstructionsView.forceHasOverlappingRendering(false);

        dragLayer.addView(splitInstructionsView);
        return splitInstructionsView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        ensureProperRotation();
    }

    void ensureProperRotation() {
        ((RecentsView) mLauncher.getOverviewPanel()).getPagedOrientationHandler()
                .setSplitInstructionsParams(
                        this,
                        mLauncher.getDeviceProfile(),
                        getMeasuredHeight(),
                        getMeasuredWidth(),
                        getThreeButtonNavShift()
                );
    }

    // In some cases, when user is using 3-button nav, there isn't enough room for both the
    // 3-button nav and a centered SplitInstructionsView. This function will return an int that will
    // be used to shift the SplitInstructionsView over a bit so that everything looks well-spaced.
    // In many cases, this will return 0, since we don't need to shift it away from the center.
    int getThreeButtonNavShift() {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        if ((DisplayController.getNavigationMode(getContext()) == THREE_BUTTONS)
                && ((dp.isTwoPanels) || (dp.isTablet && !dp.isLandscape))
                // If taskbar is in overview, overview action has dedicated space above nav buttons
                && !FeatureFlags.ENABLE_TASKBAR_IN_OVERVIEW.get()) {
            int navButtonWidth = getResources().getDimensionPixelSize(
                    R.dimen.taskbar_nav_buttons_size);
            int extraMargin = getResources().getDimensionPixelSize(
                    R.dimen.taskbar_split_instructions_margin);
            // Explanation: The 3-button nav for non-phones sits on one side of the screen, taking
            // up 3 buttons + a side margin worth of space. Our splitInstructionsView starts in the
            // center of the screen and we want to center it in the remaining space, therefore we
            // want to shift it over by half the 3-button layout's width.
            // If the user is using an RtL layout, we shift it the opposite way.
            return -((3 * navButtonWidth + extraMargin) / 2) * (isLayoutRtl() ? -1 : 1);
        } else {
            return 0;
        }
    }

    public AppCompatTextView getTextView() {
        return mTextView;
    }
}
