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
package com.android.quickstep.util;

import static com.android.launcher3.uioverrides.RecentsUiFactory.ROTATION_LANDSCAPE;
import static com.android.launcher3.uioverrides.RecentsUiFactory.ROTATION_SEASCAPE;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;

import android.content.Context;
import android.view.Surface;

import com.android.launcher3.graphics.RotationMode;
import com.android.launcher3.util.DefaultDisplay;
import com.android.quickstep.SysUINavigationMode;

/**
 * Utility class to check nav bar position
 */
public class NavBarPosition {

    private final SysUINavigationMode.Mode mMode;
    private final int mDisplayRotation;

    public NavBarPosition(Context context) {
        mMode = SysUINavigationMode.getMode(context);
        mDisplayRotation = DefaultDisplay.INSTANCE.get(context).getInfo().rotation;
    }

    public boolean isRightEdge() {
        return mMode != NO_BUTTON && mDisplayRotation == Surface.ROTATION_90;
    }

    public boolean isLeftEdge() {
        return mMode != NO_BUTTON && mDisplayRotation == Surface.ROTATION_270;
    }

    public RotationMode getRotationMode() {
        return isLeftEdge() ? ROTATION_SEASCAPE
                : (isRightEdge() ? ROTATION_LANDSCAPE : RotationMode.NORMAL);
    }
}
