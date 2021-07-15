/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.launcher3.util;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;

public final class SplitConfigurationOptions {

    ///////////////////////////////////
    // Taken from
    // frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/splitscreen/SplitScreen.java
    /**
     * Stage position isn't specified normally meaning to use what ever it is currently set to.
     */
    public static final int STAGE_POSITION_UNDEFINED = -1;
    /**
     * Specifies that a stage is positioned at the top half of the screen if
     * in portrait mode or at the left half of the screen if in landscape mode.
     */
    public static final int STAGE_POSITION_TOP_OR_LEFT = 0;

    /**
     * Specifies that a stage is positioned at the bottom half of the screen if
     * in portrait mode or at the right half of the screen if in landscape mode.
     */
    public static final int STAGE_POSITION_BOTTOM_OR_RIGHT = 1;

    @Retention(SOURCE)
    @IntDef({STAGE_POSITION_UNDEFINED, STAGE_POSITION_TOP_OR_LEFT, STAGE_POSITION_BOTTOM_OR_RIGHT})
    public @interface StagePosition {}

    /**
     * Stage type isn't specified normally meaning to use what ever the default is.
     * E.g. exit split-screen and launch the app in fullscreen.
     */
    public static final int STAGE_TYPE_UNDEFINED = -1;
    /**
     * The main stage type.
     */
    public static final int STAGE_TYPE_MAIN = 0;

    /**
     * The side stage type.
     */
    public static final int STAGE_TYPE_SIDE = 1;

    @IntDef({STAGE_TYPE_UNDEFINED, STAGE_TYPE_MAIN, STAGE_TYPE_SIDE})
    public @interface StageType {}
    ///////////////////////////////////

    public static class SplitPositionOption {
        public final int mIconResId;
        public final int mTextResId;
        @StagePosition
        public final int mStagePosition;

        @StageType
        public final int mStageType;

        public SplitPositionOption(int iconResId, int textResId, int stagePosition, int stageType) {
            mIconResId = iconResId;
            mTextResId = textResId;
            mStagePosition = stagePosition;
            mStageType = stageType;
        }
    }
}
