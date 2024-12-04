/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.pip2.animation;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.content.Context;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.R;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Animator that handles the alpha animation for entering PIP
 */
public class PipAlphaAnimator extends ValueAnimator implements ValueAnimator.AnimatorUpdateListener,
        ValueAnimator.AnimatorListener {
    @IntDef(prefix = {"FADE_"}, value = {
            FADE_IN,
            FADE_OUT
    })

    @Retention(RetentionPolicy.SOURCE)
    public @interface Fade {}

    public static final int FADE_IN = 0;
    public static final int FADE_OUT = 1;

    private final int mEnterAnimationDuration;
    private final SurfaceControl mLeash;
    private final SurfaceControl.Transaction mStartTransaction;

    // optional callbacks for tracking animation start and end
    @Nullable private Runnable mAnimationStartCallback;
    @Nullable private Runnable mAnimationEndCallback;

    private final PipSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mSurfaceControlTransactionFactory;

    public PipAlphaAnimator(Context context,
            SurfaceControl leash,
            SurfaceControl.Transaction tx,
            @Fade int direction) {
        mLeash = leash;
        mStartTransaction = tx;
        if (direction == FADE_IN) {
            setFloatValues(0f, 1f);
        } else { // direction == FADE_OUT
            setFloatValues(1f, 0f);
        }
        mSurfaceControlTransactionFactory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();
        mEnterAnimationDuration = context.getResources()
                .getInteger(R.integer.config_pipEnterAnimationDuration);
        setDuration(mEnterAnimationDuration);
        addListener(this);
        addUpdateListener(this);
    }

    public void setAnimationStartCallback(@NonNull Runnable runnable) {
        mAnimationStartCallback = runnable;
    }

    public void setAnimationEndCallback(@NonNull Runnable runnable) {
        mAnimationEndCallback = runnable;
    }

    @Override
    public void onAnimationStart(@NonNull Animator animation) {
        if (mAnimationStartCallback != null) {
            mAnimationStartCallback.run();
        }
        if (mStartTransaction != null) {
            mStartTransaction.apply();
        }
    }

    @Override
    public void onAnimationUpdate(@NonNull ValueAnimator animation) {
        final float alpha = (Float) animation.getAnimatedValue();
        mSurfaceControlTransactionFactory.getTransaction().setAlpha(mLeash, alpha).apply();
    }

    @Override
    public void onAnimationEnd(@NonNull Animator animation) {
        if (mAnimationEndCallback != null) {
            mAnimationEndCallback.run();
        }
    }

    @Override
    public void onAnimationCancel(@NonNull Animator animation) {}

    @Override
    public void onAnimationRepeat(@NonNull Animator animation) {}
}
