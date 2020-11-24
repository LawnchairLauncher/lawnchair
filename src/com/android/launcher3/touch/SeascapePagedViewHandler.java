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
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.Surface;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;

public class SeascapePagedViewHandler extends LandscapePagedViewHandler {

    @Override
    public int getSecondaryTranslationDirectionFactor() {
        return -1;
    }

    @Override
    public int getTaskDragDisplacementFactor(boolean isRtl) {
        return isRtl ? -1 : 1;
    }

    @Override
    public boolean getRecentsRtlSetting(Resources resources) {
        return Utilities.isRtl(resources);
    }

    @Override
    public float getDegreesRotated() {
        return 270;
    }

    @Override
    public int getRotation() {
        return Surface.ROTATION_270;
    }

    @Override
    public boolean isGoingUp(float displacement, boolean isRtl) {
        return isRtl ? displacement > 0 : displacement < 0;
    }

    @Override
    public void adjustFloatingIconStartVelocity(PointF velocity) {
        float oldX = velocity.x;
        float oldY = velocity.y;
        velocity.set(oldY, -oldX);
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView) {
        return x;
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView) {
        return y + thumbnailView.getMeasuredHeight();
    }

    @Override
    public void setPrimaryAndResetSecondaryTranslate(View view, float translation) {
        view.setTranslationX(0);
        view.setTranslationY(translation);
    }

    @Override
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return dp.widthPx - rect.right;
    }
}
