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

package com.android.quickstep.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.graphics.Rect;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;

import com.android.systemui.shared.pip.PipSurfaceTransactionHelper;

/**
 * An {@link Animator} that animates an Activity to PiP (picture-in-picture) window when
 * swiping up (in gesture navigation mode). Note that this class is derived from
 * {@link com.android.wm.shell.pip.PipAnimationController.PipTransitionAnimator}.
 *
 * TODO: consider sharing this class including the animator and leash operations between
 * Launcher and SysUI. Also, there should be one source of truth for the corner radius of the
 * PiP window, which would ideally be on SysUI side as well.
 */
public class SwipePipToHomeAnimator extends ValueAnimator implements
        ValueAnimator.AnimatorUpdateListener {
    private final int mTaskId;
    private final ComponentName mComponentName;
    private final SurfaceControl mLeash;
    private final Rect mStartBounds = new Rect();
    private final Rect mDestinationBounds = new Rect();
    private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    /** for calculating the transform in {@link #onAnimationUpdate(ValueAnimator)} */
    private final RectEvaluator mRectEvaluator = new RectEvaluator(new Rect());
    private final RectEvaluator mInsetsEvaluator = new RectEvaluator(new Rect());
    private final Rect mSourceHintRectInsets = new Rect();
    private final Rect mSourceInsets = new Rect();

    /**
     * Flag to avoid the double-end problem since the leash would have been released
     * after the first end call and any further operations upon it would lead to NPE.
     */
    private boolean mHasAnimationEnded;

    public SwipePipToHomeAnimator(int taskId,
            @NonNull ComponentName componentName,
            @NonNull SurfaceControl leash,
            @NonNull Rect sourceRectHint,
            @NonNull Rect startBounds,
            @NonNull Rect destinationBounds) {
        mTaskId = taskId;
        mComponentName = componentName;
        mLeash = leash;
        mStartBounds.set(startBounds);
        mDestinationBounds.set(destinationBounds);
        mSurfaceTransactionHelper = new PipSurfaceTransactionHelper();

        mSourceHintRectInsets.set(sourceRectHint.left - startBounds.left,
                sourceRectHint.top - startBounds.top,
                startBounds.right - sourceRectHint.right,
                startBounds.bottom - sourceRectHint.bottom);

        addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                SwipePipToHomeAnimator.this.onAnimationEnd();
            }
        });
        addUpdateListener(this);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animator) {
        if (mHasAnimationEnded) return;

        final float fraction = animator.getAnimatedFraction();
        final Rect bounds = mRectEvaluator.evaluate(fraction, mStartBounds, mDestinationBounds);
        final Rect insets = mInsetsEvaluator.evaluate(fraction, mSourceInsets,
                mSourceHintRectInsets);
        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mSurfaceTransactionHelper.scaleAndCrop(tx, mLeash, mStartBounds, bounds, insets);
        mSurfaceTransactionHelper.resetCornerRadius(tx, mLeash);
        tx.apply();
    }

    public int getTaskId() {
        return mTaskId;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public Rect getDestinationBounds() {
        return mDestinationBounds;
    }

    private void onAnimationEnd() {
        if (mHasAnimationEnded) return;

        final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        mSurfaceTransactionHelper.reset(tx, mLeash, mDestinationBounds);
        tx.apply();
        mHasAnimationEnded = true;
    }
}
