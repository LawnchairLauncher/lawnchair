/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;

/**
 * Animation to animate in a workspace during the unlock transition.
 */
// TODO(b/219444608): use SCALE_PROPERTY_FACTORY once the scale is reset to 1.0 after unlocking.
public class WorkspaceUnlockAnim {
    /** Scale for the workspace icons at the beginning of the animation. */
    private static final float START_SCALE = 0.9f;

    /**
     * Starting translation Y values for the animation. We use a larger value if we're animating in
     * from a swipe, since there is more perceived upward movement when we unlock from a swipe.
     */
    private static final int START_TRANSLATION_DP = 15;
    private static final int START_TRANSLATION_SWIPE_DP = 60;

    private Launcher mLauncher;
    private float mUnlockAmount = 0f;

    public WorkspaceUnlockAnim(Launcher launcher) {
        mLauncher = launcher;
    }

    /**
     * Called when we're about to make the Launcher window visible and play the unlock animation.
     *
     * This is a blocking call so that System UI knows it's safe to show the Launcher window without
     * causing the Launcher contents to flicker on screen. Do not do anything expensive here.
     */
    public void prepareForUnlock() {
        mLauncher.getWorkspace().setAlpha(0f);
        mLauncher.getHotseat().setAlpha(0f);

        mUnlockAmount = 0f;
    }

    public void setUnlockAmount(float amount, boolean fromSwipe) {
        mUnlockAmount = amount;

        final float amountInverse = 1f - amount;
        final float scale = START_SCALE + (1f - START_SCALE) * amount;

        mLauncher.getWorkspace().setScaleX(scale);
        mLauncher.getWorkspace().setScaleY(scale);
        mLauncher.getWorkspace().setAlpha(amount);
        mLauncher.getWorkspace().setPivotToScaleWithSelf(mLauncher.getHotseat());

        mLauncher.getHotseat().setScaleX(scale);
        mLauncher.getHotseat().setScaleY(scale);
        mLauncher.getHotseat().setAlpha(amount);

        if (fromSwipe) {
            mLauncher.getWorkspace().setTranslationY(
                    Utilities.dpToPx(START_TRANSLATION_SWIPE_DP) * amountInverse);
            mLauncher.getHotseat().setTranslationY(
                    Utilities.dpToPx(START_TRANSLATION_SWIPE_DP) * amountInverse);
        } else {
            mLauncher.getWorkspace().setTranslationY(
                    Utilities.dpToPx(START_TRANSLATION_DP) * amountInverse);
            mLauncher.getHotseat().setTranslationY(
                    Utilities.dpToPx(START_TRANSLATION_DP) * amountInverse);
        }
    }
}
