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
package com.android.wm.shell.startingsurface;

import static android.view.View.GONE;

import static com.android.internal.jank.InteractionJankMonitor.CUJ_SPLASHSCREEN_EXIT_ANIM;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.util.Slog;
import android.view.SurfaceControl;
import android.view.View;
import android.window.SplashScreenView;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.R;
import com.android.wm.shell.common.TransactionPool;

/**
 * Default animation for exiting the splash screen window.
 * @hide
 */
public class SplashScreenExitAnimation implements Animator.AnimatorListener {
    private static final boolean DEBUG_EXIT_ANIMATION = false;
    private static final String TAG = StartingWindowController.TAG;

    private final SurfaceControl mFirstWindowSurface;
    private final Rect mFirstWindowFrame = new Rect();
    private final SplashScreenView mSplashScreenView;
    private final int mMainWindowShiftLength;
    private final int mIconFadeOutDuration;
    private final int mAppRevealDelay;
    private final int mAppRevealDuration;
    @SplashScreenExitAnimationUtils.ExitAnimationType
    private final int mAnimationType;
    private final int mAnimationDuration;
    private final float mIconStartAlpha;
    private final float mBrandingStartAlpha;
    private final TransactionPool mTransactionPool;
    // TODO(b/261167708): Clean enter animation code after moving Letterbox code to Shell
    private final float mRoundedCornerRadius;

    private Runnable mFinishCallback;

    SplashScreenExitAnimation(Context context, SplashScreenView view, SurfaceControl leash,
            Rect frame, int mainWindowShiftLength, TransactionPool pool, Runnable handleFinish,
            float roundedCornerRadius) {
        mSplashScreenView = view;
        mFirstWindowSurface = leash;
        mRoundedCornerRadius = roundedCornerRadius;
        if (frame != null) {
            mFirstWindowFrame.set(frame);
        }

        View iconView = view.getIconView();

        // If the icon and the background are invisible, don't animate it
        if (iconView == null || iconView.getLayoutParams().width == 0
                || iconView.getLayoutParams().height == 0) {
            mIconFadeOutDuration = 0;
            mIconStartAlpha = 0;
            mBrandingStartAlpha = 0;
            mAppRevealDelay = 0;
        } else {
            iconView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            // The branding view could only exists when the icon is present.
            final View brandingView = view.getBrandingView();
            if (brandingView != null) {
                mBrandingStartAlpha = brandingView.getAlpha();
            } else {
                mBrandingStartAlpha = 0;
            }
            mIconFadeOutDuration = context.getResources().getInteger(
                    R.integer.starting_window_app_reveal_icon_fade_out_duration);
            mAppRevealDelay = context.getResources().getInteger(
                    R.integer.starting_window_app_reveal_anim_delay);
            mIconStartAlpha = iconView.getAlpha();
        }
        mAppRevealDuration = context.getResources().getInteger(
                R.integer.starting_window_app_reveal_anim_duration);
        mAnimationType = context.getResources().getInteger(
                R.integer.starting_window_exit_animation_type);
        mAnimationDuration = Math.max(mIconFadeOutDuration, mAppRevealDelay + mAppRevealDuration);
        mMainWindowShiftLength = mainWindowShiftLength;
        mFinishCallback = handleFinish;
        mTransactionPool = pool;
    }

    void startAnimations() {
        SplashScreenExitAnimationUtils.startAnimations(mAnimationType, mSplashScreenView,
                mFirstWindowSurface, mMainWindowShiftLength, mTransactionPool, mFirstWindowFrame,
                mAnimationDuration, mIconFadeOutDuration, mIconStartAlpha, mBrandingStartAlpha,
                mAppRevealDelay, mAppRevealDuration, this, mRoundedCornerRadius);
    }

    private void reset() {
        if (DEBUG_EXIT_ANIMATION) {
            Slog.v(TAG, "vanish animation finished");
        }

        if (mSplashScreenView.isAttachedToWindow()) {
            mSplashScreenView.setVisibility(GONE);
            if (mFinishCallback != null) {
                mFinishCallback.run();
                mFinishCallback = null;
            }
        }
    }

    @Override
    public void onAnimationStart(Animator animation) {
        InteractionJankMonitor.getInstance().begin(mSplashScreenView, CUJ_SPLASHSCREEN_EXIT_ANIM);
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        reset();
        InteractionJankMonitor.getInstance().end(CUJ_SPLASHSCREEN_EXIT_ANIM);
    }

    @Override
    public void onAnimationCancel(Animator animation) {
        reset();
        InteractionJankMonitor.getInstance().cancel(CUJ_SPLASHSCREEN_EXIT_ANIM);
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
        // ignore
    }
}
