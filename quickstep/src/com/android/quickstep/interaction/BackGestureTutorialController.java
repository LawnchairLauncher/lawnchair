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
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.R;
import com.android.quickstep.interaction.BackGestureTutorialFragment.TutorialStep;
import com.android.quickstep.interaction.BackGestureTutorialFragment.TutorialType;

import java.util.Optional;

/**
 * Defines the behavior of the particular {@link TutorialStep} and implements the transition to it.
 */
abstract class BackGestureTutorialController {

    final BackGestureTutorialFragment mFragment;
    final TutorialStep mTutorialStep;
    final Optional<BackGestureTutorialTypeInfo> mTutorialTypeInfo;
    final Button mActionTextButton;
    final Button mActionButton;
    final TextView mSubtitleTextView;
    final ImageButton mCloseButton;
    final BackGestureTutorialHandAnimation mHandCoachingAnimation;
    final LinearLayout mTitlesContainer;

    private final TextView mTitleTextView;
    private final ImageView mHandCoachingView;

    BackGestureTutorialController(
            BackGestureTutorialFragment fragment,
            TutorialStep tutorialStep,
            Optional<BackGestureTutorialTypeInfo> tutorialTypeInfo) {
        mFragment = fragment;
        mTutorialStep = tutorialStep;
        mTutorialTypeInfo = tutorialTypeInfo;

        View rootView = fragment.getRootView();
        mActionTextButton = rootView.findViewById(
                R.id.back_gesture_tutorial_fragment_action_text_button);
        mActionButton = rootView.findViewById(R.id.back_gesture_tutorial_fragment_action_button);
        mSubtitleTextView = rootView.findViewById(
                R.id.back_gesture_tutorial_fragment_subtitle_view);
        mTitleTextView = rootView.findViewById(R.id.back_gesture_tutorial_fragment_title_view);
        mHandCoachingView = rootView.findViewById(
                R.id.back_gesture_tutorial_fragment_hand_coaching);
        mHandCoachingAnimation = mFragment.getHandAnimation();
        mHandCoachingView.bringToFront();
        mCloseButton = rootView.findViewById(R.id.back_gesture_tutorial_fragment_close_button);
        mTitlesContainer = rootView.findViewById(
                R.id.back_gesture_tutorial_fragment_titles_container);
    }

    void transitToController() {
        updateTitles();
        updateActionButtons();
    }

    void hideHandCoachingAnimation() {
        mHandCoachingAnimation.stop();
    }

    void onGestureDetected() {
        hideHandCoachingAnimation();

        if (mTutorialStep == TutorialStep.CONFIRM) {
            mFragment.closeTutorial();
            return;
        }

        if (mTutorialTypeInfo.get().getTutorialType() == TutorialType.RIGHT_EDGE_BACK_NAVIGATION) {
            mFragment.changeController(TutorialStep.ENGAGED,
                    TutorialType.LEFT_EDGE_BACK_NAVIGATION);
            return;
        }

        mFragment.changeController(TutorialStep.CONFIRM);
    }

    abstract Optional<Integer> getTitleStringId();

    abstract Optional<Integer> getSubtitleStringId();

    abstract Optional<Integer> getActionButtonStringId();

    abstract Optional<Integer> getActionTextButtonStringId();

    abstract void onActionButtonClicked(View button);

    private void updateActionButtons() {
        updateButton(mActionButton, getActionButtonStringId(), this::onActionButtonClicked);
        updateButton(mActionTextButton, getActionTextButtonStringId(), this::onActionButtonClicked);
    }

    private static void updateButton(Button button, Optional<Integer> stringId,
            View.OnClickListener listener) {
        if (!stringId.isPresent()) {
            button.setVisibility(View.INVISIBLE);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setText(stringId.get());
        button.setOnClickListener(listener);
    }

    private void updateTitles() {
        updateTitleView(mTitleTextView, getTitleStringId(),
                R.style.TextAppearance_BackGestureTutorial_Title);
        updateTitleView(mSubtitleTextView, getSubtitleStringId(),
                R.style.TextAppearance_BackGestureTutorial_Subtitle);
    }

    private static void updateTitleView(TextView textView, Optional<Integer> stringId,
            int styleId) {
        if (!stringId.isPresent()) {
            textView.setVisibility(View.GONE);
            return;
        }

        textView.setVisibility(View.VISIBLE);
        textView.setText(stringId.get());
        textView.setTextAppearance(styleId);
    }

    /**
     * Constructs {@link BackGestureTutorialController} for providing {@link TutorialType} and
     * {@link TutorialStep}.
     */
    static Optional<BackGestureTutorialController> getTutorialController(
            BackGestureTutorialFragment fragment, TutorialStep tutorialStep,
            TutorialType tutorialType) {
        BackGestureTutorialTypeInfo tutorialTypeInfo =
                BackGestureTutorialTypeInfoProvider.getTutorialTypeInfo(tutorialType);
        switch (tutorialStep) {
            case ENGAGED:
                return Optional.of(
                        new BackGestureTutorialEngagedController(fragment, tutorialTypeInfo));
            case CONFIRM:
                return Optional.of(
                        new BackGestureTutorialConfirmController(fragment, tutorialTypeInfo));
            default:
                throw new AssertionError("Unexpected tutorial step: " + tutorialStep);
        }
    }
}
