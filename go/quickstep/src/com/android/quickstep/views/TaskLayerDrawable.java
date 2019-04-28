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

package com.android.quickstep.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;

import com.android.launcher3.R;

/**
 * A layer drawable for task content that transitions between two drawables by crossfading. Similar
 * to {@link android.graphics.drawable.TransitionDrawable} but allows callers to control transition
 * progress and provides a default, empty drawable.
 */
public final class TaskLayerDrawable extends LayerDrawable {
    private final Drawable mEmptyDrawable;
    private float mProgress;

    public TaskLayerDrawable(Context context) {
        super(new Drawable[0]);

        // Use empty drawable for both layers initially.
        mEmptyDrawable = context.getResources().getDrawable(
                R.drawable.empty_content_box, context.getTheme());
        addLayer(mEmptyDrawable);
        addLayer(mEmptyDrawable);
        setTransitionProgress(1.0f);
    }

    /**
     * Immediately set the front-most drawable layer.
     *
     * @param drawable drawable to set
     */
    public void setCurrentDrawable(@NonNull Drawable drawable) {
        setDrawable(0, drawable);
        applyTransitionProgress(mProgress);
    }

    /**
     * Immediately reset the drawable to showing the empty drawable.
     */
    public void resetDrawable() {
        setCurrentDrawable(mEmptyDrawable);
    }

    /**
     * Prepare to start animating the transition by pushing the current drawable to the back and
     * setting a new drawable to the front layer and making it invisible.
     *
     * @param endDrawable drawable to animate to
     */
    public void startNewTransition(@NonNull Drawable endDrawable) {
        Drawable oldDrawable = getDrawable(0);
        setDrawable(1, oldDrawable);
        setDrawable(0, endDrawable);
        setTransitionProgress(0.0f);
    }

    /**
     * Set the progress of the transition animation to crossfade the two drawables.
     *
     * @param progress current transition progress between 0 (front view invisible) and 1
     *                 (front view visible)
     */
    public void setTransitionProgress(float progress) {
        if (progress > 1 || progress < 0) {
            throw new IllegalArgumentException("Transition progress should be between 0 and 1");
        }
        mProgress = progress;
        applyTransitionProgress(progress);
    }

    private void applyTransitionProgress(float progress) {
        int drawableAlpha = (int) (progress * 255);
        getDrawable(0).setAlpha(drawableAlpha);
        if (getDrawable(0) != getDrawable(1)) {
            // Only do this if it's a different drawable so that it fades out.
            // Otherwise, we'd just be overwriting the front drawable's alpha.
            getDrawable(1).setAlpha(255 - drawableAlpha);
        }
        invalidateSelf();
    }
}
