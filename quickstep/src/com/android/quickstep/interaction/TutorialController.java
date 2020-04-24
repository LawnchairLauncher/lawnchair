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

import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;

abstract class TutorialController {

    final TutorialFragment mTutorialFragment;
    final TutorialType mTutorialType;

    final ImageButton mCloseButton;
    final TextView mTitleTextView;
    final TextView mSubtitleTextView;
    final TutorialHandAnimation mHandCoachingAnimation;
    final ImageView mHandCoachingView;
    final Button mActionTextButton;
    final Button mActionButton;

    TutorialController(TutorialFragment tutorialFragment, TutorialType tutorialType) {
        mTutorialFragment = tutorialFragment;
        mTutorialType = tutorialType;

        View rootView = tutorialFragment.getRootView();
        mCloseButton = rootView.findViewById(R.id.gesture_tutorial_fragment_close_button);
        mCloseButton.setOnClickListener(button -> mTutorialFragment.closeTutorial());
        mTitleTextView = rootView.findViewById(R.id.gesture_tutorial_fragment_title_view);
        mSubtitleTextView = rootView.findViewById(R.id.gesture_tutorial_fragment_subtitle_view);
        mHandCoachingAnimation = tutorialFragment.getHandAnimation();
        mHandCoachingView = rootView.findViewById(R.id.gesture_tutorial_fragment_hand_coaching);
        mHandCoachingView.bringToFront();
        mActionTextButton =
                rootView.findViewById(R.id.gesture_tutorial_fragment_action_text_button);
        mActionButton = rootView.findViewById(R.id.gesture_tutorial_fragment_action_button);
    }

    abstract void onBackGestureAttempted(BackGestureResult result);

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

    void onActionButtonClicked(View button) {}

    void onActionTextButtonClicked(View button) {}

    void hideHandCoachingAnimation() {
        mHandCoachingAnimation.stop();
    }

    @CallSuper
    void transitToController() {
        updateTitles();
        updateActionButtons();
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

    /** Denotes the type of the tutorial. */
    enum TutorialType {
        RIGHT_EDGE_BACK_NAVIGATION,
        LEFT_EDGE_BACK_NAVIGATION,
        BACK_NAVIGATION_COMPLETE,
        HOME_NAVIGATION,
        HOME_NAVIGATION_COMPLETE
    }
}
