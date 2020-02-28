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

package com.android.launcher3.uioverrides;


import android.util.IntProperty;
import android.view.View;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.anim.AnimatorSetBuilder;

/**
 * Controls the blur, for the Launcher surface only.
 */
public class BackgroundBlurController implements LauncherStateManager.StateHandler {

    public static final IntProperty<BackgroundBlurController> BACKGROUND_BLUR =
            new IntProperty<BackgroundBlurController>("backgroundBlur") {
                @Override
                public void setValue(BackgroundBlurController blurController, int blurRadius) {}

                @Override
                public Integer get(BackgroundBlurController blurController) {
                    return 0;
                }
            };

    public BackgroundBlurController(Launcher l) {}

    public int getFolderBackgroundBlurAdjustment() {
        return 0;
    }

    public void setSurfaceToLauncher(View v) {}

    @Override
    public void setState(LauncherState toState) {}

    @Override
    public void setStateWithAnimation(LauncherState toState, AnimatorSetBuilder builder,
            LauncherStateManager.AnimationConfig config) {}
}
