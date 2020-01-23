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

import com.android.launcher3.R;
import com.android.quickstep.interaction.BackGestureTutorialFragment.TutorialType;

/** Provides instances of {@link BackGestureTutorialTypeInfo} for each {@link TutorialType}. */
final class BackGestureTutorialTypeInfoProvider {

    private static final BackGestureTutorialTypeInfo RIGHT_EDGE_BACK_NAV_TUTORIAL_INFO =
            BackGestureTutorialTypeInfo.builder()
                    .setTutorialType(TutorialType.RIGHT_EDGE_BACK_NAVIGATION)
                    .setTutorialPlaygroundTitleId(
                            R.string.back_gesture_tutorial_playground_title_swipe_inward_right_edge)
                    .setTutorialEngagedSubtitleId(
                            R.string.back_gesture_tutorial_engaged_subtitle_swipe_inward_right_edge)
                    .setTutorialConfirmTitleId(R.string.back_gesture_tutorial_confirm_title)
                    .setTutorialConfirmSubtitleId(R.string.back_gesture_tutorial_confirm_subtitle)
                    .build();

    private static final BackGestureTutorialTypeInfo LEFT_EDGE_BACK_NAV_TUTORIAL_INFO =
            BackGestureTutorialTypeInfo.builder()
                    .setTutorialType(TutorialType.LEFT_EDGE_BACK_NAVIGATION)
                    .setTutorialPlaygroundTitleId(
                            R.string.back_gesture_tutorial_playground_title_swipe_inward_left_edge)
                    .setTutorialEngagedSubtitleId(
                            R.string.back_gesture_tutorial_engaged_subtitle_swipe_inward_left_edge)
                    .setTutorialConfirmTitleId(R.string.back_gesture_tutorial_confirm_title)
                    .setTutorialConfirmSubtitleId(R.string.back_gesture_tutorial_confirm_subtitle)
                    .build();

    static BackGestureTutorialTypeInfo getTutorialTypeInfo(TutorialType tutorialType) {
        switch (tutorialType) {
            case RIGHT_EDGE_BACK_NAVIGATION:
                return RIGHT_EDGE_BACK_NAV_TUTORIAL_INFO;
            case LEFT_EDGE_BACK_NAVIGATION:
                return LEFT_EDGE_BACK_NAV_TUTORIAL_INFO;
            default:
                throw new AssertionError("Unexpected tutorial type: " + tutorialType);
        }
    }

    private BackGestureTutorialTypeInfoProvider() {
    }
}
