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

import com.android.quickstep.interaction.BackGestureTutorialFragment.TutorialStep;

import java.util.Optional;

/**
 * An implementation of {@link BackGestureTutorialController} that defines the behavior of the
 * {@link TutorialStep#ENGAGED}.
 */
final class BackGestureTutorialEngagedController extends BackGestureTutorialController {

    BackGestureTutorialEngagedController(
            BackGestureTutorialFragment fragment, BackGestureTutorialTypeInfo tutorialTypeInfo) {
        super(fragment, TutorialStep.ENGAGED, Optional.of(tutorialTypeInfo));
    }

    @Override
    void transitToController() {
        super.transitToController();
        mHandCoachingAnimation.maybeStartLoopedAnimation(mTutorialTypeInfo.get().getTutorialType());
    }

    @Override
    Optional<Integer> getTitleStringId() {
        return Optional.of(mTutorialTypeInfo.get().getTutorialPlaygroundTitleId());
    }

    @Override
    Optional<Integer> getSubtitleStringId() {
        return Optional.of(mTutorialTypeInfo.get().getTutorialEngagedSubtitleId());
    }

    @Override
    Optional<Integer> getActionButtonStringId() {
        return Optional.empty();
    }

    @Override
    Optional<Integer> getActionTextButtonStringId() {
        return Optional.empty();
    }

    @Override
    void onActionButtonClicked(View button) {
    }
}
