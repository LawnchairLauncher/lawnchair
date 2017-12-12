/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import com.android.launcher3.LauncherAnimUtils;

/**
 * Animates a Folder preview item.
 */
class FolderPreviewItemAnim {

    private static PreviewItemDrawingParams sTmpParams = new PreviewItemDrawingParams(0, 0, 0, 0);

    private ValueAnimator mValueAnimator;

    float finalScale;
    float finalTransX;
    float finalTransY;

    /**
     * @param params layout params to animate
     * @param index0 original index of the item to be animated
     * @param items0 original number of items in the preview
     * @param index1 new index of the item to be animated
     * @param items1 new number of items in the preview
     * @param duration duration in ms of the animation
     * @param onCompleteRunnable runnable to execute upon animation completion
     */
    FolderPreviewItemAnim(final PreviewItemManager previewItemManager,
            final PreviewItemDrawingParams params, int index0, int items0, int index1, int items1,
            int duration, final Runnable onCompleteRunnable) {
        previewItemManager.computePreviewItemDrawingParams(index1, items1, sTmpParams);

        finalScale = sTmpParams.scale;
        finalTransX = sTmpParams.transX;
        finalTransY = sTmpParams.transY;

        previewItemManager.computePreviewItemDrawingParams(index0, items0, sTmpParams);

        final float scale0 = sTmpParams.scale;
        final float transX0 = sTmpParams.transX;
        final float transY0 = sTmpParams.transY;

        mValueAnimator = LauncherAnimUtils.ofFloat(0f, 1.0f);
        mValueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){
            public void onAnimationUpdate(ValueAnimator animation) {
                float progress = animation.getAnimatedFraction();

                params.transX = transX0 + progress * (finalTransX - transX0);
                params.transY = transY0 + progress * (finalTransY - transY0);
                params.scale = scale0 + progress * (finalScale - scale0);
                previewItemManager.onParamsChanged();
            }
        });
        mValueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
                params.anim = null;
            }
        });
        mValueAnimator.setDuration(duration);
    }

    public void start() {
        mValueAnimator.start();
    }

    public void cancel() {
        mValueAnimator.cancel();
    }

    public boolean hasEqualFinalState(FolderPreviewItemAnim anim) {
        return finalTransY == anim.finalTransY && finalTransX == anim.finalTransX &&
                finalScale == anim.finalScale;

    }
}
