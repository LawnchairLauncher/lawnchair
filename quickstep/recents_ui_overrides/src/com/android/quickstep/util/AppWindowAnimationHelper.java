/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.systemui.shared.system.QuickStepContract.getWindowCornerRadius;
import static com.android.systemui.shared.system.QuickStepContract.supportsRoundedCornersOnWindows;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.systemui.shared.recents.utilities.RectFEvaluator;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;
import com.android.systemui.shared.system.TransactionCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Utility class to handle window clip animation
 */
@TargetApi(Build.VERSION_CODES.P)
public class AppWindowAnimationHelper {

    // The bounds of the source app in device coordinates
    private final RectF mSourceStackBounds = new RectF();
    // The insets of the source app
    private final Rect mSourceInsets = new Rect();
    // The source app bounds with the source insets applied, in the device coordinates
    private final RectF mSourceRect = new RectF();
    // The bounds of the task view in device coordinates
    private final RectF mTargetRect = new RectF();
    // The bounds of the app window (between mSourceRect and mTargetRect) in device coordinates
    private final RectF mCurrentRect = new RectF();
    // The insets to be used for clipping the app window, which can be larger than mSourceInsets
    // if the aspect ratio of the target is smaller than the aspect ratio of the source rect. In
    // app window coordinates.
    private final RectF mSourceWindowClipInsets = new RectF();
    // The clip rect in source app window coordinates. The app window surface will only be drawn
    // within these bounds. This clip rect starts at the full mSourceStackBounds, and insets by
    // mSourceWindowClipInsets as the transform progress goes to 1.
    private final RectF mCurrentClipRectF = new RectF();

    // The bounds of launcher (not including insets) in device coordinates
    public final Rect mHomeStackBounds = new Rect();
    private final RectFEvaluator mRectFEvaluator = new RectFEvaluator();
    private final Matrix mTmpMatrix = new Matrix();
    private final Rect mTmpRect = new Rect();
    private final RectF mTmpRectF = new RectF();
    private final RectF mCurrentRectWithInsets = new RectF();
    private RecentsOrientedState mOrientedState;
    // Corner radius of windows, in pixels
    private final float mWindowCornerRadius;
    // Corner radius of windows when they're in overview mode.
    private final float mTaskCornerRadius;
    // If windows can have real time rounded corners.
    private final boolean mSupportsRoundedCornersOnWindows;
    // Whether or not to actually use the rounded cornders on windows
    private boolean mUseRoundedCornersOnWindows;

    // Corner radius currently applied to transformed window.
    private float mCurrentCornerRadius;

    private TargetAlphaProvider mTaskAlphaCallback = (t, a) -> a;
    private TargetAlphaProvider mBaseAlphaCallback = (t, a) -> 1;

    public AppWindowAnimationHelper(RecentsOrientedState orientedState, Context context) {
        Resources res = context.getResources();
        mOrientedState = orientedState;
        mWindowCornerRadius = getWindowCornerRadius(res);
        mSupportsRoundedCornersOnWindows = supportsRoundedCornersOnWindows(res);
        mTaskCornerRadius = TaskCornerRadius.get(context);
        mUseRoundedCornersOnWindows = mSupportsRoundedCornersOnWindows;
    }

    public AppWindowAnimationHelper(Context context) {
        this(null, context);
    }

    private void updateSourceStack(RemoteAnimationTargetCompat target) {
        mSourceInsets.set(target.contentInsets);
        mSourceStackBounds.set(target.screenSpaceBounds);

        // TODO: Should sourceContainerBounds already have this offset?
        mSourceStackBounds.offsetTo(target.position.x, target.position.y);
    }

    public void updateSource(Rect homeStackBounds, RemoteAnimationTargetCompat target) {
        updateSourceStack(target);
        updateHomeBounds(homeStackBounds);
    }

    public void updateHomeBounds(Rect homeStackBounds) {
        mHomeStackBounds.set(homeStackBounds);
    }

    public void updateTargetRect(Rect targetRect) {
        mSourceRect.set(mSourceInsets.left, mSourceInsets.top,
                mSourceStackBounds.width() - mSourceInsets.right,
                mSourceStackBounds.height() - mSourceInsets.bottom);
        mTargetRect.set(targetRect);
        mTargetRect.offset(mHomeStackBounds.left - mSourceStackBounds.left,
                mHomeStackBounds.top - mSourceStackBounds.top);

        // Calculate the clip based on the target rect (since the content insets and the
        // launcher insets may differ, so the aspect ratio of the target rect can differ
        // from the source rect. The difference between the target rect (scaled to the
        // source rect) is the amount to clip on each edge.
        RectF scaledTargetRect = new RectF(mTargetRect);
        float scale = getSrcToTargetScale();
        Utilities.scaleRectFAboutCenter(scaledTargetRect, scale);

        scaledTargetRect.offsetTo(mSourceRect.left, mSourceRect.top);
        mSourceWindowClipInsets.set(
                Math.max(scaledTargetRect.left, 0),
                Math.max(scaledTargetRect.top, 0),
                Math.max(mSourceStackBounds.width() - scaledTargetRect.right, 0),
                Math.max(mSourceStackBounds.height() - scaledTargetRect.bottom, 0));
        mSourceRect.set(scaledTargetRect);
    }

    public float getSrcToTargetScale() {
        return LayoutUtils.getTaskScale(mOrientedState,
                mSourceRect.width(), mSourceRect.height(),
                mTargetRect.width(), mTargetRect.height());
    }

    public void prepareAnimation(DeviceProfile dp) {
        mUseRoundedCornersOnWindows = mSupportsRoundedCornersOnWindows && !dp.isMultiWindowMode;
    }

    public RectF applyTransform(TransformParams params) {
        SurfaceParams[] surfaceParams = computeSurfaceParams(params);
        if (surfaceParams == null) {
            return null;
        }
        applySurfaceParams(params.mSyncTransactionApplier, surfaceParams);
        return mCurrentRect;
    }

    /**
     * Updates this AppWindowAnimationHelper's state based on the given TransformParams, and returns
     * the SurfaceParams to apply via {@link SyncRtSurfaceTransactionApplierCompat#applyParams}.
     */
    public SurfaceParams[] computeSurfaceParams(TransformParams params) {
        if (params.mTargetSet == null) {
            return null;
        }

        float progress = Utilities.boundToRange(params.mProgress, 0, 1);
        updateCurrentRect(params);

        SurfaceParams[] surfaceParams = new SurfaceParams[params.mTargetSet.unfilteredApps.length];
        for (int i = 0; i < params.mTargetSet.unfilteredApps.length; i++) {
            RemoteAnimationTargetCompat app = params.mTargetSet.unfilteredApps[i];
            SurfaceParams.Builder builder = new SurfaceParams.Builder(app.leash);
            if (app.localBounds != null) {
                mTmpMatrix.setTranslate(0, 0);
                if (app.activityType == ACTIVITY_TYPE_HOME && app.mode == MODE_CLOSING) {
                    mTmpMatrix.setTranslate(app.localBounds.left, app.localBounds.top);
                }
            } else {
                mTmpMatrix.setTranslate(app.position.x, app.position.y);
            }

            Rect crop = mTmpRect;
            crop.set(app.screenSpaceBounds);
            crop.offsetTo(0, 0);
            float alpha;
            float cornerRadius = 0f;
            float scale = Math.max(mCurrentRect.width(), mTargetRect.width()) / crop.width();
            if (app.mode == params.mTargetSet.targetMode) {
                alpha = mTaskAlphaCallback.getAlpha(app, params.mTargetAlpha);
                if (app.activityType != RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                    mTmpMatrix.setRectToRect(mSourceRect, mCurrentRect, ScaleToFit.FILL);
                    if (app.localBounds != null) {
                        mTmpMatrix.postTranslate(app.localBounds.left, app.localBounds.top);
                    } else {
                        mTmpMatrix.postTranslate(app.position.x, app.position.y);
                    }
                    mCurrentClipRectF.roundOut(crop);
                    if (mSupportsRoundedCornersOnWindows) {
                        if (params.mCornerRadius > -1) {
                            cornerRadius = params.mCornerRadius;
                            scale = mCurrentRect.width() / crop.width();
                        } else {
                            float windowCornerRadius = mUseRoundedCornersOnWindows
                                    ? mWindowCornerRadius : 0;
                            cornerRadius = Utilities.mapRange(progress, windowCornerRadius,
                                    mTaskCornerRadius);
                        }
                        mCurrentCornerRadius = cornerRadius;
                    }
                    // Fade out Assistant overlay.
                    if (app.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_ASSISTANT
                            && app.isNotInRecents) {
                        alpha = 1 - Interpolators.DEACCEL_2_5.getInterpolation(progress);
                    }
                } else if (params.mTargetSet.hasRecents) {
                    // If home has a different target then recents, reverse anim the
                    // home target.
                    alpha = 1 - (progress * params.mTargetAlpha);
                }
            } else {
                alpha = mBaseAlphaCallback.getAlpha(app, progress);
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() && params.mLauncherOnTop) {
                    crop = null;
                }
            }
            builder.withAlpha(alpha)
                    .withMatrix(mTmpMatrix)
                    .withWindowCrop(crop)
                    // Since radius is in Surface space, but we draw the rounded corners in screen
                    // space, we have to undo the scale
                    .withCornerRadius(cornerRadius / scale);
            surfaceParams[i] = builder.build();
        }
        return surfaceParams;
    }

    public RectF updateCurrentRect(TransformParams params) {
        if (params.mCurrentRect != null) {
            mCurrentRect.set(params.mCurrentRect);
        } else {
            mTmpRectF.set(mTargetRect);
            Utilities.scaleRectFAboutCenter(mTmpRectF, params.mOffsetScale);
            mCurrentRect.set(mRectFEvaluator.evaluate(params.mProgress, mSourceRect, mTmpRectF));
            if (mOrientedState == null
                    || !mOrientedState.isMultipleOrientationSupportedByDevice()) {
                mCurrentRect.offset(params.mOffset, 0);
            } else {
                int displayRotation = mOrientedState.getDisplayRotation();
                int launcherRotation = mOrientedState.getLauncherRotation();
                mOrientedState.getOrientationHandler().offsetTaskRect(mCurrentRect,
                    params.mOffset, displayRotation, launcherRotation);
            }
        }

        updateClipRect(params);
        return mCurrentRect;
    }

    private void updateClipRect(TransformParams params) {
        // Don't clip past progress > 1.
        float progress = Math.min(1, params.mProgress);
        mCurrentClipRectF.left = mSourceWindowClipInsets.left * progress;
        mCurrentClipRectF.top = mSourceWindowClipInsets.top * progress;
        mCurrentClipRectF.right =
                mSourceStackBounds.width() - (mSourceWindowClipInsets.right * progress);
        mCurrentClipRectF.bottom =
                mSourceStackBounds.height() - (mSourceWindowClipInsets.bottom * progress);
    }

    public RectF getCurrentRectWithInsets() {
        mTmpMatrix.mapRect(mCurrentRectWithInsets, mCurrentClipRectF);
        return mCurrentRectWithInsets;
    }

    public static void applySurfaceParams(@Nullable SyncRtSurfaceTransactionApplierCompat
            syncTransactionApplier, SurfaceParams[] params) {
        if (syncTransactionApplier != null) {
            syncTransactionApplier.scheduleApply(params);
        } else {
            TransactionCompat t = new TransactionCompat();
            for (SurfaceParams param : params) {
                SyncRtSurfaceTransactionApplierCompat.applyParams(t, param);
            }
            t.setEarlyWakeup();
            t.apply();
        }
    }

    public void setTaskAlphaCallback(TargetAlphaProvider callback) {
        mTaskAlphaCallback = callback;
    }

    public void setBaseAlphaCallback(TargetAlphaProvider callback) {
        mBaseAlphaCallback = callback;
    }

    public void fromTaskThumbnailView(TaskThumbnailView ttv, RecentsView rv,
            @Nullable RemoteAnimationTargetCompat target) {
        BaseDraggingActivity activity = BaseDraggingActivity.fromContext(ttv.getContext());
        BaseDragLayer dl = activity.getDragLayer();

        int[] pos = new int[2];
        dl.getLocationOnScreen(pos);
        mHomeStackBounds.set(0, 0, dl.getWidth(), dl.getHeight());
        mHomeStackBounds.offset(pos[0], pos[1]);

        if (target != null) {
            updateSourceStack(target);
        } else  if (rv.shouldUseMultiWindowTaskSizeStrategy()) {
            updateStackBoundsToMultiWindowTaskSize(activity);
        } else {
            mSourceStackBounds.set(mHomeStackBounds);
            Rect fallback = dl.getInsets();
            mSourceInsets.set(ttv.getInsets(fallback));
        }

        Rect targetRect = new Rect();
        dl.getDescendantRectRelativeToSelf(ttv, targetRect);
        updateTargetRect(targetRect);

        if (target == null) {
            // Transform the clip relative to the target rect. Only do this in the case where we
            // aren't applying the insets to the app windows (where the clip should be in target app
            // space)
            float scale = mTargetRect.width() / mSourceRect.width();
            mSourceWindowClipInsets.left = mSourceWindowClipInsets.left * scale;
            mSourceWindowClipInsets.top = mSourceWindowClipInsets.top * scale;
            mSourceWindowClipInsets.right = mSourceWindowClipInsets.right * scale;
            mSourceWindowClipInsets.bottom = mSourceWindowClipInsets.bottom * scale;
        }
    }

    private void updateStackBoundsToMultiWindowTaskSize(BaseDraggingActivity activity) {
        SystemUiProxy proxy = SystemUiProxy.INSTANCE.get(activity);
        if (proxy.isActive()) {
            mSourceStackBounds.set(proxy.getNonMinimizedSplitScreenSecondaryBounds());
            return;
        }

        // Assume that the task size is half screen size (minus the insets and the divider size)
        DeviceProfile fullDp = activity.getDeviceProfile().getFullScreenProfile();
        // Use availableWidthPx and availableHeightPx instead of widthPx and heightPx to
        // account for system insets
        int taskWidth = fullDp.availableWidthPx;
        int taskHeight = fullDp.availableHeightPx;
        int halfDividerSize = activity.getResources()
                .getDimensionPixelSize(R.dimen.multi_window_task_divider_size) / 2;

        Rect insets = new Rect();
        WindowManagerWrapper.getInstance().getStableInsets(insets);
        if (fullDp.isLandscape) {
            taskWidth = taskWidth / 2 - halfDividerSize;
        } else {
            taskHeight = taskHeight / 2 - halfDividerSize;
        }

        // Align the task to bottom left/right edge (closer to nav bar).
        int left = activity.getDeviceProfile().isSeascape() ? insets.left
                : (insets.left + fullDp.availableWidthPx - taskWidth);
        mSourceStackBounds.set(0, 0, taskWidth, taskHeight);
        mSourceStackBounds.offset(left, insets.top + fullDp.availableHeightPx - taskHeight);
    }

    public RectF getTargetRect() {
        return mTargetRect;
    }

    public float getCurrentCornerRadius() {
        return mCurrentCornerRadius;
    }

    public interface TargetAlphaProvider {
        float getAlpha(RemoteAnimationTargetCompat target, float expectedAlpha);
    }

    public static class TransformParams {
        private float mProgress;
        private float mOffset;
        private float mOffsetScale;
        private @Nullable RectF mCurrentRect;
        private float mTargetAlpha;
        private float mCornerRadius;
        private boolean mLauncherOnTop;
        private RemoteAnimationTargets mTargetSet;
        private SyncRtSurfaceTransactionApplierCompat mSyncTransactionApplier;

        public TransformParams() {
            mProgress = 0;
            mOffset = 0;
            mOffsetScale = 1;
            mCurrentRect = null;
            mTargetAlpha = 1;
            mCornerRadius = -1;
            mLauncherOnTop = false;
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
         * If {@link #mCurrentRect} is null (i.e. {@link #setCurrentRect(RectF)} hasn't overridden
         * the default), then offset the current rect by this amount after computing the rect based
         * on {@link #mProgress}.
         */
        public TransformParams setOffset(float offset) {
            mOffset = offset;
            return this;
        }

        /**
         * If {@link #mCurrentRect} is null (i.e. {@link #setCurrentRect(RectF)} hasn't overridden
         * the default), then scale the current rect by this amount after computing the rect based
         * on {@link #mProgress}.
         */
        public TransformParams setOffsetScale(float offsetScale) {
            mOffsetScale = offsetScale;
            return this;
        }

        /**
         * If true, sets the crop = null and layer = Integer.MAX_VALUE for targets that don't match
         * {@link #mTargetSet}.targetMode. (Currently only does this when live tiles are enabled.)
         */
        public TransformParams setLauncherOnTop(boolean launcherOnTop) {
            mLauncherOnTop = launcherOnTop;
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

        // Pubic getters so outside packages can read the values.

        public float getProgress() {
            return mProgress;
        }

        public float getOffset() {
            return mOffset;
        }

        public float getOffsetScale() {
            return mOffsetScale;
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

        public boolean isLauncherOnTop() {
            return mLauncherOnTop;
        }

        public RemoteAnimationTargets getTargetSet() {
            return mTargetSet;
        }

        public SyncRtSurfaceTransactionApplierCompat getSyncTransactionApplier() {
            return mSyncTransactionApplier;
        }
    }
}
