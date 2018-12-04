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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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

    public static final FloatProperty<TaskViewDrawable> PROGRESS =
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

    /**
     * The progress at which we play the atomic icon scale animation.
     */
    private static final float ICON_SCALE_THRESHOLD = 0.95f;

    private final RecentsView mParent;
    private final View mIconView;
    private final int[] mIconPos;

    private final TaskThumbnailView mThumbnailView;

    private final ClipAnimationHelper mClipAnimationHelper;

    private float mProgress = 1;
    private boolean mPassedIconScaleThreshold;
    private ValueAnimator mIconScaleAnimator;
    private float mIconScale;

    public TaskViewDrawable(TaskView tv, RecentsView parent) {
        mParent = parent;
        mIconView = tv.getIconView();
        mIconPos = new int[2];
        mIconScale = mIconView.getScaleX();
        Utilities.getDescendantCoordRelativeToAncestor(mIconView, parent, mIconPos, true);

        mThumbnailView = tv.getThumbnail();
        mClipAnimationHelper = new ClipAnimationHelper();
        mClipAnimationHelper.fromTaskThumbnailView(mThumbnailView, parent);
    }

    public void setProgress(float progress) {
        mProgress = progress;
        mParent.invalidate();
        boolean passedIconScaleThreshold = progress <= ICON_SCALE_THRESHOLD;
        if (mPassedIconScaleThreshold != passedIconScaleThreshold) {
            mPassedIconScaleThreshold = passedIconScaleThreshold;
            animateIconScale(mPassedIconScaleThreshold ? 0 : 1);
        }
    }

    private void animateIconScale(float toScale) {
        if (mIconScaleAnimator != null) {
            mIconScaleAnimator.cancel();
        }
        mIconScaleAnimator = ValueAnimator.ofFloat(mIconScale, toScale);
        mIconScaleAnimator.addUpdateListener(valueAnimator -> {
            mIconScale = (float) valueAnimator.getAnimatedValue();
            if (mProgress > ICON_SCALE_THRESHOLD) {
                // Speed up the icon scale to ensure it is 1 when progress is 1.
                float iconProgress = (mProgress - ICON_SCALE_THRESHOLD) / (1 - ICON_SCALE_THRESHOLD);
                if (iconProgress > mIconScale) {
                    mIconScale = iconProgress;
                }
            }
            invalidateSelf();
        });
        mIconScaleAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIconScaleAnimator = null;
            }
        });
        mIconScaleAnimator.setDuration(TaskView.SCALE_ICON_DURATION);
        mIconScaleAnimator.start();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(mParent.getScrollX(), mParent.getScrollY());
        mClipAnimationHelper.drawForProgress(mThumbnailView, canvas, mProgress);
        canvas.restore();

        canvas.save();
        canvas.translate(mIconPos[0], mIconPos[1]);
        canvas.scale(mIconScale, mIconScale, mIconView.getWidth() / 2, mIconView.getHeight() / 2);
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
