/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static com.android.internal.R.styleable.WindowAnimation_windowEnterAnimation;
import static com.android.internal.R.styleable.WindowAnimation_windowExitAnimation;
import static com.android.wm.shell.transition.Transitions.ENABLE_SHELL_TRANSITIONS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.AnyRes;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.IntProperty;
import android.util.Log;
import android.util.Property;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.animation.Animation;

import com.android.internal.policy.TransitionAnimation;

/**
 * Controls the enter/exit a dialog.
 *
 * @param <T> The {@link DialogContainerSupplier} to use
 */
public class DialogAnimationController<T extends DialogContainerSupplier> {

    // The alpha of a background is a number between 0 (fully transparent) to 255 (fully opaque).
    // 204 is simply 255 * 0.8.
    static final int BACKGROUND_DIM_ALPHA = 204;

    // If shell transitions are enabled, startEnterAnimation will be called after all transitions
    // have finished, and therefore the start delay should be shorter.
    private static final int ENTER_ANIM_START_DELAY_MILLIS = ENABLE_SHELL_TRANSITIONS ? 300 : 500;

    private final TransitionAnimation mTransitionAnimation;
    private final String mPackageName;
    private final String mTag;
    @AnyRes
    private final int mAnimStyleResId;

    @Nullable
    private Animation mDialogAnimation;
    @Nullable
    private Animator mBackgroundDimAnimator;

    public DialogAnimationController(Context context, String tag) {
        mTransitionAnimation = new TransitionAnimation(context, /* debug= */ false, tag);
        mAnimStyleResId = (new ContextThemeWrapper(context,
                android.R.style.ThemeOverlay_Material_Dialog).getTheme()).obtainStyledAttributes(
                com.android.internal.R.styleable.Window).getResourceId(
                com.android.internal.R.styleable.Window_windowAnimationStyle, 0);
        mPackageName = context.getPackageName();
        mTag = tag;
    }

    /**
     * Starts both background dim fade-in animation and the dialog enter animation.
     */
    public void startEnterAnimation(@NonNull T layout, Runnable endCallback) {
        // Cancel any previous animation if it's still running.
        cancelAnimation();

        final View dialogContainer = layout.getDialogContainerView();
        mDialogAnimation = loadAnimation(WindowAnimation_windowEnterAnimation);
        if (mDialogAnimation == null) {
            endCallback.run();
            return;
        }
        mDialogAnimation.setAnimationListener(getAnimationListener(
                /* startCallback= */ () -> dialogContainer.setAlpha(1),
                /* endCallback= */ () -> {
                    mDialogAnimation = null;
                    endCallback.run();
                }));

        mBackgroundDimAnimator = getAlphaAnimator(layout.getBackgroundDimDrawable(),
                /* endAlpha= */ BACKGROUND_DIM_ALPHA,
                mDialogAnimation.getDuration());
        mBackgroundDimAnimator.addListener(getDimAnimatorListener());

        mDialogAnimation.setStartOffset(ENTER_ANIM_START_DELAY_MILLIS);
        mBackgroundDimAnimator.setStartDelay(ENTER_ANIM_START_DELAY_MILLIS);

        dialogContainer.startAnimation(mDialogAnimation);
        mBackgroundDimAnimator.start();
    }

    /**
     * Starts both the background dim fade-out animation and the dialog exit animation.
     */
    public void startExitAnimation(@NonNull T layout, Runnable endCallback) {
        // Cancel any previous animation if it's still running.
        cancelAnimation();

        final View dialogContainer = layout.getDialogContainerView();
        mDialogAnimation = loadAnimation(WindowAnimation_windowExitAnimation);
        if (mDialogAnimation == null) {
            endCallback.run();
            return;
        }
        mDialogAnimation.setAnimationListener(getAnimationListener(
                /* startCallback= */ () -> {},
                /* endCallback= */ () -> {
                    dialogContainer.setAlpha(0);
                    mDialogAnimation = null;
                    endCallback.run();
                }));

        mBackgroundDimAnimator = getAlphaAnimator(layout.getBackgroundDimDrawable(),
                /* endAlpha= */ 0, mDialogAnimation.getDuration());
        mBackgroundDimAnimator.addListener(getDimAnimatorListener());

        dialogContainer.startAnimation(mDialogAnimation);
        mBackgroundDimAnimator.start();
    }

    /**
     * Cancels all animations and resets the state of the controller.
     */
    public void cancelAnimation() {
        if (mDialogAnimation != null) {
            mDialogAnimation.cancel();
            mDialogAnimation = null;
        }
        if (mBackgroundDimAnimator != null) {
            mBackgroundDimAnimator.cancel();
            mBackgroundDimAnimator = null;
        }
    }

    private Animation loadAnimation(int animAttr) {
        Animation animation = mTransitionAnimation.loadAnimationAttr(mPackageName, mAnimStyleResId,
                animAttr, /* translucent= */ false);
        if (animation == null) {
            Log.e(mTag, "Failed to load animation " + animAttr);
        }
        return animation;
    }

    private Animation.AnimationListener getAnimationListener(Runnable startCallback,
            Runnable endCallback) {
        return new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                startCallback.run();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                endCallback.run();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        };
    }

    private AnimatorListenerAdapter getDimAnimatorListener() {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mBackgroundDimAnimator = null;
            }
        };
    }

    private static Animator getAlphaAnimator(
            Drawable drawable, int endAlpha, long duration) {
        Animator animator = ObjectAnimator.ofInt(drawable, DRAWABLE_ALPHA, endAlpha);
        animator.setDuration(duration);
        return animator;
    }

    private static final Property<Drawable, Integer> DRAWABLE_ALPHA = new IntProperty<Drawable>(
            "alpha") {
        @Override
        public void setValue(Drawable object, int value) {
            object.setAlpha(value);
        }

        @Override
        public Integer get(Drawable object) {
            return object.getAlpha();
        }
    };
}
