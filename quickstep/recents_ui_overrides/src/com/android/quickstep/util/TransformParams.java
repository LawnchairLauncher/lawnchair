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
package com.android.quickstep.util;

import android.graphics.RectF;
import android.util.FloatProperty;

import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;
import com.android.systemui.shared.system.TransactionCompat;

public class TransformParams {

    public static FloatProperty<TransformParams> PROGRESS =
            new FloatProperty<TransformParams>("progress") {
        @Override
        public void setValue(TransformParams params, float v) {
            params.setProgress(v);
        }

        @Override
        public Float get(TransformParams params) {
            return params.getProgress();
        }
    };

    private float mProgress;
    private @Nullable RectF mCurrentRect;
    private float mTargetAlpha;
    private float mCornerRadius;
    private RemoteAnimationTargets mTargetSet;
    private SyncRtSurfaceTransactionApplierCompat mSyncTransactionApplier;

    private TargetAlphaProvider mTaskAlphaCallback = (t, a) -> a;
    private TargetAlphaProvider mBaseAlphaCallback = (t, a) -> 1;

    public TransformParams() {
        mProgress = 0;
        mCurrentRect = null;
        mTargetAlpha = 1;
        mCornerRadius = -1;
    }

    /**
     * Sets the progress of the transformation, where 0 is the source and 1 is the target. We
     * automatically adjust properties such as currentRect and cornerRadius based on this
     * progress, unless they are manually overridden by setting them on this TransformParams.
     */
    public TransformParams setProgress(float progress) {
        mProgress = progress;
        return this;
    }

    /**
     * Sets the corner radius of the transformed window, in pixels. If unspecified (-1), we
     * simply interpolate between the window's corner radius to the task view's corner radius,
     * based on {@link #mProgress}.
     */
    public TransformParams setCornerRadius(float cornerRadius) {
        mCornerRadius = cornerRadius;
        return this;
    }

    /**
     * Sets the current rect to show the transformed window, in device coordinates. This gives
     * the caller manual control of where to show the window. If unspecified (null), we
     * interpolate between {@link AppWindowAnimationHelper#mSourceRect} and
     * {@link AppWindowAnimationHelper#mTargetRect}, based on {@link #mProgress}.
     */
    public TransformParams setCurrentRect(RectF currentRect) {
        mCurrentRect = currentRect;
        return this;
    }

    /**
     * Specifies the alpha of the transformed window. Default is 1.
     */
    public TransformParams setTargetAlpha(float targetAlpha) {
        mTargetAlpha = targetAlpha;
        return this;
    }

    /**
     * Specifies the set of RemoteAnimationTargetCompats that are included in the transformation
     * that these TransformParams help compute. These TransformParams generally only apply to
     * the targetSet.apps which match the targetSet.targetMode (e.g. the MODE_CLOSING app when
     * swiping to home).
     */
    public TransformParams setTargetSet(RemoteAnimationTargets targetSet) {
        mTargetSet = targetSet;
        return this;
    }

    /**
     * Sets the SyncRtSurfaceTransactionApplierCompat that will apply the SurfaceParams that
     * are computed based on these TransformParams.
     */
    public TransformParams setSyncTransactionApplier(
            SyncRtSurfaceTransactionApplierCompat applier) {
        mSyncTransactionApplier = applier;
        return this;
    }

    /**
     * Sets an alternate function which can be used to control the alpha of target app
     */
    public TransformParams setTaskAlphaCallback(TargetAlphaProvider callback) {
        mTaskAlphaCallback = callback;
        return this;
    }

    /**
     * Sets an alternate function which can be used to control the alpha of non-target app
     */
    public TransformParams setBaseAlphaCallback(TargetAlphaProvider callback) {
        mBaseAlphaCallback = callback;
        return this;
    }

    public SurfaceParams[] createSurfaceParams(BuilderProxy proxy) {
        RemoteAnimationTargets targets = mTargetSet;
        SurfaceParams[] surfaceParams = new SurfaceParams[targets.unfilteredApps.length];
        for (int i = 0; i < targets.unfilteredApps.length; i++) {
            RemoteAnimationTargetCompat app = targets.unfilteredApps[i];
            SurfaceParams.Builder builder = new SurfaceParams.Builder(app.leash);

            float progress = Utilities.boundToRange(getProgress(), 0, 1);
            float alpha;
            if (app.mode == targets.targetMode) {
                alpha = mTaskAlphaCallback.getAlpha(app, getTargetAlpha());
                if (app.activityType != RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                    // Fade out Assistant overlay.
                    if (app.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_ASSISTANT
                            && app.isNotInRecents) {
                        alpha = 1 - Interpolators.DEACCEL_2_5.getInterpolation(progress);
                    }
                } else if (targets.hasRecents) {
                    // If home has a different target then recents, reverse anim the
                    // home target.
                    alpha = 1 - (progress * getTargetAlpha());
                }
            } else {
                alpha = mBaseAlphaCallback.getAlpha(app, progress);
            }
            proxy.onBuildParams(builder.withAlpha(alpha), app, targets.targetMode, this);
            surfaceParams[i] = builder.build();
        }
        return surfaceParams;
    }

    // Pubic getters so outside packages can read the values.

    public float getProgress() {
        return mProgress;
    }

    @Nullable
    public RectF getCurrentRect() {
        return mCurrentRect;
    }

    public float getTargetAlpha() {
        return mTargetAlpha;
    }

    public float getCornerRadius() {
        return mCornerRadius;
    }

    public RemoteAnimationTargets getTargetSet() {
        return mTargetSet;
    }

    public void applySurfaceParams(SurfaceParams[] params) {
        if (mSyncTransactionApplier != null) {
            mSyncTransactionApplier.scheduleApply(params);
        } else {
            TransactionCompat t = new TransactionCompat();
            for (SurfaceParams param : params) {
                SyncRtSurfaceTransactionApplierCompat.applyParams(t, param);
            }
            t.setEarlyWakeup();
            t.apply();
        }
    }

    public interface TargetAlphaProvider {
        float getAlpha(RemoteAnimationTargetCompat target, float expectedAlpha);
    }

    public interface BuilderProxy {

        default void onBuildParams(SurfaceParams.Builder builder,
                RemoteAnimationTargetCompat app, int targetMode, TransformParams params) {
            if (app.mode == targetMode
                    && app.activityType != RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                onBuildTargetParams(builder, app, params);
            }
        }

        default void onBuildTargetParams(SurfaceParams.Builder builder,
                RemoteAnimationTargetCompat app, TransformParams params) { }
    }
}
