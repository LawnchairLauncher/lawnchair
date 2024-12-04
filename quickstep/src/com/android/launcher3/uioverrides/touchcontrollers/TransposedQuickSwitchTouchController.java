/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.uioverrides.touchcontrollers;

import com.android.launcher3.LauncherState;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.uioverrides.QuickstepLauncher;

public class TransposedQuickSwitchTouchController extends QuickSwitchTouchController {

    public TransposedQuickSwitchTouchController(QuickstepLauncher launcher) {
        super(launcher, SingleAxisSwipeDetector.VERTICAL);
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        return super.getTargetState(fromState,
                isDragTowardPositive ^ mLauncher.getDeviceProfile().isSeascape());
    }

    @Override
    protected float initCurrentAnimation() {
        float multiplier = super.initCurrentAnimation();
        return mLauncher.getDeviceProfile().isSeascape() ? multiplier : -multiplier;
    }

    @Override
    protected float getShiftRange() {
        return mLauncher.getDeviceProfile().heightPx / 2f;
    }
}
