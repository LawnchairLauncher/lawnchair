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
package com.android.quickstep.util;

import static com.android.launcher3.LauncherAppTransitionManagerImpl.INDEX_SHELF_ANIM;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.animation.Interpolator;

import com.android.launcher3.Launcher;
import com.android.launcher3.uioverrides.states.OverviewState;

/**
 * Animates the shelf between states HIDE, PEEK, and OVERVIEW.
 */

public class ShelfPeekAnim {

    public static final Interpolator INTERPOLATOR = OVERSHOOT_1_2;
    public static final long DURATION = 240;

    private final Launcher mLauncher;

    private ShelfAnimState mShelfState;
    private boolean mIsPeeking;

    public ShelfPeekAnim(Launcher launcher) {
        mLauncher = launcher;
    }

    /**
     * Animates to the given state, canceling the previous animation if it was still running.
     */
    public void setShelfState(ShelfAnimState shelfState, Interpolator interpolator, long duration) {
        if (mShelfState == shelfState) {
            return;
        }
        mLauncher.getStateManager().cancelStateElementAnimation(INDEX_SHELF_ANIM);
        mShelfState = shelfState;
        mIsPeeking = mShelfState == ShelfAnimState.PEEK || mShelfState == ShelfAnimState.HIDE;
        if (mShelfState == ShelfAnimState.CANCEL) {
            return;
        }
        float shelfHiddenProgress = BACKGROUND_APP.getVerticalProgress(mLauncher);
        float shelfOverviewProgress = OVERVIEW.getVerticalProgress(mLauncher);
        // Peek based on default overview progress so we can see hotseat if we're showing
        // that instead of predictions in overview.
        float defaultOverviewProgress = OverviewState.getDefaultVerticalProgress(mLauncher);
        float shelfPeekingProgress = shelfHiddenProgress
                - (shelfHiddenProgress - defaultOverviewProgress) * 0.25f;
        float toProgress = mShelfState == ShelfAnimState.HIDE
                ? shelfHiddenProgress
                : mShelfState == ShelfAnimState.PEEK
                        ? shelfPeekingProgress
                        : shelfOverviewProgress;
        Animator shelfAnim = mLauncher.getStateManager()
                .createStateElementAnimation(INDEX_SHELF_ANIM, toProgress);
        shelfAnim.setInterpolator(interpolator);
        shelfAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                mShelfState = ShelfAnimState.CANCEL;
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mIsPeeking = mShelfState == ShelfAnimState.PEEK;
            }
        });
        shelfAnim.setDuration(duration).start();
    }

    /** @return Whether the shelf is currently peeking or animating to or from peeking. */
    public boolean isPeeking() {
        return mIsPeeking;
    }

    /** The various shelf states we can animate to. */
    public enum ShelfAnimState {
        HIDE(true), PEEK(true), OVERVIEW(false), CANCEL(false);

        ShelfAnimState(boolean shouldPreformHaptic) {
            this.shouldPreformHaptic = shouldPreformHaptic;
        }

        public final boolean shouldPreformHaptic;
    }
}
