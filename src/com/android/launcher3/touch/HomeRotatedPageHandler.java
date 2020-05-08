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

package com.android.launcher3.touch;

import android.graphics.RectF;
import android.view.Surface;
import android.widget.LinearLayout;

public class HomeRotatedPageHandler extends PortraitPagedViewHandler {
    @Override
    public void offsetTaskRect(RectF rect, float value, int displayRotation, int launcherRotation) {
        if (launcherRotation == Surface.ROTATION_0) {
            super.offsetTaskRect(rect, value, displayRotation, launcherRotation);
        } else if (launcherRotation == Surface.ROTATION_90) {
            if (displayRotation == Surface.ROTATION_0) {
                rect.offset(0, value);
            } else if (displayRotation == Surface.ROTATION_90) {
                rect.offset(value, 0);
            } else if (displayRotation == Surface.ROTATION_180) {
                rect.offset(-value, 0);
            } else {
                rect.offset(-value, 0);
            }
        } else if (launcherRotation == Surface.ROTATION_270) {
            if (displayRotation == Surface.ROTATION_0) {
                rect.offset(0, -value);
            } else if (displayRotation == Surface.ROTATION_90) {
                rect.offset(value, 0);
            } else if (displayRotation == Surface.ROTATION_180) {
                rect.offset(0, -value);
            } else {
                rect.offset(value, 0);
            }
        } // TODO (b/149609488) handle 180 case as well
    }

    @Override
    public int getTaskMenuLayoutOrientation(LinearLayout taskMenuLayout) {
        return taskMenuLayout.getOrientation();
    }
}
