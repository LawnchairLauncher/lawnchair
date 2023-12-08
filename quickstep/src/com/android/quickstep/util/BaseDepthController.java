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
package com.android.quickstep.util;

import android.app.WallpaperManager;
import android.os.IBinder;
import android.util.FloatProperty;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.SurfaceControl;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.systemui.shared.system.BlurUtils;

/**
 * Utility class for applying depth effect
 */
public class BaseDepthController {

    private static final FloatProperty<BaseDepthController> DEPTH =
            new FloatProperty<BaseDepthController>("depth") {
                @Override
                public void setValue(BaseDepthController depthController, float depth) {
                    depthController.setDepth(depth);
                }

                @Override
                public Float get(BaseDepthController depthController) {
                    return depthController.mDepth;
                }
            };

    private static final int DEPTH_INDEX_STATE_TRANSITION = 0;
    private static final int DEPTH_INDEX_WIDGET = 1;
    private static final int DEPTH_INDEX_COUNT = 2;

    // b/291401432
    private static final String TAG = "BaseDepthController";

    protected final Launcher mLauncher;
    /** Property to set the depth for state transition. */
    public final MultiProperty stateDepth;
    /** Property to set the depth for widget picker. */
    public final MultiProperty widgetDepth;

    /**
     * Blur radius when completely zoomed out, in pixels.
     */
    protected final int mMaxBlurRadius;
    protected final WallpaperManager mWallpaperManager;
    protected boolean mCrossWindowBlursEnabled;

    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;

    protected SurfaceControl mSurface;

    // Hints that there is potentially content behind Launcher and that we shouldn't optimize by
    // marking the launcher surface as opaque.  Only used in certain Launcher states.
    private boolean mHasContentBehindLauncher;

    /** Pause blur but allow transparent, can be used when launch something behind the Launcher. */
    protected boolean mPauseBlurs;

    /**
     * Last blur value, in pixels, that was applied.
     * For debugging purposes.
     */
    protected int mCurrentBlur;
    /**
     * If we requested early wake-up offsets to SurfaceFlinger.
     */
    protected boolean mInEarlyWakeUp;

    protected boolean mWaitingOnSurfaceValidity;

    public BaseDepthController(Launcher activity) {
        mLauncher = activity;
        mMaxBlurRadius = activity.getResources().getInteger(R.integer.max_depth_blur_radius);
        mWallpaperManager = activity.getSystemService(WallpaperManager.class);

        MultiPropertyFactory<BaseDepthController> depthProperty =
                new MultiPropertyFactory<>(this, DEPTH, DEPTH_INDEX_COUNT, Float::max);
        stateDepth = depthProperty.get(DEPTH_INDEX_STATE_TRANSITION);
        widgetDepth = depthProperty.get(DEPTH_INDEX_WIDGET);
    }

    protected void setCrossWindowBlursEnabled(boolean isEnabled) {
        mCrossWindowBlursEnabled = isEnabled;
        applyDepthAndBlur();
    }

    public void setHasContentBehindLauncher(boolean hasContentBehindLauncher) {
        mHasContentBehindLauncher = hasContentBehindLauncher;
    }

    public void pauseBlursOnWindows(boolean pause) {
        if (pause != mPauseBlurs) {
            mPauseBlurs = pause;
            applyDepthAndBlur();
        }
    }

    protected void onInvalidSurface() { }

    protected void applyDepthAndBlur() {
        float depth = mDepth;
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null) {
            // The API's full zoom-out is three times larger than the zoom-out we apply to the
            // icons. To keep the two consistent throughout the animation while keeping Launcher's
            // concept of full depth unchanged, we divide the depth by 3 here.
            mWallpaperManager.setWallpaperZoomOut(windowToken, depth / 3);
        }

        if (!BlurUtils.supportsBlursOnWindows()) {
            return;
        }
        if (mSurface == null) {
            Log.d(TAG, "mSurface is null and mCurrentBlur is: " + mCurrentBlur);
            return;
        }
        if (!mSurface.isValid()) {
            Log.d(TAG, "mSurface is not valid");
            mWaitingOnSurfaceValidity = true;
            onInvalidSurface();
            return;
        }
        mWaitingOnSurfaceValidity = false;
        boolean hasOpaqueBg = mLauncher.getScrimView().isFullyOpaque();
        boolean isSurfaceOpaque = !mHasContentBehindLauncher && hasOpaqueBg && !mPauseBlurs;

        mCurrentBlur = !mCrossWindowBlursEnabled || hasOpaqueBg || mPauseBlurs
                ? 0 : (int) (depth * mMaxBlurRadius);
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()
                .setBackgroundBlurRadius(mSurface, mCurrentBlur)
                .setOpaque(mSurface, isSurfaceOpaque);

        // Set early wake-up flags when we know we're executing an expensive operation, this way
        // SurfaceFlinger will adjust its internal offsets to avoid jank.
        boolean wantsEarlyWakeUp = depth > 0 && depth < 1;
        if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
            transaction.setEarlyWakeupStart();
            mInEarlyWakeUp = true;
        } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
            transaction.setEarlyWakeupEnd();
            mInEarlyWakeUp = false;
        }

        AttachedSurfaceControl rootSurfaceControl =
                mLauncher.getRootView().getRootSurfaceControl();
        if (rootSurfaceControl != null) {
            rootSurfaceControl.applyTransactionOnDraw(transaction);
        }
    }

    private void setDepth(float depth) {
        depth = Utilities.boundToRange(depth, 0, 1);
        // Round out the depth to dedupe frequent, non-perceptable updates
        int depthI = (int) (depth * 256);
        float depthF = depthI / 256f;
        if (Float.compare(mDepth, depthF) == 0) {
            return;
        }
        mDepth = depthF;
        applyDepthAndBlur();
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     */
    protected void setSurface(SurfaceControl surface) {
        if (mSurface != surface || mWaitingOnSurfaceValidity) {
            mSurface = surface;
            Log.d(TAG, "setSurface:\n\tmWaitingOnSurfaceValidity: " + mWaitingOnSurfaceValidity
                    + "\n\tmSurface: " + mSurface);
            applyDepthAndBlur();
        }
    }
}
