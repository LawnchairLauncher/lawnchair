/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.animation.TimeInterpolator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.view.View;

import com.android.launcher3.Utilities;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;

public class TaskViewDrawable extends Drawable {

    public static FloatProperty<TaskViewDrawable> PROGRESS =
            new FloatProperty<TaskViewDrawable>("progress") {
                @Override
                public void setValue(TaskViewDrawable taskViewDrawable, float v) {
                    taskViewDrawable.setProgress(v);
                }

                @Override
                public Float get(TaskViewDrawable taskViewDrawable) {
                    return taskViewDrawable.mProgress;
                }
            };

    private static final TimeInterpolator ICON_SIZE_INTERPOLATOR =
            (t) -> (Math.max(t, 0.3f) - 0.3f) / 0.7f;

    private final RecentsView mParent;
    private final View mIconView;
    private final int[] mIconPos;

    private final TaskThumbnailView mThumbnailView;

    private final ClipAnimationHelper mClipAnimationHelper;

    private float mProgress = 1;

    public TaskViewDrawable(TaskView tv, RecentsView parent) {
        mParent = parent;
        mIconView = tv.getIconView();
        mIconPos = new int[2];
        Utilities.getDescendantCoordRelativeToAncestor(mIconView, parent, mIconPos, true);

        mThumbnailView = tv.getThumbnail();
        mClipAnimationHelper = new ClipAnimationHelper();
        mClipAnimationHelper.fromTaskThumbnailView(mThumbnailView, parent);
    }

    public void setProgress(float progress) {
        mProgress = progress;
        mParent.invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(mParent.getScrollX(), mParent.getScrollY());
        mClipAnimationHelper.drawForProgress(mThumbnailView, canvas, mProgress);
        canvas.restore();

        canvas.save();
        canvas.translate(mIconPos[0], mIconPos[1]);
        float scale = ICON_SIZE_INTERPOLATOR.getInterpolation(mProgress);
        canvas.scale(scale, scale, mIconView.getWidth() / 2, mIconView.getHeight() / 2);
        mIconView.draw(canvas);
        canvas.restore();
    }

    public ClipAnimationHelper getClipAnimationHelper() {
        return mClipAnimationHelper;
    }

    @Override
    public void setAlpha(int i) { }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
