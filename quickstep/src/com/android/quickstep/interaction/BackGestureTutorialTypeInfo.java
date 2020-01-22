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

import com.android.quickstep.interaction.BackGestureTutorialFragment.TutorialType;

/** Defines the UI element identifiers for the particular {@link TutorialType}. */
final class BackGestureTutorialTypeInfo {

    private final TutorialType mTutorialType;
    private final int mTutorialPlaygroundTitleId;
    private final int mTutorialEngagedSubtitleId;
    private final int mTutorialConfirmTitleId;
    private final int mTutorialConfirmSubtitleId;

    TutorialType getTutorialType() {
        return mTutorialType;
    }

    int getTutorialPlaygroundTitleId() {
        return mTutorialPlaygroundTitleId;
    }

    int getTutorialEngagedSubtitleId() {
        return mTutorialEngagedSubtitleId;
    }

    int getTutorialConfirmTitleId() {
        return mTutorialConfirmTitleId;
    }

    int getTutorialConfirmSubtitleId() {
        return mTutorialConfirmSubtitleId;
    }

    static Builder builder() {
        return new Builder();
    }

    private BackGestureTutorialTypeInfo(
            TutorialType tutorialType,
            int tutorialPlaygroundTitleId,
            int tutorialEngagedSubtitleId,
            int tutorialConfirmTitleId,
            int tutorialConfirmSubtitleId) {
        mTutorialType = tutorialType;
        mTutorialPlaygroundTitleId = tutorialPlaygroundTitleId;
        mTutorialEngagedSubtitleId = tutorialEngagedSubtitleId;
        mTutorialConfirmTitleId = tutorialConfirmTitleId;
        mTutorialConfirmSubtitleId = tutorialConfirmSubtitleId;
    }

    /** Builder for producing {@link BackGestureTutorialTypeInfo} objects. */
    static class Builder {

        private TutorialType mTutorialType;
        private Integer mTutorialPlaygroundTitleId;
        private Integer mTutorialEngagedSubtitleId;
        private Integer mTutorialConfirmTitleId;
        private Integer mTutorialConfirmSubtitleId;

        Builder setTutorialType(TutorialType tutorialType) {
            mTutorialType = tutorialType;
            return this;
        }

        Builder setTutorialPlaygroundTitleId(int stringId) {
            mTutorialPlaygroundTitleId = stringId;
            return this;
        }

        Builder setTutorialEngagedSubtitleId(int stringId) {
            mTutorialEngagedSubtitleId = stringId;
            return this;
        }

        Builder setTutorialConfirmTitleId(int stringId) {
            mTutorialConfirmTitleId = stringId;
            return this;
        }

        Builder setTutorialConfirmSubtitleId(int stringId) {
            mTutorialConfirmSubtitleId = stringId;
            return this;
        }

        BackGestureTutorialTypeInfo build() {
            return new BackGestureTutorialTypeInfo(
                    mTutorialType,
                    mTutorialPlaygroundTitleId,
                    mTutorialEngagedSubtitleId,
                    mTutorialConfirmTitleId,
                    mTutorialConfirmSubtitleId);
        }
    }
}
