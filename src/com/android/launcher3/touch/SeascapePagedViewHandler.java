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

package com.android.launcher3.touch;

import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.launcher3.Utilities;

public class SeascapePagedViewHandler extends LandscapePagedViewHandler {

    @Override
    public int getTaskDismissDirectionFactor() {
        return -1;
    }

    @Override
    public boolean getRecentsRtlSetting(Resources resources) {
        return Utilities.isRtl(resources);
    }

    @Override
    public void offsetTaskRect(RectF rect, float value, int delta) {
        if (delta == 0) {
            rect.offset(-value, 0);
        } else if (delta == 1) {
            rect.offset(0, value);
        } else if (delta == 2) {
            rect.offset(-value, 0);
        } else {
            rect.offset(0, -value);
        }
    }

    @Override
    public void mapRectFromNormalOrientation(Rect src, int screenWidth, int screenHeight) {
        Matrix m = new Matrix();
        m.setRotate(90);
        m.postTranslate(screenHeight, 0);
        RectF newTarget = new RectF();
        RectF oldTarget = new RectF(src);
        m.mapRect(newTarget, oldTarget);
        src.set((int)newTarget.left, (int)newTarget.top, (int)newTarget.right, (int)newTarget.bottom);
    }

    @Override
    public float getDegreesRotated() {
        return 270;
    }

    @Override
    public boolean isGoingUp(float displacement) {
        return displacement < 0;
    }
}
