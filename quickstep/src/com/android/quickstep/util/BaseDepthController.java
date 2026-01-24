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

import static android.os.Trace.TRACE_TAG_APP;

import static com.android.launcher3.Flags.enableOverviewBackgroundWallpaperBlur;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;

import android.app.WallpaperManager;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.gui.EarlyWakeupInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Trace;
import android.util.FloatProperty;
import android.util.Log;
import android.view.AttachedSurfaceControl;
import android.view.CrossWindowBlurListeners;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.systemui.shared.system.BlurUtils;

/**
 * Utility class for applying depth effect
 */
public class BaseDepthController {
    public static final float DEPTH_0_PERCENT = 0f;
    public static final float DEPTH_60_PERCENT = 0.6f;
    public static final float DEPTH_70_PERCENT = 0.7f;

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

    protected final QuickstepLauncher mLauncher;
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
     *
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;

    protected SurfaceControl mBaseSurface;

    protected SurfaceControl mBaseSurfaceOverride;

    // Hints that there is potentially content behind Launcher and that we shouldn't optimize by
    // marking the launcher surface as opaque.  Only used in certain Launcher states.
    private boolean mHasContentBehindLauncher;

    /** Pause blur but allow transparent, can be used when launch something behind the Launcher. */
    protected boolean mPauseBlurs;

    /**
     * Last blur value, in pixels, that was applied.
     */
    protected int mCurrentBlur;
    /**
     * If we requested early wake-up offsets to SurfaceFlinger.
     */
    protected boolean mInEarlyWakeUp;

    protected boolean mWaitingOnSurfaceValidity;

    private SurfaceControl mBlurSurface = null;
    /**
     * Info for early wakeup requests to SurfaceFlinger.
     */
//    private EarlyWakeupInfo mEarlyWakeupInfo = new EarlyWakeupInfo();

    public BaseDepthController(QuickstepLauncher activity) {
        mLauncher = activity;
        if (Flags.allAppsBlur() || enableOverviewBackgroundWallpaperBlur()) {
            if (Utilities.ATLEAST_S) {
                mCrossWindowBlursEnabled =
                        CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled();
            } else {
                mCrossWindowBlursEnabled = false;
            }
            mMaxBlurRadius = activity.getResources().getDimensionPixelSize(
                    R.dimen.max_depth_blur_radius_enhanced);
        } else {
            mMaxBlurRadius = activity.getResources().getInteger(R.integer.max_depth_blur_radius);
        }
        mWallpaperManager = activity.getSystemService(WallpaperManager.class);

        MultiPropertyFactory<BaseDepthController> depthProperty =
                new MultiPropertyFactory<>(this, DEPTH, DEPTH_INDEX_COUNT, Float::max);
        stateDepth = depthProperty.get(DEPTH_INDEX_STATE_TRANSITION);
        widgetDepth = depthProperty.get(DEPTH_INDEX_WIDGET);
//        mEarlyWakeupInfo.token = new Binder();
//        mEarlyWakeupInfo.trace = BaseDepthController.class.getName();
    }

    /**
     * Returns if cross window blurs are enabled. In other words, whether launcher should use blurs
     * style UI or fallback style UI.
     */
    public boolean isCrossWindowBlursEnabled() {
        return mCrossWindowBlursEnabled;
    }

    protected void setCrossWindowBlursEnabled(boolean isEnabled) {
        if (mCrossWindowBlursEnabled == isEnabled) {
            return;
        }
        mCrossWindowBlursEnabled = isEnabled;
        mLauncher.updateBlurStyle();
        applyDepthAndBlur();
    }

    public void setHasContentBehindLauncher(boolean hasContentBehindLauncher) {
        mHasContentBehindLauncher = hasContentBehindLauncher;
    }

    public void pauseBlursOnWindows(boolean pause) {
        if (mPauseBlurs == pause) {
            return;
        }
        mPauseBlurs = pause;
        applyDepthAndBlur();
    }

    protected void onInvalidSurface() { }

    protected void applyDepthAndBlur() {
        applyDepthAndBlur(null, /* applyImmediately */ false, /* skipSimilarBlur */ true);
    }

    /**
     * Applies depth and blur to the launcher.
     *
     * @param transaction      optional Surface to apply to the blur to.
     * @param applyImmediately whether to apply the blur immediately or defer to the next frame.
     */
    protected void applyDepthAndBlur(SurfaceControl.Transaction transaction,
            boolean applyImmediately, boolean skipSimilarBlur) {
        try (transaction) {
            applyDepthAndBlurInternal(transaction, applyImmediately, skipSimilarBlur);
        }
    }

    private void applyDepthAndBlurInternal(SurfaceControl.Transaction transaction,
            boolean applyImmediately, boolean skipSimilarBlur) {
        float depth = mDepth;
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null) {
            if (!Utilities.ATLEAST_R) return;
            if (enableScalingRevealHomeAnimation()) {
                mWallpaperManager.setWallpaperZoomOut(windowToken, depth);
            } else {
                // The API's full zoom-out is three times larger than the zoom-out we apply
                // to the
                // icons. To keep the two consistent throughout the animation while keeping
                // Launcher's concept of full depth unchanged, we divide the depth by 3 here.
                mWallpaperManager.setWallpaperZoomOut(windowToken, depth / 3);
            }
        }

        if (!BlurUtils.supportsBlursOnWindows()) {
            return;
        }
        if (mBaseSurface == null) {
            Log.d(TAG, "mSurface is null and mCurrentBlur is: " + mCurrentBlur);
            return;
        }
        if (!mBaseSurface.isValid()) {
            Log.d(TAG, "mSurface is not valid");
            mWaitingOnSurfaceValidity = true;
            onInvalidSurface();
            return;
        }
        mWaitingOnSurfaceValidity = false;
        boolean hasOpaqueBg = mLauncher.getScrimView().isFullyOpaque();
        boolean isSurfaceOpaque = !mHasContentBehindLauncher && hasOpaqueBg && !mPauseBlurs;

        float blurAmount;
        if (enableScalingRevealHomeAnimation()) {
            blurAmount = mapDepthToBlur(depth);
        } else {
            blurAmount = depth;
        }
        SurfaceControl blurSurface =
                enableOverviewBackgroundWallpaperBlur() && mBlurSurface != null ? mBlurSurface
                        : mBaseSurface;

        int previousBlur = mCurrentBlur;
        int newBlur = mCrossWindowBlursEnabled && !hasOpaqueBg && !mPauseBlurs ? (int) (blurAmount
                * mMaxBlurRadius) : 0;
        int delta = Math.abs(newBlur - previousBlur);
        if (skipSimilarBlur && delta < Utilities.dpToPx(1) && newBlur != 0 && previousBlur != 0
                && blurAmount != 1f) {
            Log.d(TAG, "Skipping small blur delta. newBlur: " + newBlur + " previousBlur: "
                    + previousBlur + " delta: " + delta + " surface: " + blurSurface);
            return;
        }
        mCurrentBlur = newBlur;
        Log.v(TAG, "Applying blur: " + mCurrentBlur + " to " + blurSurface);

        final SurfaceControl.Transaction finalTransaction =
                transaction == null ? createTransaction() : transaction;
        
        // LC: Fix blur effect on Android 12.1/12.0
        try (finalTransaction) {
            if (blurSurface != null && blurSurface.isValid()) {
                finalTransaction.setBackgroundBlurRadius(blurSurface, mCurrentBlur)
                        .setOpaque(blurSurface, isSurfaceOpaque);
            } else {
                // GRASP
                return;
            }

            boolean wantsEarlyWakeUp = blurAmount > 0 && blurAmount < 1;
            if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
                try {
                    setEarlyWakeup(finalTransaction, true);
                } catch (NoSuchMethodError e) {
                    // LC-Ignored: wtf?
                }
            } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
                try {
                    setEarlyWakeup(finalTransaction, false);
                } catch (NoSuchMethodError e) {
                    // LC-Ignored: wtf?
                }
            }

            // LC: Always apply immediately.
            finalTransaction.apply();
        }

        blurWorkspaceDepthTargets();
    }

    /**
     * Sets the early wakeup state.
     *
     * @param inEarlyWakeUp whether SurfaceFlinger's early wakeup timing should be active.
     */
    public void setEarlyWakeup(boolean inEarlyWakeUp) {
        if (mInEarlyWakeUp == inEarlyWakeUp) {
            return;
        }
        try (SurfaceControl.Transaction transaction = createTransaction()) {
            setEarlyWakeup(transaction, inEarlyWakeUp);
            transaction.apply();
        }
    }

    /**
     * Sets the early wakeup state.
     *
     * @param transaction transaction to apply to.
     * @param start whether to start or end the early wakeup.
     */
    protected void setEarlyWakeup(@NonNull SurfaceControl.Transaction transaction, boolean start) {
        if (mInEarlyWakeUp == start) {
            return;
        }
        Log.d(TAG, "setEarlyWakeup: " + start);
        if (start) {
//            Trace.instantForTrack(TRACE_TAG_APP, TAG, "notifyRendererForGpuLoadUp");
//            mLauncher.getRootView().getViewRootImpl().notifyRendererForGpuLoadUp("applyBlur");
//            transaction.setEarlyWakeupStart();
        } else {
//            transaction.setEarlyWakeupEnd();
        }
        mInEarlyWakeUp = start;
    }

    /** @return {@code true} if the workspace should be blurred. */
    @VisibleForTesting
    public boolean blurWorkspaceDepthTargets() {
        if (!Flags.allAppsBlur()) {
            return false;
        }
        StateManager<LauncherState, Launcher> stateManager = mLauncher.getStateManager();
        LauncherState targetState = stateManager.getTargetState() != null
                ? stateManager.getTargetState() : stateManager.getState();
        // Only blur workspace if the current state wants to blur based on the target state.
        boolean shouldBlurWorkspace =
                stateManager.getCurrentStableState().shouldBlurWorkspace(targetState);

        RenderEffect blurEffect = shouldBlurWorkspace && mCurrentBlur > 0
                ? RenderEffect.createBlurEffect(mCurrentBlur, mCurrentBlur, Shader.TileMode.DECAL)
                // If blur is not desired, clear the blur effect from the depth targets.
                : null;
        mLauncher.getDepthBlurTargets().forEach(target -> target.setRenderEffect(blurEffect));
        return shouldBlurWorkspace;
    }

    private void setDepth(float depth) {
        depth = Utilities.boundToRange(depth, 0, 1);
        // Depth of the Launcher state we are in or transitioning to.
        float targetStateDepth = mLauncher.getStateManager().getState().getDepth(mLauncher);

        float depthF;
        if (depth == targetStateDepth) {
            // Always apply the target state depth.
            depthF = depth;
        } else {
            // Round out the depth to dedupe frequent, non-perceptable updates
            int depthI = (int) (depth * 256);
            depthF = depthI / 256f;
        }
        if (Float.compare(mDepth, depthF) == 0) {
            return;
        }
        mDepth = depthF;
        applyDepthAndBlur();
    }

    /**
     * Sets the lowest surface that should not be blurred.
     * <p>
     * Blur is applied to below {@link #mBaseSurfaceOverride}. When set to {@code null}, blur is
     * applied to below {@link #mBaseSurface}.
     * </p>
     */
    public void setBaseSurfaceOverride(@Nullable SurfaceControl baseSurfaceOverride,
            boolean applyOnDraw) {
        if (mBaseSurfaceOverride != baseSurfaceOverride) {
            boolean applyImmediately = mBaseSurfaceOverride != null && baseSurfaceOverride == null
                    && !applyOnDraw;
            mBaseSurfaceOverride = baseSurfaceOverride;
            Log.d(TAG, "setBaseSurfaceOverride: applying blur behind leash " + baseSurfaceOverride);
            SurfaceControl.Transaction transaction = setupBlurSurface();
            applyDepthAndBlur(transaction, applyImmediately, /* skipSimilarBlur */ false);
        }
    }

    private @Nullable SurfaceControl.Transaction setupBlurSurface() {
        SurfaceControl.Transaction transaction = null;
        if (mBaseSurface != null && mBaseSurfaceOverride != null) {
            transaction = createTransaction().setBackgroundBlurRadius(mBaseSurface, 0)
                    .setOpaque(mBaseSurface, false);
            if (mBlurSurface == null) {
                mBlurSurface = new SurfaceControl.Builder()
                        .setName("Overview Blur")
                        .setHidden(false)
                        .build();
                Log.d(TAG,
                        "setupBlurSurface: creating Overview Blur surface " + mBlurSurface);
                transaction.reparent(mBlurSurface, mBaseSurface);
                Log.d(TAG,
                        "setupBlurSurface: reparenting " + mBlurSurface + " to " + mBaseSurface);
            }
            transaction.setRelativeLayer(mBlurSurface, mBaseSurfaceOverride, -1);
            Log.d(TAG, "setupBlurSurface: relayering to leash " + mBaseSurfaceOverride);
        } else if (mBlurSurface != null) {
            Log.d(TAG, "setupBlurSurface: removing blur surface " + mBlurSurface);
            transaction = createTransaction().remove(mBlurSurface);
            mBlurSurface = null;
        }
        return transaction;
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     */
    protected void setBaseSurface(SurfaceControl baseSurface) {
        if (mBaseSurface != baseSurface || mWaitingOnSurfaceValidity) {
            mBaseSurface = baseSurface;
            Log.d(TAG, "setSurface:\n\tmWaitingOnSurfaceValidity: " + mWaitingOnSurfaceValidity
                    + "\n\tmBaseSurface: " + mBaseSurface);
            SurfaceControl.Transaction transaction = null;
            if (enableOverviewBackgroundWallpaperBlur()) {
                transaction = setupBlurSurface();
            }
            applyDepthAndBlur(transaction, /* applyImmediately */ false,
                    /* skipSimilarBlur */ false);
        }
    }

    /**
     * Maps depth values to blur amounts as a percentage of the max blur.
     * The blur percentage grows linearly with depth, and maxes out at 30% depth.
     */
    private static float mapDepthToBlur(float depth) {
        return Interpolators.clampToProgress(depth, 0, 0.3f);
    }

    private SurfaceControl.Transaction createTransaction() {
        return new SurfaceControl.Transaction();
    }
}
