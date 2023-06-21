/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.interaction;

import static com.android.launcher3.config.FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/** Root layout that TutorialFragment uses to intercept motion events. */
public class RootSandboxLayout extends RelativeLayout {

    private final Rect mTempStepIndicatorBounds = new Rect();
    private final Rect mTempInclusionBounds = new Rect();
    private final Rect mTempExclusionBounds = new Rect();

    private View mFeedbackView;
    private View mTutorialStepView;
    private View mSkipButton;
    private View mDoneButton;

    public RootSandboxLayout(Context context) {
        super(context);
    }

    public RootSandboxLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RootSandboxLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        return ((TutorialFragment) FragmentManager.findFragment(this))
                .onInterceptTouch(motionEvent);
    }

    /**
     * Returns this view's fullscreen height. This method is agnostic of this view's actual height.
     */
    public int getFullscreenHeight() {
        Insets insets = getRootWindowInsets().getInsets(WindowInsets.Type.systemBars());

        return getHeight() + insets.top + insets.bottom;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            return;
        }
        mFeedbackView = findViewById(R.id.gesture_tutorial_fragment_feedback_view);
        mTutorialStepView =
                mFeedbackView.findViewById(R.id.gesture_tutorial_fragment_feedback_tutorial_step);
        mSkipButton = mFeedbackView.findViewById(R.id.gesture_tutorial_fragment_close_button);
        mDoneButton = mFeedbackView.findViewById(R.id.gesture_tutorial_fragment_action_button);

        mFeedbackView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    if (mSkipButton.getVisibility() != VISIBLE
                            && mDoneButton.getVisibility() != VISIBLE) {
                        return;
                    }
                    // Either the skip or the done button is ever shown at once, never both.
                    boolean showingSkipButton = mSkipButton.getVisibility() == VISIBLE;
                    boolean isRTL = Utilities.isRtl(getContext().getResources());
                    updateTutorialStepViewTranslation(
                            showingSkipButton ? mSkipButton : mDoneButton,
                            // Translate the step indicator away from whichever button is being
                            // shown. The skip button in on the left in LTR or on the right in RTL.
                            // The done button is on the right in LTR or left in RTL.
                            (showingSkipButton && !isRTL) || (!showingSkipButton && isRTL));
                });
    }

    private void updateTutorialStepViewTranslation(
            @NonNull View anchorView, boolean translateToRight) {
        mTempStepIndicatorBounds.set(
                mTutorialStepView.getLeft(),
                mTutorialStepView.getTop(),
                mTutorialStepView.getRight(),
                mTutorialStepView.getBottom());
        mTempInclusionBounds.set(0, 0, mFeedbackView.getWidth(), mFeedbackView.getHeight());
        mTempExclusionBounds.set(
                anchorView.getLeft(),
                anchorView.getTop(),
                anchorView.getRight(),
                anchorView.getBottom());

        Utilities.translateOverlappingView(
                mTutorialStepView,
                mTempStepIndicatorBounds,
                mTempInclusionBounds,
                mTempExclusionBounds,
                translateToRight ? Utilities.TRANSLATE_RIGHT : Utilities.TRANSLATE_LEFT);
    }
}
