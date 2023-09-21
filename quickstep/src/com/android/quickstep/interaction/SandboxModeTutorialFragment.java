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

import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.logging.StatsLogManager;
import com.android.quickstep.interaction.TutorialController.TutorialType;

/** Shows the general navigation gesture sandbox environment. */
public class SandboxModeTutorialFragment extends TutorialFragment {

    public SandboxModeTutorialFragment(boolean fromTutorialMenu) {
        super(fromTutorialMenu);
    }

    @Override
    TutorialController createController(TutorialType type) {
        return new SandboxModeTutorialController(this, type);
    }

    @Override
    Class<? extends TutorialController> getControllerClass() {
        return SandboxModeTutorialController.class;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN && mTutorialController != null) {
            mTutorialController.setRippleHotspot(motionEvent.getX(), motionEvent.getY());
        }
        return super.onTouch(view, motionEvent);
    }

    @Override
    void logTutorialStepShown(@NonNull StatsLogManager statsLogManager) {
        // No-Op: tutorial step not currently shown to users
    }

    @Override
    void logTutorialStepCompleted(@NonNull StatsLogManager statsLogManager) {
        // No-Op: tutorial step not currently shown to users
    }
}
