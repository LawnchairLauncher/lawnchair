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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.StatefulActivity;

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

    public static SplitInstructionsView getSplitInstructionsView(StatefulActivity launcher) {
        ViewGroup dragLayer = launcher.getDragLayer();
        final SplitInstructionsView splitInstructionsView =
                (SplitInstructionsView) launcher.getLayoutInflater().inflate(
                        R.layout.split_instructions_view,
                        dragLayer,
                        false
                );
        splitInstructionsView.init();

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

    private void init() {
        mTextView = findViewById(R.id.split_instructions_text);

        if (!FeatureFlags.ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.get()) {
            mTextView.setCompoundDrawables(null, null, null, null);
            return;
        }

        mTextView.setOnTouchListener((v, event) -> {
            if (isTouchInsideRightCompoundDrawable(event)) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    exitSplitSelection();
                }
                return true;
            }
            return false;
        });
    }

    private void exitSplitSelection() {
        ((RecentsView) mLauncher.getOverviewPanel()).getSplitSelectController()
                .getSplitAnimationController().playPlaceholderDismissAnim(mLauncher);
        mLauncher.getStateManager().goToState(LauncherState.NORMAL);
    }

    private boolean isTouchInsideRightCompoundDrawable(MotionEvent event) {
        // Get the right compound drawable of the TextView.
        Drawable rightDrawable = mTextView.getCompoundDrawablesRelative()[2];

        // Check if the touch event intersects with the drawable's bounds.
        if (rightDrawable != null) {
            // We can get away w/o caring about the Y bounds since it's such a small view, if it's
            // above/below the drawable just assume they meant to touch it. ¯\_(ツ)_/¯
            return  event.getX() >= (mTextView.getWidth() - rightDrawable.getBounds().width());
        } else {
            return false;
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (!FeatureFlags.ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.get()) {
            return;
        }

        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                R.string.toast_split_select_cont_desc,
                getResources().getString(R.string.toast_split_select_cont_desc)
        ));
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (!FeatureFlags.ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.get()) {
            return super.performAccessibilityAction(action, arguments);
        }

        if (action == R.string.toast_split_select_cont_desc) {
            exitSplitSelection();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    void ensureProperRotation() {
        ((RecentsView) mLauncher.getOverviewPanel()).getPagedOrientationHandler()
                .setSplitInstructionsParams(
                        this,
                        mLauncher.getDeviceProfile(),
                        getMeasuredHeight(),
                        getMeasuredWidth()
                );
    }

    public AppCompatTextView getTextView() {
        return mTextView;
    }
}
