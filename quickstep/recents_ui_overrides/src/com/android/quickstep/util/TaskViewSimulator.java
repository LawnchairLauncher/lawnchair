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

import static com.android.launcher3.states.RotationHelper.deltaRotation;
import static com.android.launcher3.touch.PagedOrientationHandler.MATRIX_POST_TRANSLATE;
import static com.android.quickstep.util.RecentsOrientedState.postDisplayRotation;
import static com.android.systemui.shared.system.WindowManagerWrapper.WINDOWING_MODE_FULLSCREEN;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.IntProperty;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.BaseActivityInterface;
import com.android.quickstep.views.RecentsView.ScrollState;
import com.android.quickstep.views.TaskThumbnailView.PreviewPositionHelper;
import com.android.quickstep.views.TaskView;
import com.android.quickstep.views.TaskView.FullscreenDrawParams;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams.Builder;

/**
 * A utility class which emulates the layout behavior of TaskView and RecentsView
 */
public class TaskViewSimulator implements TransformParams.BuilderProxy {

    public static final IntProperty<TaskViewSimulator> SCROLL =
            new IntProperty<TaskViewSimulator>("scroll") {
        @Override
        public void setValue(TaskViewSimulator simulator, int i) {
            simulator.setScroll(i);
        }

        @Override
        public Integer get(TaskViewSimulator simulator) {
            return simulator.mScrollState.scroll;
        }
    };

    private final Rect mTmpCropRect = new Rect();
    private final RectF mTempRectF = new RectF();
    private final float[] mTempPoint = new float[2];

    private final RecentsOrientedState mOrientationState;
    private final Context mContext;
    private final BaseActivityInterface mSizeStrategy;

    private final Rect mTaskRect = new Rect();
    private final PointF mPivot = new PointF();
    private DeviceProfile mDp;

    private final Matrix mMatrix = new Matrix();
    private final Point mRunningTargetWindowPosition = new Point();

    // Thumbnail view properties
    private final Rect mThumbnailPosition = new Rect();
    private final ThumbnailData mThumbnailData = new ThumbnailData();
    private final PreviewPositionHelper mPositionHelper = new PreviewPositionHelper();
    private final Matrix mInversePositionMatrix = new Matrix();

    // TaskView properties
    private final FullscreenDrawParams mCurrentFullscreenParams;
    private float mCurveScale = 1;

    // RecentsView properties
    public final AnimatedFloat recentsViewScale = new AnimatedFloat();
    public final AnimatedFloat fullScreenProgress = new AnimatedFloat();
    public final AnimatedFloat recentsViewSecondaryTranslation = new AnimatedFloat();
    private final ScrollState mScrollState = new ScrollState();

    // Cached calculations
    private boolean mLayoutValid = false;
    private boolean mScrollValid = false;

    public TaskViewSimulator(Context context, BaseActivityInterface sizeStrategy) {
        mContext = context;
        mSizeStrategy = sizeStrategy;

        mOrientationState = new RecentsOrientedState(context, sizeStrategy, i -> { });
        mOrientationState.setGestureActive(true);

        mCurrentFullscreenParams = new FullscreenDrawParams(context);
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
        mOrientationState.update(touchRotation, displayRotation);
        mLayoutValid = false;
    }

    /**
     * @see com.android.quickstep.views.RecentsView#onConfigurationChanged(Configuration)
     */
    public void setRecentsRotation(int recentsRotation) {
        mOrientationState.setRecentsRotation(recentsRotation);
        mLayoutValid = false;
    }

    /**
     * @see com.android.quickstep.views.RecentsView#FULLSCREEN_PROGRESS
     */
    public float getFullScreenScale() {
        if (mDp == null) {
            return 1;
        }
        mSizeStrategy.calculateTaskSize(mContext, mDp, mTaskRect,
                mOrientationState.getOrientationHandler());
        return mOrientationState.getFullScreenScaleAndPivot(mTaskRect, mDp, mPivot);
    }

    /**
     * Sets the targets which the simulator will control
     */
    public void setPreview(RemoteAnimationTargetCompat runningTarget) {
        setPreviewBounds(runningTarget.screenSpaceBounds, runningTarget.contentInsets);
        mRunningTargetWindowPosition.set(runningTarget.screenSpaceBounds.left,
                runningTarget.screenSpaceBounds.top);
    }

    /**
     * Sets the targets which the simulator will control
     */
    public void setPreviewBounds(Rect bounds, Rect insets) {
        mThumbnailData.insets.set(insets);
        // TODO: What is this?
        mThumbnailData.windowingMode = WINDOWING_MODE_FULLSCREEN;

        mThumbnailPosition.set(bounds);
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
     * Adds animation for all the components corresponding to transition from an app to overview
     */
    public void addAppToOverviewAnim(PendingAnimation pa, TimeInterpolator interpolator) {
        pa.addFloat(fullScreenProgress, AnimatedFloat.VALUE, 1, 0, interpolator);
        pa.addFloat(recentsViewScale, AnimatedFloat.VALUE, getFullScreenScale(), 1, interpolator);
    }

    /**
     * Returns the current clipped/visible window bounds in the window coordinate space
     */
    public RectF getCurrentCropRect() {
        // Crop rect is the inverse of thumbnail matrix
        RectF insets = mCurrentFullscreenParams.mCurrentDrawnInsets;
        mTempRectF.set(-insets.left, -insets.top,
                mTaskRect.width() + insets.right, mTaskRect.height() + insets.bottom);
        mInversePositionMatrix.mapRect(mTempRectF);
        return mTempRectF;
    }

    public RecentsOrientedState getOrientationState() {
        return mOrientationState;
    }

    /**
     * Returns the current transform applied to the window
     */
    public Matrix getCurrentMatrix() {
        return mMatrix;
    }

    /**
     * Applies the rotation on the matrix to so that it maps from launcher coordinate space to
     * window coordinate space.
     */
    public void applyWindowToHomeRotation(Matrix matrix) {
        mMatrix.postTranslate(mDp.windowX, mDp.windowY);
        postDisplayRotation(deltaRotation(
                mOrientationState.getRecentsActivityRotation(),
                mOrientationState.getDisplayRotation()),
                mDp.widthPx, mDp.heightPx, matrix);
        matrix.postTranslate(-mRunningTargetWindowPosition.x, -mRunningTargetWindowPosition.y);
    }

    /**
     * Applies the target to the previously set parameters
     */
    public void apply(TransformParams params) {
        if (mDp == null || mThumbnailPosition.isEmpty()) {
            return;
        }
        if (!mLayoutValid) {
            mLayoutValid = true;

            getFullScreenScale();
            mThumbnailData.rotation = mOrientationState.getDisplayRotation();

            mPositionHelper.updateThumbnailMatrix(
                    mThumbnailPosition, mThumbnailData,
                    mTaskRect.width(), mTaskRect.height(),
                    mDp, mOrientationState.getRecentsActivityRotation());
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
            mScrollState.updateInterpolation(start);
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
        mMatrix.postTranslate(insets.left, insets.top);
        mMatrix.postScale(scale, scale);

        // Apply TaskView matrix: translate, scale, scroll
        mMatrix.postTranslate(mTaskRect.left, mTaskRect.top);
        mMatrix.postScale(mCurveScale, mCurveScale, taskWidth / 2, taskHeight / 2);
        mOrientationState.getOrientationHandler().set(
                mMatrix, MATRIX_POST_TRANSLATE, mScrollState.scroll);

        // Apply RecentsView matrix
        mMatrix.postScale(recentsViewScale.value, recentsViewScale.value, mPivot.x, mPivot.y);
        mOrientationState.getOrientationHandler().setSecondary(mMatrix, MATRIX_POST_TRANSLATE,
                recentsViewSecondaryTranslation.value);
        applyWindowToHomeRotation(mMatrix);

        // Crop rect is the inverse of thumbnail matrix
        mTempRectF.set(-insets.left, -insets.top,
                taskWidth + insets.right, taskHeight + insets.bottom);
        mInversePositionMatrix.mapRect(mTempRectF);
        mTempRectF.roundOut(mTmpCropRect);

        params.applySurfaceParams(params.createSurfaceParams(this));
    }

    @Override
    public void onBuildTargetParams(
            Builder builder, RemoteAnimationTargetCompat app, TransformParams params) {
        builder.withMatrix(mMatrix)
                .withWindowCrop(mTmpCropRect)
                .withCornerRadius(getCurrentCornerRadius());
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
