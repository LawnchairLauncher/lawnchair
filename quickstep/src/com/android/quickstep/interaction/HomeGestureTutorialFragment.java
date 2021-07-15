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

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.quickstep.interaction.TutorialController.TutorialType;

/** Shows the Home gesture interactive tutorial. */
public class HomeGestureTutorialFragment extends TutorialFragment {
    @Nullable
    @Override
    Integer getFeedbackVideoResId(boolean forDarkMode) {
        return forDarkMode
                ? R.drawable.gesture_tutorial_motion_home_dark_mode
                : R.drawable.gesture_tutorial_motion_home_light_mode;
    }

    @Nullable
    @Override
    Integer getGestureVideoResId() {
        return R.drawable.gesture_tutorial_loop_home;
    }

    @Override
    TutorialController createController(TutorialType type) {
        return new HomeGestureTutorialController(this, type);
    }

    @Override
    Class<? extends TutorialController> getControllerClass() {
        return HomeGestureTutorialController.class;
    }
}
