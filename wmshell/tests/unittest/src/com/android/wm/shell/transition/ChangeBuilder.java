/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.transition;

import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_UNSPECIFIED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.Mockito.mock;

import android.app.ActivityManager.RunningTaskInfo;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;

public class ChangeBuilder {
    final TransitionInfo.Change mChange;

    ChangeBuilder(@WindowManager.TransitionType int mode) {
        mChange = new TransitionInfo.Change(null /* token */, createMockSurface(true));
        mChange.setMode(mode);
    }

    ChangeBuilder setFlags(@TransitionInfo.ChangeFlags int flags) {
        mChange.setFlags(flags);
        return this;
    }

    ChangeBuilder setTask(RunningTaskInfo taskInfo) {
        mChange.setTaskInfo(taskInfo);
        return this;
    }

    ChangeBuilder setRotate(int anim) {
        return setRotate(Surface.ROTATION_90, anim);
    }

    ChangeBuilder setRotate() {
        return setRotate(ROTATION_ANIMATION_UNSPECIFIED);
    }

    ChangeBuilder setRotate(@Surface.Rotation int target, int anim) {
        mChange.setRotation(Surface.ROTATION_0, target);
        mChange.setRotationAnimation(anim);
        return this;
    }

    TransitionInfo.Change build() {
        return mChange;
    }

    private static SurfaceControl createMockSurface(boolean valid) {
        SurfaceControl sc = mock(SurfaceControl.class);
        doReturn(valid).when(sc).isValid();
        return sc;
    }
}
