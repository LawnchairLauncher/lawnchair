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

import static android.view.Surface.ROTATION_0;

import static com.android.launcher3.states.RotationHelper.deltaRotation;
import static com.android.launcher3.touch.PagedOrientationHandler.MATRIX_POST_TRANSLATE;
import static com.android.quickstep.util.AppWindowAnimationHelper.applySurfaceParams;
import static com.android.quickstep.util.RecentsOrientedState.isFixedRotationTransformEnabled;
import static com.android.quickstep.util.RecentsOrientedState.postDisplayRotation;
import static com.android.systemui.shared.system.WindowManagerWrapper.WINDOWING_MODE_FULLSCREEN;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.util.AppWindowAnimationHelper.TargetAlphaProvider;
import com.android.quickstep.util.AppWindowAnimationHelper.TransformParams;
import com.android.quickstep.views.RecentsView.ScrollState;
import com.android.quickstep.views.TaskThumbnailView.PreviewPositionHelper;
import com.android.quickstep.views.TaskView;
import com.android.quickstep.views.TaskView.FullscreenDrawParams;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

/**
 * A utility class which emulates the layout behavior of TaskView and RecentsView
 */
public class TaskViewSimulator {

    private final Rect mTmpCropRect = new Rect();
    private final RectF mTempRectF = new RectF();
    private final float[] mTempPoint = new float[2];

    private final RecentsOrientedState mOrientationState;
    private final Context mContext;
    private final WindowSizeStrategy mSizeStrategy;

    private final Rect mTaskRect = new Rect();
    private final PointF mPivot = new PointF();
    private DeviceProfile mDp;

    private final Matrix mMatrix = new Matrix();
    private RemoteAnimationTargetCompat mRunningTarget;
    private RecentsAnimationTargets mAllTargets;

    private TargetAlphaProvider mTaskAlphaCallback = (t, a) -> a;

    // Thumbnail view properties
    private final Rect mThumbnailPosition = new Rect();
    private final ThumbnailData mThumbnailData = new ThumbnailData();
    private final PreviewPositionHelper mPositionHelper;
    private final Matrix mInversePositionMatrix = new Matrix();

    // TaskView properties
    private final FullscreenDrawParams mCurrentFullscreenParams;
    private float mCurveScale = 1;

    // RecentsView properties
    public final AnimatedFloat recentsViewScale = new AnimatedFloat(() -> { });
    public final AnimatedFloat fullScreenProgress = new AnimatedFloat(() -> { });
    private final ScrollState mScrollState = new ScrollState();
    private final int mPageSpacing;

    // Cached calculations
    private boolean mLayoutValid = false;
    private boolean mScrollValid = false;

    public TaskViewSimulator(Context context, WindowSizeStrategy sizeStrategy) {
        mContext = context;
        mSizeStrategy = sizeStrategy;
        mPositionHelper = new PreviewPositionHelper(context);

        mOrientationState = new RecentsOrientedState(context, sizeStrategy, i -> { });
        // We do not need to attach listeners as the simulator is created just for the gesture
        // duration, and any settings are unlikely to change during this
        mOrientationState.initWithoutListeners();

        mCurrentFullscreenParams = new FullscreenDrawParams(context);
        mPageSpacing = context.getResources().getDimensionPixelSize(R.dimen.recents_page_spacing);
    }

    /**
     * Sets the device profile for the current state
     */
    public void setDp(DeviceProfile dp) {
        mDp = dp;
        mOrientationState.setMultiWindowMode(mDp.isMultiWindowMode);
        mLayoutValid = false;
    }

    /**
     * @see com.android.quickstep.views.RecentsView#setLayoutRotation(int, int)
     */
    public void setLayoutRotation(int touchRotation, int displayRotation) {
        int launcherRotation;
        if (!mOrientationState.isMultipleOrientationSupportedByDevice()
                || mOrientationState.isHomeRotationAllowed()) {
            launcherRotation = displayRotation;
        } else {
            launcherRotation = ROTATION_0;
        }

        mOrientationState.update(touchRotation, displayRotation, launcherRotation);
        mLayoutValid = false;
    }

    /**
     * @see com.android.quickstep.views.RecentsView#FULLSCREEN_PROGRESS
     */
    public float getFullScreenScale() {
        if (mDp == null) {
            return 1;
        }
        mSizeStrategy.calculateTaskSize(mContext, mDp, mTaskRect);
        return mOrientationState.getFullScreenScaleAndPivot(mTaskRect, mDp, mPivot);
    }

    /**
     * Sets the targets which the simulator will control
     */
    public void setPreview(
            RemoteAnimationTargetCompat runningTarget, RecentsAnimationTargets allTargets) {
        mRunningTarget = runningTarget;
        mAllTargets = allTargets;

        mThumbnailData.insets.set(mRunningTarget.contentInsets);
        // TODO: What is this?
        mThumbnailData.windowingMode = WINDOWING_MODE_FULLSCREEN;

        mThumbnailPosition.set(runningTarget.screenSpaceBounds);
        // TODO: Should sourceContainerBounds already have this offset?
        mThumbnailPosition.offset(-mRunningTarget.position.x, -mRunningTarget.position.y);
        mLayoutValid = false;
    }

    /**
     * Updates the scroll for RecentsView
     */
    public void setScroll(int scroll) {
        if (mScrollState.scroll != scroll) {
            mScrollState.scroll = scroll;
            mScrollValid = false;
        }
    }

    /**
     * Sets an alternate function which can be used to control the alpha
     */
    public void setTaskAlphaCallback(TargetAlphaProvider callback) {
        mTaskAlphaCallback = callback;
    }

    /**
     * Applies the target to the previously set parameters
     */
    public void apply(TransformParams params) {
        if (mDp == null || mRunningTarget == null) {
            return;
        }
        if (!mLayoutValid) {
            mLayoutValid = true;

            getFullScreenScale();
            mThumbnailData.rotation = isFixedRotationTransformEnabled(mContext)
                    ? mOrientationState.getDisplayRotation() : mPositionHelper.getCurrentRotation();

            mPositionHelper.updateThumbnailMatrix(mThumbnailPosition, mThumbnailData,
                    mTaskRect.width(), mTaskRect.height(), mDp);

            mPositionHelper.getMatrix().invert(mInversePositionMatrix);

            PagedOrientationHandler poh = mOrientationState.getOrientationHandler();
            mScrollState.halfPageSize =
                    poh.getPrimaryValue(mTaskRect.width(), mTaskRect.height()) / 2;
            mScrollState.halfScreenSize = poh.getPrimaryValue(mDp.widthPx, mDp.heightPx) / 2;
            mScrollValid = false;
        }


        if (!mScrollValid) {
            mScrollValid = true;
            int start = mOrientationState.getOrientationHandler()
                    .getPrimaryValue(mTaskRect.left, mTaskRect.top);
            mScrollState.screenCenter = start + mScrollState.scroll + mScrollState.halfPageSize;
            mScrollState.updateInterpolation(start, mPageSpacing);
            mCurveScale = TaskView.getCurveScaleForInterpolation(mScrollState.linearInterpolation);
        }

        float progress = Utilities.boundToRange(fullScreenProgress.value, 0, 1);
        mCurrentFullscreenParams.setProgress(
                progress, recentsViewScale.value, mTaskRect.width(), mDp, mPositionHelper);

        // Apply thumbnail matrix
        RectF insets = mCurrentFullscreenParams.mCurrentDrawnInsets;
        float scale = mCurrentFullscreenParams.mScale;
        float taskWidth = mTaskRect.width();
        float taskHeight = mTaskRect.height();

        mMatrix.set(mPositionHelper.getMatrix());
        mMatrix.postScale(scale, scale);
        mMatrix.postTranslate(insets.left, insets.top);

        // Apply TaskView matrix: scale, translate, scroll
        mMatrix.postScale(mCurveScale, mCurveScale, taskWidth / 2, taskHeight / 2);
        mMatrix.postTranslate(mTaskRect.left, mTaskRect.top);
        mOrientationState.getOrientationHandler().set(
                mMatrix, MATRIX_POST_TRANSLATE, mScrollState.scroll);

        // Apply recensView matrix
        mMatrix.postScale(recentsViewScale.value, recentsViewScale.value, mPivot.x, mPivot.y);
        postDisplayRotation(deltaRotation(
                mOrientationState.getLauncherRotation(), mOrientationState.getDisplayRotation()),
                mDp.widthPx, mDp.heightPx, mMatrix);
        mMatrix.postTranslate(mDp.windowX - mRunningTarget.position.x,
                mDp.windowY - mRunningTarget.position.y);

        // Crop rect is the inverse of thumbnail matrix
        mTempRectF.set(-insets.left, -insets.top,
                taskWidth + insets.right, taskHeight + insets.bottom);
        mInversePositionMatrix.mapRect(mTempRectF);
        mTempRectF.roundOut(mTmpCropRect);

        SurfaceParams[] surfaceParams = new SurfaceParams[mAllTargets.unfilteredApps.length];
        for (int i = 0; i < mAllTargets.unfilteredApps.length; i++) {
            RemoteAnimationTargetCompat app = mAllTargets.unfilteredApps[i];
            SurfaceParams.Builder builder = new SurfaceParams.Builder(app.leash);

            if (app.mode == mAllTargets.targetMode) {
                float alpha = mTaskAlphaCallback.getAlpha(app, params.getTargetAlpha());
                if (app.activityType != RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                    // Fade out Assistant overlay.
                    if (app.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_ASSISTANT
                            && app.isNotInRecents) {
                        alpha = Interpolators.ACCEL_2.getInterpolation(fullScreenProgress.value);
                    }

                    builder.withAlpha(alpha)
                            .withMatrix(mMatrix)
                            .withWindowCrop(mTmpCropRect)
                            .withCornerRadius(getCurrentCornerRadius());
                } else if (params.getTargetSet().hasRecents) {
                    // If home has a different target then recents, reverse anim the home target.
                    builder.withAlpha(fullScreenProgress.value * params.getTargetAlpha());
                }
            } else {
                builder.withAlpha(1);
            }
            surfaceParams[i] = builder.build();
        }

        applySurfaceParams(params.getSyncTransactionApplier(), surfaceParams);
    }

    /**
     * Returns the corner radius that should be applied to the target so that it matches the
     * TaskView
     */
    public float getCurrentCornerRadius() {
        float visibleRadius = mCurrentFullscreenParams.mCurrentDrawnCornerRadius;
        mTempPoint[0] = visibleRadius;
        mTempPoint[1] = 0;
        mInversePositionMatrix.mapVectors(mTempPoint);

        // Ideally we should use square-root. This is an optimization as one of the dimension is 0.
        return Math.max(Math.abs(mTempPoint[0]), Math.abs(mTempPoint[1]));
    }
}
