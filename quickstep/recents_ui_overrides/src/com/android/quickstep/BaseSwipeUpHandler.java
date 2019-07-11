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
package com.android.quickstep;

import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.createPredefined;

import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.quickstep.TouchInteractionService.BACKGROUND_EXECUTOR;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.animation.Interpolator;

import com.android.launcher3.Utilities;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.ClipAnimationHelper.TransformParams;

/**
 * Base class for swipe up handler with some utility methods
 */
@TargetApi(Build.VERSION_CODES.Q)
public abstract class BaseSwipeUpHandler {

    // Start resisting when swiping past this factor of mTransitionDragLength.
    private static final float DRAG_LENGTH_FACTOR_START_PULLBACK = 1.4f;
    // This is how far down we can scale down, where 0f is full screen and 1f is recents.
    private static final float DRAG_LENGTH_FACTOR_MAX_PULLBACK = 1.8f;
    private static final Interpolator PULLBACK_INTERPOLATOR = DEACCEL;

    // The distance needed to drag to reach the task size in recents.
    protected int mTransitionDragLength;
    // How much further we can drag past recents, as a factor of mTransitionDragLength.
    protected float mDragLengthFactor = 1;

    protected final Context mContext;

    protected final ClipAnimationHelper mClipAnimationHelper;
    protected final TransformParams mTransformParams = new TransformParams();

    private final Vibrator mVibrator;

    protected BaseSwipeUpHandler(Context context) {
        mContext = context;
        mClipAnimationHelper = new ClipAnimationHelper(context);

        mVibrator = context.getSystemService(Vibrator.class);
    }

    public void performHapticFeedback() {
        if (!mVibrator.hasVibrator()) {
            return;
        }
        if (Settings.System.getInt(
                mContext.getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) == 0) {
            return;
        }

        VibrationEffect effect = createPredefined(EFFECT_CLICK);
        if (effect == null) {
            return;
        }
        BACKGROUND_EXECUTOR.execute(() -> mVibrator.vibrate(effect));
    }

    protected float getShiftForDisplacement(float displacement) {
        // We are moving in the negative x/y direction
        displacement = -displacement;
        if (displacement > mTransitionDragLength * mDragLengthFactor && mTransitionDragLength > 0) {
            return mDragLengthFactor;
        } else {
            float translation = Math.max(displacement, 0);
            float shift = mTransitionDragLength == 0 ? 0 : translation / mTransitionDragLength;
            if (shift > DRAG_LENGTH_FACTOR_START_PULLBACK) {
                float pullbackProgress = Utilities.getProgress(shift,
                        DRAG_LENGTH_FACTOR_START_PULLBACK, mDragLengthFactor);
                pullbackProgress = PULLBACK_INTERPOLATOR.getInterpolation(pullbackProgress);
                shift = DRAG_LENGTH_FACTOR_START_PULLBACK + pullbackProgress
                        * (DRAG_LENGTH_FACTOR_MAX_PULLBACK - DRAG_LENGTH_FACTOR_START_PULLBACK);
            }
            return shift;
        }
    }
}
