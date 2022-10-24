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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;

import android.util.FloatProperty;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;

import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;

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

    public static FloatProperty<TransformParams> TARGET_ALPHA =
            new FloatProperty<TransformParams>("targetAlpha") {
        @Override
        public void setValue(TransformParams params, float v) {
            params.setTargetAlpha(v);
        }

        @Override
        public Float get(TransformParams params) {
            return params.getTargetAlpha();
        }
    };

    private float mProgress;
    private float mTargetAlpha;
    private float mCornerRadius;
    private RemoteAnimationTargets mTargetSet;
    private SurfaceTransactionApplier mSyncTransactionApplier;
    private SurfaceControl mRecentsSurface;

    private BuilderProxy mHomeBuilderProxy = BuilderProxy.ALWAYS_VISIBLE;
    private BuilderProxy mBaseBuilderProxy = BuilderProxy.ALWAYS_VISIBLE;

    public TransformParams() {
        mProgress = 0;
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
    public TransformParams setSyncTransactionApplier(SurfaceTransactionApplier applier) {
        mSyncTransactionApplier = applier;
        return this;
    }

    /**
     * Sets an alternate function to control transform for non-target apps. The default
     * implementation keeps the targets visible with alpha=1
     */
    public TransformParams setBaseBuilderProxy(BuilderProxy proxy) {
        mBaseBuilderProxy = proxy;
        return this;
    }

    /**
     * Sets an alternate function to control transform for home target. The default
     * implementation keeps the targets visible with alpha=1
     */
    public TransformParams setHomeBuilderProxy(BuilderProxy proxy) {
        mHomeBuilderProxy = proxy;
        return this;
    }

    public SurfaceTransaction createSurfaceParams(BuilderProxy proxy) {
        RemoteAnimationTargets targets = mTargetSet;
        SurfaceTransaction transaction = new SurfaceTransaction();
        mRecentsSurface = getRecentsSurface(targets);

        for (int i = 0; i < targets.unfilteredApps.length; i++) {
            RemoteAnimationTarget app = targets.unfilteredApps[i];
            SurfaceProperties builder = transaction.forSurface(app.leash);

            if (app.mode == targets.targetMode) {
                int activityType = app.windowConfiguration.getActivityType();
                if (activityType == ACTIVITY_TYPE_HOME) {
                    mHomeBuilderProxy.onBuildTargetParams(builder, app, this);
                } else {
                    // Fade out Assistant overlay.
                    if (activityType == ACTIVITY_TYPE_ASSISTANT && app.isNotInRecents) {
                        float progress = Utilities.boundToRange(getProgress(), 0, 1);
                        builder.setAlpha(1 - Interpolators.DEACCEL_2_5.getInterpolation(progress));
                    } else {
                        builder.setAlpha(getTargetAlpha());
                    }

                    proxy.onBuildTargetParams(builder, app, this);
                }
            } else {
                mBaseBuilderProxy.onBuildTargetParams(builder, app, this);
            }
        }

        // always put wallpaper layer to bottom.
        final int wallpaperLength = targets.wallpapers != null ? targets.wallpapers.length : 0;
        for (int i = 0; i < wallpaperLength; i++) {
            RemoteAnimationTarget wallpaper = targets.wallpapers[i];
            transaction.forSurface(wallpaper.leash).setLayer(Integer.MIN_VALUE);
        }
        return transaction;
    }

    private static SurfaceControl getRecentsSurface(RemoteAnimationTargets targets) {
        for (int i = 0; i < targets.unfilteredApps.length; i++) {
            RemoteAnimationTarget app = targets.unfilteredApps[i];
            if (app.mode == targets.targetMode) {
                if (app.windowConfiguration.getActivityType() == ACTIVITY_TYPE_RECENTS) {
                    return app.leash;
                }
            } else {
                return app.leash;
            }
        }
        return null;
    }

    // Pubic getters so outside packages can read the values.

    public float getProgress() {
        return mProgress;
    }

    public float getTargetAlpha() {
        return mTargetAlpha;
    }

    public float getCornerRadius() {
        return mCornerRadius;
    }

    public SurfaceControl getRecentsSurface() {
        return mRecentsSurface;
    }

    public RemoteAnimationTargets getTargetSet() {
        return mTargetSet;
    }

    public void applySurfaceParams(SurfaceTransaction builder) {
        if (mSyncTransactionApplier != null) {
            mSyncTransactionApplier.scheduleApply(builder);
        } else {
            builder.getTransaction().apply();
        }
    }

    @FunctionalInterface
    public interface BuilderProxy {

        BuilderProxy NO_OP = (builder, app, params) -> { };
        BuilderProxy ALWAYS_VISIBLE = (builder, app, params) -> builder.setAlpha(1);

        void onBuildTargetParams(SurfaceProperties builder,
                RemoteAnimationTarget app, TransformParams params);
    }
}
