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

package com.android.launcher3.uioverrides;

import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.util.IntProperty;
import android.view.View;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SurfaceControlCompat;
import com.android.systemui.shared.system.TransactionCompat;

/**
 * Controls the blur, for the Launcher surface only.
 */
public class BackgroundBlurController implements LauncherStateManager.StateHandler {

    public static final IntProperty<BackgroundBlurController> BACKGROUND_BLUR =
            new IntProperty<BackgroundBlurController>("backgroundBlur") {
                @Override
                public void setValue(BackgroundBlurController blurController, int blurRadius) {
                    blurController.setBackgroundBlurRadius(blurRadius);
                }

                @Override
                public Integer get(BackgroundBlurController blurController) {
                    return blurController.mBackgroundBlurRadius;
                }
            };

    /**
     * A property that updates the background blur within a given range of values (ie. even if the
     * animator goes beyond 0..1, the interpolated value will still be bounded).
     */
    public static class ClampedBlurProperty extends IntProperty<BackgroundBlurController> {
        private final int mMinValue;
        private final int mMaxValue;

        public ClampedBlurProperty(int minValue, int maxValue) {
            super(("backgroundBlurClamped"));
            mMinValue = minValue;
            mMaxValue = maxValue;
        }

        @Override
        public void setValue(BackgroundBlurController blurController, int blurRadius) {
            blurController.setBackgroundBlurRadius(Utilities.boundToRange(blurRadius,
                    mMinValue, mMaxValue));
        }

        @Override
        public Integer get(BackgroundBlurController blurController) {
            return blurController.mBackgroundBlurRadius;
        }
    }

    private final Launcher mLauncher;
    private SurfaceControlCompat mSurface;
    private int mBackgroundBlurRadius;

    public BackgroundBlurController(Launcher l) {
        mLauncher = l;
    }

    /**
     * @return the background blur adjustment for folders
     */
    public int getFolderBackgroundBlurAdjustment() {
        return mLauncher.getResources().getInteger(
                R.integer.folder_background_blur_radius_adjustment);
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     */
    public void setSurfaceToApp(RemoteAnimationTargetCompat target) {
        if (target != null) {
            setSurface(target.leash);
        }
    }

    /**
     * Sets the surface to apply the blur to as the launcher surface.
     */
    public void setSurfaceToLauncher(View v) {
        setSurface(v != null ? new SurfaceControlCompat(v) : null);
    }

    private void setSurface(SurfaceControlCompat surface) {
        if (mSurface != surface) {
            mSurface = surface;
            if (surface != null) {
                setBackgroundBlurRadius(mBackgroundBlurRadius);
            } else {
                // If there is no surface, then reset the blur radius
                setBackgroundBlurRadius(0);
            }
        }
    }

    @Override
    public void setState(LauncherState toState) {
        if (mSurface == null) {
            return;
        }

        int toBackgroundBlurRadius = toState.getBackgroundBlurRadius(mLauncher);
        if (mBackgroundBlurRadius != toBackgroundBlurRadius) {
            setBackgroundBlurRadius(toBackgroundBlurRadius);
        }
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, AnimatorSetBuilder builder,
            LauncherStateManager.AnimationConfig config) {
        if (mSurface == null || !config.playNonAtomicComponent()) {
            return;
        }

        int toBackgroundBlurRadius = toState.getBackgroundBlurRadius(mLauncher);
        if (mBackgroundBlurRadius != toBackgroundBlurRadius) {
            PropertySetter propertySetter = config.getPropertySetter(builder);
            propertySetter.setInt(this, BACKGROUND_BLUR, toBackgroundBlurRadius, LINEAR);
        }
    }

    private void setBackgroundBlurRadius(int blurRadius) {
        // TODO: Do nothing if the shadows are not enabled
        // Always update the background blur as it will be reapplied when a surface is next
        // available
        mBackgroundBlurRadius = blurRadius;
        if (mSurface == null || !mSurface.isValid()) {
            return;
        }
        new TransactionCompat()
                .setBackgroundBlurRadius(mSurface, blurRadius)
                .apply();
    }
}
