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
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.ISystemUiProxy;
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
public class ClipAnimationHelper {

    // The bounds of the source app in device coordinates
    private final Rect mSourceStackBounds = new Rect();
    // The insets of the source app
    private final Rect mSourceInsets = new Rect();
    // The source app bounds with the source insets applied, in the source app window coordinates
    private final RectF mSourceRect = new RectF();
    // The bounds of the task view in launcher window coordinates
    private final RectF mTargetRect = new RectF();
    // The insets to be used for clipping the app window, which can be larger than mSourceInsets
    // if the aspect ratio of the target is smaller than the aspect ratio of the source rect. In
    // app window coordinates.
    private final RectF mSourceWindowClipInsets = new RectF();
    // The insets to be used for clipping the app window. For live tile, we don't transform the clip
    // relative to the target rect.
    private final RectF mSourceWindowClipInsetsForLiveTile = new RectF();

    // The bounds of launcher (not including insets) in device coordinates
    public final Rect mHomeStackBounds = new Rect();

    // The clip rect in source app window coordinates
    private final RectF mClipRectF = new RectF();
    private final RectFEvaluator mRectFEvaluator = new RectFEvaluator();
    private final Matrix mTmpMatrix = new Matrix();
    private final Rect mTmpRect = new Rect();
    private final RectF mTmpRectF = new RectF();
    private final RectF mCurrentRectWithInsets = new RectF();
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

    // Whether to boost the opening animation target layers, or the closing
    private int mBoostModeTargetLayers = -1;

    private TargetAlphaProvider mTaskAlphaCallback = (t, a) -> a;
    private TargetAlphaProvider mBaseAlphaCallback = (t, a) -> 1;

    public ClipAnimationHelper(Context context) {
        mWindowCornerRadius = getWindowCornerRadius(context.getResources());
        mSupportsRoundedCornersOnWindows = supportsRoundedCornersOnWindows(context.getResources());
        mTaskCornerRadius = TaskCornerRadius.get(context);
        mUseRoundedCornersOnWindows = mSupportsRoundedCornersOnWindows;
    }

    private void updateSourceStack(RemoteAnimationTargetCompat target) {
        mSourceInsets.set(target.contentInsets);
        mSourceStackBounds.set(target.sourceContainerBounds);

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
        Utilities.scaleRectFAboutCenter(scaledTargetRect,
                mSourceRect.width() / mTargetRect.width());
        scaledTargetRect.offsetTo(mSourceRect.left, mSourceRect.top);
        mSourceWindowClipInsets.set(
                Math.max(scaledTargetRect.left, 0),
                Math.max(scaledTargetRect.top, 0),
                Math.max(mSourceStackBounds.width() - scaledTargetRect.right, 0),
                Math.max(mSourceStackBounds.height() - scaledTargetRect.bottom, 0));
        mSourceWindowClipInsetsForLiveTile.set(mSourceWindowClipInsets);
        mSourceRect.set(scaledTargetRect);
    }

    public void prepareAnimation(DeviceProfile dp, boolean isOpening) {
        mBoostModeTargetLayers = isOpening ? MODE_OPENING : MODE_CLOSING;
        mUseRoundedCornersOnWindows = mSupportsRoundedCornersOnWindows && !dp.isMultiWindowMode;
    }

    public RectF applyTransform(RemoteAnimationTargetSet targetSet, TransformParams params) {
        return applyTransform(targetSet, params, true /* launcherOnTop */);
    }

    public RectF applyTransform(RemoteAnimationTargetSet targetSet, TransformParams params,
            boolean launcherOnTop) {
        float progress = params.progress;
        if (params.currentRect == null) {
            RectF currentRect;
            mTmpRectF.set(mTargetRect);
            Utilities.scaleRectFAboutCenter(mTmpRectF, params.offsetScale);
            currentRect = mRectFEvaluator.evaluate(progress, mSourceRect, mTmpRectF);
            currentRect.offset(params.offsetX, 0);

            // Don't clip past progress > 1.
            progress = Math.min(1, progress);
            final RectF sourceWindowClipInsets = params.forLiveTile
                    ? mSourceWindowClipInsetsForLiveTile : mSourceWindowClipInsets;
            mClipRectF.left = sourceWindowClipInsets.left * progress;
            mClipRectF.top = sourceWindowClipInsets.top * progress;
            mClipRectF.right =
                    mSourceStackBounds.width() - (sourceWindowClipInsets.right * progress);
            mClipRectF.bottom =
                    mSourceStackBounds.height() - (sourceWindowClipInsets.bottom * progress);
            params.setCurrentRectAndTargetAlpha(currentRect, 1);
        }

        SurfaceParams[] surfaceParams = new SurfaceParams[targetSet.unfilteredApps.length];
        for (int i = 0; i < targetSet.unfilteredApps.length; i++) {
            RemoteAnimationTargetCompat app = targetSet.unfilteredApps[i];
            mTmpMatrix.setTranslate(app.position.x, app.position.y);
            Rect crop = mTmpRect;
            crop.set(app.sourceContainerBounds);
            crop.offsetTo(0, 0);
            float alpha;
            int layer = RemoteAnimationProvider.getLayer(app, mBoostModeTargetLayers);
            float cornerRadius = 0f;
            float scale = Math.max(params.currentRect.width(), mTargetRect.width()) / crop.width();
            if (app.mode == targetSet.targetMode) {
                alpha = mTaskAlphaCallback.getAlpha(app, params.targetAlpha);
                if (app.activityType != RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                    mTmpMatrix.setRectToRect(mSourceRect, params.currentRect, ScaleToFit.FILL);
                    mTmpMatrix.postTranslate(app.position.x, app.position.y);
                    mClipRectF.roundOut(crop);
                    if (mSupportsRoundedCornersOnWindows) {
                        if (params.cornerRadius > -1) {
                            cornerRadius = params.cornerRadius;
                            scale = params.currentRect.width() / crop.width();
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
                } else if (targetSet.hasRecents) {
                    // If home has a different target then recents, reverse anim the
                    // home target.
                    alpha = 1 - (progress * params.targetAlpha);
                }
            } else {
                alpha = mBaseAlphaCallback.getAlpha(app, progress);
                if (ENABLE_QUICKSTEP_LIVE_TILE.get() && launcherOnTop) {
                    crop = null;
                    layer = Integer.MAX_VALUE;
                }
            }

            // Since radius is in Surface space, but we draw the rounded corners in screen space, we
            // have to undo the scale.
            surfaceParams[i] = new SurfaceParams(app.leash, alpha, mTmpMatrix, crop, layer,
                    cornerRadius / scale);
        }
        applySurfaceParams(params.syncTransactionApplier, surfaceParams);
        return params.currentRect;
    }

    public RectF getCurrentRectWithInsets() {
        mTmpMatrix.mapRect(mCurrentRectWithInsets, mClipRectF);
        return mCurrentRectWithInsets;
    }

    private void applySurfaceParams(@Nullable SyncRtSurfaceTransactionApplierCompat
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

    public void fromTaskThumbnailView(TaskThumbnailView ttv, RecentsView rv) {
        fromTaskThumbnailView(ttv, rv, null);
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

    /**
     * Compute scale and translation y such that the specified task view fills the screen.
     */
    public ClipAnimationHelper updateForFullscreenOverview(TaskView v) {
        TaskThumbnailView thumbnailView = v.getThumbnail();
        RecentsView recentsView = v.getRecentsView();
        fromTaskThumbnailView(thumbnailView, recentsView);
        Rect taskSize = new Rect();
        recentsView.getTaskSize(taskSize);
        updateTargetRect(taskSize);
        return this;
    }

    /**
     * @return The source rect's scale and translation relative to the target rect.
     */
    public LauncherState.ScaleAndTranslation getScaleAndTranslation() {
        float scale = mSourceRect.width() / mTargetRect.width();
        float translationY = mSourceRect.centerY() - mSourceRect.top - mTargetRect.centerY();
        return new LauncherState.ScaleAndTranslation(scale, 0, translationY);
    }

    private void updateStackBoundsToMultiWindowTaskSize(BaseDraggingActivity activity) {
        ISystemUiProxy sysUiProxy = RecentsModel.INSTANCE.get(activity).getSystemUiProxy();
        if (sysUiProxy != null) {
            try {
                mSourceStackBounds.set(sysUiProxy.getNonMinimizedSplitScreenSecondaryBounds());
                return;
            } catch (RemoteException e) {
                // Use half screen size
            }
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
        float progress;
        public float offsetX;
        public float offsetScale;
        @Nullable RectF currentRect;
        float targetAlpha;
        boolean forLiveTile;
        float cornerRadius;

        SyncRtSurfaceTransactionApplierCompat syncTransactionApplier;

        public TransformParams() {
            progress = 0;
            offsetX = 0;
            offsetScale = 1;
            currentRect = null;
            targetAlpha = 0;
            forLiveTile = false;
            cornerRadius = -1;
        }

        public TransformParams setProgress(float progress) {
            this.progress = progress;
            this.currentRect = null;
            return this;
        }

        public float getProgress() {
            return progress;
        }

        public TransformParams setCornerRadius(float cornerRadius) {
            this.cornerRadius = cornerRadius;
            return this;
        }

        public TransformParams setCurrentRectAndTargetAlpha(RectF currentRect, float targetAlpha) {
            this.currentRect = currentRect;
            this.targetAlpha = targetAlpha;
            return this;
        }

        public TransformParams setOffsetX(float offsetX) {
            this.offsetX = offsetX;
            return this;
        }

        public TransformParams setOffsetScale(float offsetScale) {
            this.offsetScale = offsetScale;
            return this;
        }

        public TransformParams setForLiveTile(boolean forLiveTile) {
            this.forLiveTile = forLiveTile;
            return this;
        }

        public TransformParams setSyncTransactionApplier(
                SyncRtSurfaceTransactionApplierCompat applier) {
            this.syncTransactionApplier = applier;
            return this;
        }
    }
}

