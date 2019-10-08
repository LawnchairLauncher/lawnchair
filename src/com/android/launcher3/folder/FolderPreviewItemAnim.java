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
import android.animation.FloatArrayEvaluator;
import android.animation.ObjectAnimator;
import android.util.Property;

import java.util.Arrays;

/**
 * Animates a Folder preview item.
 */
class FolderPreviewItemAnim {

    private static final Property<FolderPreviewItemAnim, float[]> PARAMS =
            new Property<FolderPreviewItemAnim, float[]>(float[].class, "params") {
                @Override
                public float[] get(FolderPreviewItemAnim anim) {
                    sTempParamsArray[0] = anim.mParams.scale;
                    sTempParamsArray[1] = anim.mParams.transX;
                    sTempParamsArray[2] = anim.mParams.transY;
                    return sTempParamsArray;
                }

                @Override
                public void set(FolderPreviewItemAnim anim, float[] value) {
                    anim.setParams(value);
                }
            };

    private static PreviewItemDrawingParams sTmpParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private static final float[] sTempParamsArray = new float[3];

    private final ObjectAnimator mAnimator;
    private final PreviewItemManager mItemManager;
    private final PreviewItemDrawingParams mParams;

    public final float[] finalState;

    /**
     * @param params layout params to animate
     * @param index0 original index of the item to be animated
     * @param items0 original number of items in the preview
     * @param index1 new index of the item to be animated
     * @param items1 new number of items in the preview
     * @param duration duration in ms of the animation
     * @param onCompleteRunnable runnable to execute upon animation completion
     */
    FolderPreviewItemAnim(PreviewItemManager itemManager,
            PreviewItemDrawingParams params, int index0, int items0, int index1, int items1,
            int duration, final Runnable onCompleteRunnable) {
        mItemManager = itemManager;
        mParams = params;

        mItemManager.computePreviewItemDrawingParams(index1, items1, sTmpParams);
        finalState = new float[] {sTmpParams.scale, sTmpParams.transX, sTmpParams.transY};

        mItemManager.computePreviewItemDrawingParams(index0, items0, sTmpParams);
        float[] startState = new float[] {sTmpParams.scale, sTmpParams.transX, sTmpParams.transY};

        mAnimator = ObjectAnimator.ofObject(this, PARAMS, new FloatArrayEvaluator(),
                startState, finalState);
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
                params.anim = null;
            }
        });
        mAnimator.setDuration(duration);
    }

    private void setParams(float[] values) {
        mParams.scale = values[0];
        mParams.transX = values[1];
        mParams.transY = values[2];
        mItemManager.onParamsChanged();
    }

    public void start() {
        mAnimator.start();
    }

    public void cancel() {
        mAnimator.cancel();
    }

    public boolean hasEqualFinalState(FolderPreviewItemAnim anim) {
        return Arrays.equals(finalState, anim.finalState);

    }
}
