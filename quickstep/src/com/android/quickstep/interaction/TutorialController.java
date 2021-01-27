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

import android.content.Context;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.views.ClipIconView;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureAttemptCallback;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureAttemptCallback;

abstract class TutorialController implements BackGestureAttemptCallback,
        NavBarGestureAttemptCallback {

    private static final int FEEDBACK_VISIBLE_MS = 3000;
    private static final int FEEDBACK_ANIMATION_MS = 500;
    private static final int RIPPLE_VISIBLE_MS = 300;

    final TutorialFragment mTutorialFragment;
    TutorialType mTutorialType;
    final Context mContext;

    final ImageButton mCloseButton;
    final TextView mTitleTextView;
    final TextView mSubtitleTextView;
    final TextView mFeedbackView;
    final ClipIconView mFakeIconView;
    final View mFakeTaskView;
    final View mRippleView;
    final RippleDrawable mRippleDrawable;
    final TutorialHandAnimation mHandCoachingAnimation;
    final ImageView mHandCoachingView;
    final Button mActionTextButton;
    final Button mActionButton;
    private final Runnable mHideFeedbackRunnable;

    TutorialController(TutorialFragment tutorialFragment, TutorialType tutorialType) {
        mTutorialFragment = tutorialFragment;
        mTutorialType = tutorialType;
        mContext = mTutorialFragment.getContext();

        View rootView = tutorialFragment.getRootView();
        mCloseButton = rootView.findViewById(R.id.gesture_tutorial_fragment_close_button);
        mCloseButton.setOnClickListener(button -> mTutorialFragment.closeTutorial());
        mTitleTextView = rootView.findViewById(R.id.gesture_tutorial_fragment_title_view);
        mSubtitleTextView = rootView.findViewById(R.id.gesture_tutorial_fragment_subtitle_view);
        mFeedbackView = rootView.findViewById(R.id.gesture_tutorial_fragment_feedback_view);
        mFakeIconView = rootView.findViewById(R.id.gesture_tutorial_fake_icon_view);
        mFakeTaskView = rootView.findViewById(R.id.gesture_tutorial_fake_task_view);
        mRippleView = rootView.findViewById(R.id.gesture_tutorial_ripple_view);
        mRippleDrawable = (RippleDrawable) mRippleView.getBackground();
        mHandCoachingAnimation = tutorialFragment.getHandAnimation();
        mHandCoachingView = rootView.findViewById(R.id.gesture_tutorial_fragment_hand_coaching);
        mHandCoachingView.bringToFront();
        mActionTextButton =
                rootView.findViewById(R.id.gesture_tutorial_fragment_action_text_button);
        mActionButton = rootView.findViewById(R.id.gesture_tutorial_fragment_action_button);

        mHideFeedbackRunnable =
                () -> mFeedbackView.animate().alpha(0).setDuration(FEEDBACK_ANIMATION_MS)
                        .withEndAction(this::showHandCoachingAnimation).start();
    }

    void setTutorialType(TutorialType tutorialType) {
        mTutorialType = tutorialType;
    }

    @Nullable
    Integer getTitleStringId() {
        return null;
    }

    @Nullable
    Integer getSubtitleStringId() {
        return null;
    }

    @Nullable
    Integer getActionButtonStringId() {
        return null;
    }

    @Nullable
    Integer getActionTextButtonStringId() {
        return null;
    }

    void showFeedback(int resId) {
        hideHandCoachingAnimation();
        mFeedbackView.setText(resId);
        mFeedbackView.animate().alpha(1).setDuration(FEEDBACK_ANIMATION_MS).start();
        mFeedbackView.removeCallbacks(mHideFeedbackRunnable);
        mFeedbackView.postDelayed(mHideFeedbackRunnable, FEEDBACK_VISIBLE_MS);
    }

    void hideFeedback() {
        mFeedbackView.setText(null);
        mFeedbackView.removeCallbacks(mHideFeedbackRunnable);
        mFeedbackView.clearAnimation();
        mFeedbackView.setAlpha(0);
    }

    void setRippleHotspot(float x, float y) {
        mRippleDrawable.setHotspot(x, y);
    }

    void showRippleEffect(@Nullable Runnable onCompleteRunnable) {
        mRippleDrawable.setState(
                new int[] {android.R.attr.state_pressed, android.R.attr.state_enabled});
        mRippleView.postDelayed(() -> {
            mRippleDrawable.setState(new int[] {});
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
        }, RIPPLE_VISIBLE_MS);
    }

    void onActionButtonClicked(View button) {}

    void onActionTextButtonClicked(View button) {}

    void showHandCoachingAnimation() {
        if (isComplete()) {
            return;
        }
        mHandCoachingAnimation.startLoopedAnimation(mTutorialType);
    }

    void hideHandCoachingAnimation() {
        mHandCoachingAnimation.stop();
        mHandCoachingView.setVisibility(View.INVISIBLE);
    }

    @CallSuper
    void transitToController() {
        hideFeedback();
        updateTitles();
        updateActionButtons();

        if (isComplete()) {
            hideHandCoachingAnimation();
        } else {
            showHandCoachingAnimation();
        }
    }

    private void updateTitles() {
        updateTitleView(mTitleTextView, getTitleStringId(),
                R.style.TextAppearance_GestureTutorial_Title);
        updateTitleView(mSubtitleTextView, getSubtitleStringId(),
                R.style.TextAppearance_GestureTutorial_Subtitle);
    }

    private void updateTitleView(TextView textView, @Nullable Integer stringId, int styleId) {
        if (stringId == null) {
            textView.setVisibility(View.GONE);
            return;
        }

        textView.setVisibility(View.VISIBLE);
        textView.setText(stringId);
        textView.setTextAppearance(styleId);
    }

    private void updateActionButtons() {
        updateButton(mActionButton, getActionButtonStringId(), this::onActionButtonClicked);
        updateButton(
                mActionTextButton, getActionTextButtonStringId(), this::onActionTextButtonClicked);
    }

    private void updateButton(Button button, @Nullable Integer stringId, OnClickListener listener) {
        if (stringId == null) {
            button.setVisibility(View.INVISIBLE);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setText(stringId);
        button.setOnClickListener(listener);
    }

    private boolean isComplete() {
        return mTutorialType == TutorialType.BACK_NAVIGATION_COMPLETE
                || mTutorialType == TutorialType.HOME_NAVIGATION_COMPLETE
                || mTutorialType == TutorialType.OVERVIEW_NAVIGATION_COMPLETE
                || mTutorialType == TutorialType.ASSISTANT_COMPLETE;
    }

    /** Denotes the type of the tutorial. */
    enum TutorialType {
        RIGHT_EDGE_BACK_NAVIGATION,
        LEFT_EDGE_BACK_NAVIGATION,
        BACK_NAVIGATION_COMPLETE,
        HOME_NAVIGATION,
        HOME_NAVIGATION_COMPLETE,
        OVERVIEW_NAVIGATION,
        OVERVIEW_NAVIGATION_COMPLETE,
        ASSISTANT,
        ASSISTANT_COMPLETE
    }
}
