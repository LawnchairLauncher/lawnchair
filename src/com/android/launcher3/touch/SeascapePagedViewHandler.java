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

import static com.android.launcher3.touch.SingleAxisSwipeDetector.HORIZONTAL;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_TYPE_MAIN;

import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.Surface;
import android.view.View;
import android.widget.LinearLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Collections;
import java.util.List;

public class SeascapePagedViewHandler extends LandscapePagedViewHandler {

    @Override
    public int getSecondaryTranslationDirectionFactor() {
        return -1;
    }

    @Override
    public int getSplitTranslationDirectionFactor(int stagePosition) {
        if (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
            return -1;
        } else {
            return 1;
        }
    }

    @Override
    public int getSplitAnimationTranslation(int translationOffset, DeviceProfile dp) {
        return translationOffset;
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
    public void adjustFloatingIconStartVelocity(PointF velocity) {
        float oldX = velocity.x;
        float oldY = velocity.y;
        velocity.set(oldY, -oldX);
    }

    @Override
    public float getTaskMenuX(float x, View thumbnailView, int overScroll) {
        return x;
    }

    @Override
    public float getTaskMenuY(float y, View thumbnailView, int overScroll) {
        return y + thumbnailView.getMeasuredHeight() + overScroll;
    }

    @Override
    public void setTaskMenuAroundTaskView(LinearLayout taskView, float margin) {
        BaseDragLayer.LayoutParams lp = (BaseDragLayer.LayoutParams) taskView.getLayoutParams();
        lp.bottomMargin += margin;
    }

    @Override
    public PointF getAdditionalInsetForTaskMenu(float margin) {
        return new PointF(-margin, margin);
    }

    @Override
    public int getDistanceToBottomOfRect(DeviceProfile dp, Rect rect) {
        return dp.widthPx - rect.right;
    }

    @Override
    public List<SplitPositionOption> getSplitPositionOptions(DeviceProfile dp) {
        // Add "right" option which is actually the top
        return Collections.singletonList(new SplitPositionOption(
                R.drawable.ic_split_screen, R.string.split_screen_position_right,
                STAGE_POSITION_TOP_OR_LEFT, STAGE_TYPE_MAIN));
    }

    /* ---------- The following are only used by TaskViewTouchHandler. ---------- */

    @Override
    public SingleAxisSwipeDetector.Direction getUpDownSwipeDirection() {
        return HORIZONTAL;
    }

    @Override
    public int getUpDirection(boolean isRtl) {
        return isRtl ? SingleAxisSwipeDetector.DIRECTION_POSITIVE
                : SingleAxisSwipeDetector.DIRECTION_NEGATIVE;
    }

    @Override
    public boolean isGoingUp(float displacement, boolean isRtl) {
        return isRtl ? displacement > 0 : displacement < 0;
    }

    @Override
    public int getTaskDragDisplacementFactor(boolean isRtl) {
        return isRtl ? -1 : 1;
    }

    /* -------------------- */
}
