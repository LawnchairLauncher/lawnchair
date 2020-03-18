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

import android.os.IBinder;
import android.util.FloatProperty;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SurfaceControlCompat;
import com.android.systemui.shared.system.TransactionCompat;
import com.android.systemui.shared.system.WallpaperManagerCompat;

/**
 * Controls blur and wallpaper zoom, for the Launcher surface only.
 */
public class DepthController implements LauncherStateManager.StateHandler {

    public static final FloatProperty<DepthController> DEPTH =
            new FloatProperty<DepthController>("depth") {
                @Override
                public void setValue(DepthController depthController, float depth) {
                    depthController.setDepth(depth);
                }

                @Override
                public Float get(DepthController depthController) {
                    return depthController.mDepth;
                }
            };

    /**
     * A property that updates the background blur within a given range of values (ie. even if the
     * animator goes beyond 0..1, the interpolated value will still be bounded).
     */
    public static class ClampedDepthProperty extends FloatProperty<DepthController> {
        private final float mMinValue;
        private final float mMaxValue;

        public ClampedDepthProperty(float minValue, float maxValue) {
            super("depthClamped");
            mMinValue = minValue;
            mMaxValue = maxValue;
        }

        @Override
        public void setValue(DepthController depthController, float depth) {
            depthController.setDepth(Utilities.boundToRange(depth, mMinValue, mMaxValue));
        }

        @Override
        public Float get(DepthController depthController) {
            return depthController.mDepth;
        }
    }

    private final Launcher mLauncher;
    /**
     * Blur radius when completely zoomed out, in pixels.
     */
    private int mMaxBlurRadius;
    private WallpaperManagerCompat mWallpaperManager;
    private SurfaceControlCompat mSurface;
    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;

    public DepthController(Launcher l) {
        mLauncher = l;
    }

    private void ensureDependencies() {
        if (mWallpaperManager != null) {
            return;
        }
        mMaxBlurRadius = mLauncher.getResources().getInteger(R.integer.max_depth_blur_radius);
        mWallpaperManager = new WallpaperManagerCompat(mLauncher);
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
                setDepth(mDepth);
            } else {
                // If there is no surface, then reset the ratio
                setDepth(0f);
            }
        }
    }

    @Override
    public void setState(LauncherState toState) {
        if (mSurface == null) {
            return;
        }

        float toDepth = toState.getDepth(mLauncher);
        if (Float.compare(mDepth, toDepth) != 0) {
            setDepth(toDepth);
        }
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, StateAnimationConfig config,
            PendingAnimation animation) {
        if (mSurface == null || config.onlyPlayAtomicComponent()) {
            return;
        }

        float toDepth = toState.getDepth(mLauncher);
        if (Float.compare(mDepth, toDepth) != 0) {
            animation.setFloat(this, DEPTH, toDepth, LINEAR);
        }
    }

    private void setDepth(float depth) {
        mDepth = depth;
        if (mSurface == null || !mSurface.isValid()) {
            return;
        }
        ensureDependencies();
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null) {
            mWallpaperManager.setWallpaperZoomOut(windowToken, mDepth);
        }
        new TransactionCompat()
                .setBackgroundBlurRadius(mSurface, (int) (mDepth * mMaxBlurRadius))
                .apply();
    }
}
