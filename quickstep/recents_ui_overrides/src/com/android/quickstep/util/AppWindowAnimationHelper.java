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

import static com.android.launcher3.Utilities.boundToRange;
import static com.android.launcher3.Utilities.mapRange;
import static com.android.systemui.shared.system.QuickStepContract.getWindowCornerRadius;
import static com.android.systemui.shared.system.QuickStepContract.supportsRoundedCornersOnWindows;

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
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.systemui.shared.recents.utilities.RectFEvaluator;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Utility class to handle window clip animation
 */
@TargetApi(Build.VERSION_CODES.P)
public class AppWindowAnimationHelper implements TransformParams.BuilderProxy {

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
        params.applySurfaceParams(surfaceParams);
        return mCurrentRect;
    }

    /**
     * Updates this AppWindowAnimationHelper's state based on the given TransformParams, and returns
     * the SurfaceParams to apply via {@link SyncRtSurfaceTransactionApplierCompat#applyParams}.
     */
    public SurfaceParams[] computeSurfaceParams(TransformParams params) {
        if (params.getTargetSet() == null) {
            return null;
        }

        updateCurrentRect(params);
        return params.createSurfaceParams(this);
    }

    @Override
    public void onBuildParams(Builder builder, RemoteAnimationTargetCompat app,
            int targetMode, TransformParams params) {
        Rect crop = mTmpRect;
        crop.set(app.screenSpaceBounds);
        crop.offsetTo(0, 0);
        float cornerRadius = 0f;
        float scale = Math.max(mCurrentRect.width(), mTargetRect.width()) / crop.width();
        if (app.mode == targetMode
                && app.activityType != RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
            mTmpMatrix.setRectToRect(mSourceRect, mCurrentRect, ScaleToFit.FILL);
            if (app.localBounds != null) {
                mTmpMatrix.postTranslate(app.localBounds.left, app.localBounds.top);
            } else {
                mTmpMatrix.postTranslate(app.position.x, app.position.y);
            }
            mCurrentClipRectF.roundOut(crop);
            if (mSupportsRoundedCornersOnWindows) {
                if (params.getCornerRadius() > -1) {
                    cornerRadius = params.getCornerRadius();
                    scale = mCurrentRect.width() / crop.width();
                } else {
                    float windowCornerRadius = mUseRoundedCornersOnWindows
                            ? mWindowCornerRadius : 0;
                    cornerRadius = mapRange(boundToRange(params.getProgress(), 0, 1),
                            windowCornerRadius, mTaskCornerRadius);
                }
                mCurrentCornerRadius = cornerRadius;
            }

            builder.withMatrix(mTmpMatrix)
                    .withWindowCrop(crop)
                    // Since radius is in Surface space, but we draw the rounded corners in screen
                    // space, we have to undo the scale
                    .withCornerRadius(cornerRadius / scale);

        }
    }

    public RectF updateCurrentRect(TransformParams params) {
        if (params.getCurrentRect() != null) {
            mCurrentRect.set(params.getCurrentRect());
        } else {
            mTmpRectF.set(mTargetRect);
            mCurrentRect.set(mRectFEvaluator.evaluate(
                    params.getProgress(), mSourceRect, mTmpRectF));
        }

        updateClipRect(params);
        return mCurrentRect;
    }

    private void updateClipRect(TransformParams params) {
        // Don't clip past progress > 1.
        float progress = Math.min(1, params.getProgress());
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

}
