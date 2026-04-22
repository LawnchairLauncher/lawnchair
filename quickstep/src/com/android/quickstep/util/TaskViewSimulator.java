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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static com.android.launcher3.states.RotationHelper.deltaRotation;
import static com.android.launcher3.touch.PagedOrientationHandler.MATRIX_POST_TRANSLATE;
import static com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview;
import static com.android.quickstep.util.RecentsOrientedState.postDisplayRotation;
import static com.android.quickstep.util.RecentsOrientedState.preDisplayRotation;
import static com.android.wm.shell.Flags.enableFlexibleTwoAppSplit;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.BaseActivityInterface;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.DesktopFullscreenDrawParams;
import com.android.quickstep.FullscreenDrawParams;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper;
import com.android.wm.shell.shared.split.SplitBounds;
import com.android.wm.shell.shared.split.SplitScreenConstants;

/**
 * A utility class which emulates the layout behavior of TaskView and RecentsView
 */
public class TaskViewSimulator implements TransformParams.BuilderProxy {

    private static final String TAG = "TaskViewSimulator";
    private static final boolean DEBUG = false;

    private final Rect mTmpCropRect = new Rect();
    private final RectF mTempRectF = new RectF();
    private final float[] mTempPoint = new float[2];

    private final Context mContext;
    private final BaseContainerInterface mSizeStrategy;

    @NonNull
    private RecentsOrientedState mOrientationState;
    private final boolean mIsRecentsRtl;

    private final Rect mTaskRect = new Rect();
    private final Rect mFullTaskSize = new Rect();
    private final Rect mCarouselTaskSize = new Rect();
    private PointF mPivotOverride = null;
    private final PointF mPivot = new PointF();
    private DeviceProfile mDp;
    @SplitScreenConstants.SplitPosition
    private int mSplitPosition = SPLIT_POSITION_UNDEFINED;

    private final Matrix mMatrix = new Matrix();
    private final Matrix mMatrixTmp = new Matrix();

    // Thumbnail view properties
    private final Rect mThumbnailPosition = new Rect();
    private final ThumbnailData mThumbnailData = new ThumbnailData();
    private final PreviewPositionHelper mPositionHelper = new PreviewPositionHelper();
    private final Matrix mInversePositionMatrix = new Matrix();

    // TaskView properties
    private final FullscreenDrawParams mCurrentFullscreenParams;
    public final AnimatedFloat taskPrimaryTranslation = new AnimatedFloat();
    public final AnimatedFloat taskSecondaryTranslation = new AnimatedFloat();
    public final AnimatedFloat taskGridTranslationX = new AnimatedFloat();
    public final AnimatedFloat taskGridTranslationY = new AnimatedFloat();

    // Carousel properties
    public final AnimatedFloat carouselScale = new AnimatedFloat();

    // RecentsView properties
    public final AnimatedFloat recentsViewScale = new AnimatedFloat();
    public final AnimatedFloat fullScreenProgress = new AnimatedFloat();
    public final AnimatedFloat recentsViewSecondaryTranslation = new AnimatedFloat();
    public final AnimatedFloat recentsViewPrimaryTranslation = new AnimatedFloat();
    public final AnimatedFloat recentsViewScroll = new AnimatedFloat();

    // Cached calculations
    private boolean mLayoutValid = false;
    private int mOrientationStateId;
    private SplitBounds mSplitBounds;
    private Boolean mDrawsBelowRecents = null;
    private Boolean mDrawAboveOtherApps = null;
    private boolean mIsGridTask;
    private final boolean mIsDesktopTask;
    private boolean mIsAnimatingToCarousel = false;
    private final int mDesktopTaskIndex;

    @Nullable
    private Matrix mTaskRectTransform = null;

    public TaskViewSimulator(Context context, BaseContainerInterface sizeStrategy,
            boolean isDesktop, int desktopTaskIndex) {
        mContext = context;
        mSizeStrategy = sizeStrategy;
        mIsDesktopTask = isDesktop;
        mDesktopTaskIndex = desktopTaskIndex;

        mOrientationState = TraceHelper.allowIpcs("TaskViewSimulator.init",
                () -> new RecentsOrientedState(context, sizeStrategy, i -> { }));
        mOrientationState.setGestureActive(true);
        mCurrentFullscreenParams = mIsDesktopTask
                ? new DesktopFullscreenDrawParams(context)
                : new FullscreenDrawParams(context);
        mOrientationStateId = mOrientationState.getStateId();
        Resources resources = context.getResources();
        mIsRecentsRtl = mOrientationState.getOrientationHandler().getRecentsRtlSetting(resources);
        carouselScale.value = 1f;
    }

    /**
     * Sets the device profile for the current state
     */
    public void setDp(DeviceProfile dp) {
        mDp = dp;
        mLayoutValid = false;
        mOrientationState.setDeviceProfile(dp);
        if (enableGridOnlyOverview()) {
            mIsGridTask = dp.getDeviceProperties().isTablet() && !mIsDesktopTask;
        }
        calculateTaskSize();
    }

    /**
     * Updates the task size.
     */
    public void calculateTaskSize() {
        if (mDp == null) {
            return;
        }

        if (mIsGridTask) {
            mSizeStrategy.calculateGridTaskSize(mContext, mDp, mFullTaskSize,
                    mOrientationState.getOrientationHandler());
            if (enableGridOnlyOverview()) {
                mSizeStrategy.calculateTaskSize(mContext, mDp, mCarouselTaskSize,
                        mOrientationState.getOrientationHandler());
            }
        } else {
            mSizeStrategy.calculateTaskSize(mContext, mDp, mFullTaskSize,
                    mOrientationState.getOrientationHandler());
            if (enableGridOnlyOverview()) {
                mCarouselTaskSize.set(mFullTaskSize);
            }
        }

        if (mSplitBounds != null) {
            // The task rect changes according to the staged split task sizes, but recents
            // fullscreen scale and pivot remains the same since the task fits into the existing
            // sized task space bounds
            mTaskRect.set(mFullTaskSize);
            mOrientationState.getOrientationHandler()
                    .setSplitTaskSwipeRect(mDp, mTaskRect, mSplitBounds, mSplitPosition);
        } else if (mIsDesktopTask) {
            // For desktop, tasks can take up only part of the screen size.
            // Full task size represents the whole screen size, but scaled down to fit in recents.
            // Task rect will represent the scaled down thumbnail position and is placed inside
            // full task size as it is on the home screen.
            PointF fullscreenTaskDimension = new PointF();
            BaseActivityInterface.getTaskDimension(mContext, mDp, fullscreenTaskDimension);
            // Calculate the scale down factor used in recents
            float scale = mFullTaskSize.width() / fullscreenTaskDimension.x;
            mTaskRect.set(mThumbnailPosition);
            mTaskRect.scale(scale);
            // Ensure the task rect is inside the full task rect
            mTaskRect.offset(mFullTaskSize.left, mFullTaskSize.top);

            Rect taskDimension = new Rect(0, 0, (int) fullscreenTaskDimension.x,
                    (int) fullscreenTaskDimension.y);
            mTmpCropRect.set(mThumbnailPosition);
            if (mTmpCropRect.setIntersect(taskDimension, mThumbnailPosition)) {
                mTmpCropRect.offset(-mThumbnailPosition.left, -mThumbnailPosition.top);
            } else {
                mTmpCropRect.setEmpty();
            }
        } else {
            mTaskRect.set(mFullTaskSize);
        }
    }

    /**
     * Sets the orientation state used for this animation
     */
    public void setOrientationState(@NonNull RecentsOrientedState orientationState) {
        mOrientationState = orientationState;
        mLayoutValid = false;
    }

    /**
     * @see com.android.quickstep.views.RecentsView#FULLSCREEN_PROGRESS
     */
    public float getFullScreenScale() {
        if (mDp == null) {
            return 1;
        }
        float scale = mOrientationState.getFullScreenScaleAndPivot(
                mIsAnimatingToCarousel ? mCarouselTaskSize : mFullTaskSize, mDp, mPivot);
        if (mPivotOverride != null) {
            mPivot.set(mPivotOverride);
        }
        return scale;
    }

    /**
     * Sets the targets which the simulator will control
     */
    public void setPreview(RemoteAnimationTarget runningTarget) {
        setPreviewBounds(
                runningTarget.startBounds == null
                        ? (Utilities.ATLEAST_R ? runningTarget.screenSpaceBounds : runningTarget.sourceContainerBounds)
                        : runningTarget.startBounds,
                runningTarget.contentInsets);
    }

    /**
     * Sets the targets which the simulator will control specifically for targets to animate when
     * in split screen
     *
     * @param splitInfo set to {@code null} when not in staged split mode
     */
    public void setPreview(RemoteAnimationTarget runningTarget, SplitBounds splitInfo) {
        setPreview(runningTarget);
        mSplitBounds = splitInfo;
        if (mSplitBounds == null) {
            mSplitPosition = SPLIT_POSITION_UNDEFINED;
        } else {
            mSplitPosition = runningTarget.taskId == splitInfo.leftTopTaskId
                    ? SPLIT_POSITION_TOP_OR_LEFT : SPLIT_POSITION_BOTTOM_OR_RIGHT;
            if (enableFlexibleTwoAppSplit()) {
                mPositionHelper.setSplitBounds(mSplitBounds, mSplitPosition);
            }
        }
        calculateTaskSize();
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
    public void setScroll(float scroll) {
        recentsViewScroll.value = scroll;
    }

    public void setDrawsBelowRecents(boolean drawsBelowRecents) {
        mDrawsBelowRecents = drawsBelowRecents;
    }

    public boolean getDrawsBelowRecents() {
        return mDrawsBelowRecents != null ? mDrawsBelowRecents : false;
    }

    /**
     * Sets whether the task is part of overview grid and not being focused.
     */
    public void setIsGridTask(boolean isGridTask) {
        mIsGridTask = isGridTask;
    }

    /**
     * Sets whether drawing this app above other apps during animation. It's currently used when
     * activating an app window from the exploded desktop view which will launch the desktop tile
     * and exit Overview.
     */
    public void setDrawsAboveOtherApps(boolean drawsAboveOtherApps) {
        mDrawAboveOtherApps = drawsAboveOtherApps;
    }

    /**
     * Override the pivot used to apply scale changes.
     */
    public void setPivotOverride(PointF pivotOverride) {
        mPivotOverride = pivotOverride;
        getFullScreenScale();
    }

    /**
     * Adds animation for all the components corresponding to transition from an app to carousel.
     */
    public void addAppToCarouselAnim(PendingAnimation pa, Interpolator interpolator,
            boolean isHandlingAtomicEvent) {
        pa.addFloat(fullScreenProgress, AnimatedFloat.VALUE, 1, 0, interpolator);
        if (enableGridOnlyOverview() && mDp.getDeviceProperties().isTablet() && !isHandlingAtomicEvent) {
            mIsAnimatingToCarousel = true;
            carouselScale.value = mCarouselTaskSize.width() / (float) mFullTaskSize.width();
        }
        pa.addFloat(recentsViewScale, AnimatedFloat.VALUE, getFullScreenScale(), 1,
                interpolator);
    }

    /**
     * Adds animation for all the components corresponding to transition from overview to the app.
     */
    public void addOverviewToAppAnim(PendingAnimation pa, TimeInterpolator interpolator) {
        pa.addFloat(fullScreenProgress, AnimatedFloat.VALUE, 0, 1, interpolator);
        pa.addFloat(recentsViewScale, AnimatedFloat.VALUE, 1, getFullScreenScale(), interpolator);
    }

    /**
     * Returns the current clipped/visible window bounds in the window coordinate space
     */
    public RectF getCurrentCropRect() {
        // Crop rect is the inverse of thumbnail matrix
        mTempRectF.set(0, 0, mTaskRect.width(), mTaskRect.height());
        mInversePositionMatrix.mapRect(mTempRectF);
        return mTempRectF;
    }

    /**
     * Returns the current task bounds in the Launcher coordinate space.
     */
    public RectF getCurrentRect() {
        RectF result = getCurrentCropRect();
        mMatrixTmp.set(mMatrix);
        preDisplayRotation(mOrientationState.getDisplayRotation(), mDp.getDeviceProperties().getWidthPx(), mDp.getDeviceProperties().getHeightPx(),
                mMatrixTmp);
        mMatrixTmp.mapRect(result);
        return result;
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
     * Sets a matrix used to transform the position of tasks. If set, this matrix is applied to
     * the task rect after the task has been scaled and positioned inside the fulltask, but
     * before scaling and translation of the whole recents view is performed.
     */
    public void setTaskRectTransform(@Nullable Matrix taskRectTransform) {
        mTaskRectTransform = taskRectTransform;
    }

    /**
     * Calculates the crop rect for desktop tasks given the current matrix.
     */
    private void calculateDesktopTaskCropRect() {
        // The approach here is to map a rect that represents the untransformed thumbnail position
        // using the current matrix. This will give us a rect that can be intersected with
        // [mFullTaskSize]. Using the intersection, we then compute how much of the task window that
        // needs to be cropped (which will be nothing if the window is entirely within the desktop).
        mTempRectF.set(0, 0, mThumbnailPosition.width(), mThumbnailPosition.height());
        mMatrix.mapRect(mTempRectF);

        float offsetX = mTempRectF.left;
        float offsetY = mTempRectF.top;
        float scale = mThumbnailPosition.width() / mTempRectF.width();

        if (mTempRectF.intersect(mFullTaskSize.left, mFullTaskSize.top, mFullTaskSize.right,
                mFullTaskSize.bottom)) {
            mTempRectF.offset(-offsetX, -offsetY);
            mTempRectF.scale(scale);
            mTempRectF.round(mTmpCropRect);
        }
    }

    /**
     * Applies the rotation on the matrix to so that it maps from launcher coordinate space to
     * window coordinate space.
     */
    public void applyWindowToHomeRotation(Matrix matrix) {
        matrix.postTranslate(mDp.getDeviceProperties().getWindowX(), mDp.getDeviceProperties().getWindowY());
        postDisplayRotation(deltaRotation(
                        mOrientationState.getRecentsActivityRotation(),
                        mOrientationState.getDisplayRotation()),
                mDp.getDeviceProperties().getWidthPx(), mDp.getDeviceProperties().getHeightPx(), matrix);
    }

    /**
     * Applies the target to the previously set parameters
     */
    public void apply(TransformParams params) {
        apply(params, null);
    }

    /**
     * Applies the target to the previously set parameters, optionally with an overridden
     * surface transaction
     */
    public void apply(TransformParams params, @Nullable SurfaceTransaction surfaceTransaction) {
        if (mDp == null || mThumbnailPosition.isEmpty()) {
            return;
        }
        if (!mLayoutValid || mOrientationStateId != mOrientationState.getStateId()) {
            mLayoutValid = true;
            mOrientationStateId = mOrientationState.getStateId();

            getFullScreenScale();
            if (TaskAnimationManager.SHELL_TRANSITIONS_ROTATION) {
                // With shell transitions, the display is rotated early so we need to actually use
                // the rotation when the gesture starts
                mThumbnailData.rotation = mOrientationState.getTouchRotation();
            } else {
                mThumbnailData.rotation = mOrientationState.getDisplayRotation();
            }

            // mIsRecentsRtl is the inverse of TaskView RTL.
            boolean isRtlEnabled = !mIsRecentsRtl;
            mPositionHelper.updateThumbnailMatrix(
                    mThumbnailPosition, mThumbnailData, mTaskRect.width(), mTaskRect.height(),
                    mDp.getDeviceProperties().isTablet(), mOrientationState.getRecentsActivityRotation(), isRtlEnabled);
            mPositionHelper.getMatrix().invert(mInversePositionMatrix);
            if (DEBUG) {
                Log.d(TAG, " taskRect: " + mTaskRect);
            }
        }

        float fullScreenProgress = Utilities.boundToRange(this.fullScreenProgress.value, 0, 1);
        mCurrentFullscreenParams.setProgress(fullScreenProgress, recentsViewScale.value,
                carouselScale.value);

        // Apply thumbnail matrix
        float taskWidth = mTaskRect.width();
        float taskHeight = mTaskRect.height();

        mMatrix.set(mPositionHelper.getMatrix());

        // Apply TaskView matrix: taskRect, optional transform, translate
        mMatrix.postTranslate(mTaskRect.left, mTaskRect.top);
        if (mTaskRectTransform != null) {
            mMatrix.postConcat(mTaskRectTransform);

            // Calculate cropping for desktop tasks. The order is important since it uses the
            // current matrix. Therefore we calculate it here, after applying the task rect
            // transform, but before applying scaling/translation that affects the whole
            // recentsview.
            if (mIsDesktopTask) {
                calculateDesktopTaskCropRect();
            }
        }

        mOrientationState.getOrientationHandler().setPrimary(mMatrix, MATRIX_POST_TRANSLATE,
                taskPrimaryTranslation.value);
        mOrientationState.getOrientationHandler().setSecondary(mMatrix, MATRIX_POST_TRANSLATE,
                taskSecondaryTranslation.value);
        mMatrix.postTranslate(taskGridTranslationX.value, taskGridTranslationY.value);

        mMatrix.postScale(carouselScale.value, carouselScale.value,
                mIsRecentsRtl ? mCarouselTaskSize.right : mCarouselTaskSize.left,
                mCarouselTaskSize.top);

        mOrientationState.getOrientationHandler().setPrimary(
                mMatrix, MATRIX_POST_TRANSLATE, recentsViewScroll.value);

        // Apply RecentsView matrix
        mMatrix.postScale(recentsViewScale.value, recentsViewScale.value, mPivot.x, mPivot.y);
        mOrientationState.getOrientationHandler().setSecondary(mMatrix, MATRIX_POST_TRANSLATE,
                recentsViewSecondaryTranslation.value);
        mOrientationState.getOrientationHandler().setPrimary(mMatrix, MATRIX_POST_TRANSLATE,
                recentsViewPrimaryTranslation.value);
        applyWindowToHomeRotation(mMatrix);

        if (!mIsDesktopTask) {
            // Crop rect is the inverse of thumbnail matrix
            mTempRectF.set(0, 0, taskWidth, taskHeight);
            mInversePositionMatrix.mapRect(mTempRectF);
            mTempRectF.roundOut(mTmpCropRect);
        }

        params.setProgress(1f - fullScreenProgress);
        params.applySurfaceParams(surfaceTransaction == null
                ? params.createSurfaceParams(this) : surfaceTransaction);

        if (!DEBUG) {
            return;
        }
        Log.d(TAG, "progress: " + fullScreenProgress
                + " carouselScale: " + carouselScale.value
                + " recentsViewScale: " + recentsViewScale.value
                + " crop: " + mTmpCropRect
                + " radius: " + getCurrentCornerRadius()
                + " taskW: " + taskWidth + " H: " + taskHeight
                + " taskRect: " + mTaskRect
                + " taskPrimaryT: " + taskPrimaryTranslation.value
                + " taskSecondaryT: " + taskSecondaryTranslation.value
                + " taskGridTranslationX: " + taskGridTranslationX.value
                + " taskGridTranslationY: " + taskGridTranslationY.value
                + " recentsPrimaryT: " + recentsViewPrimaryTranslation.value
                + " recentsSecondaryT: " + recentsViewSecondaryTranslation.value
                + " recentsScroll: " + recentsViewScroll.value
                + " pivot: " + mPivot
        );
    }

    @Override
    public void onBuildTargetParams(
            SurfaceProperties builder, RemoteAnimationTarget app, TransformParams params) {
        builder.setMatrix(mMatrix)
                .setWindowCrop(mTmpCropRect)
                .setCornerRadius(getCurrentCornerRadius());

        if (mDrawsBelowRecents == null && mDrawAboveOtherApps == null) {
            // No reordering will be enforced.
            return;
        }

        // In shell transitions, the animation leashes are reparented to an animation container
        // so we can bump layers as needed.
        int baseLayer = app.prefixOrderIndex - mDesktopTaskIndex;
        // 1000/2000 are arbitrary numbers to give room for multiple layers.
        if (mDrawsBelowRecents != null) {
            baseLayer += mDrawsBelowRecents ? Integer.MIN_VALUE + 2000 :  Integer.MAX_VALUE - 2000;
        }
        if (mDrawAboveOtherApps != null && mDrawAboveOtherApps) {
            baseLayer += 1000;
        }
        builder.setLayer(baseLayer);
    }

    /**
     * Returns the corner radius that should be applied to the target so that it matches the
     * TaskView
     */
    public float getCurrentCornerRadius() {
        float visibleRadius = mCurrentFullscreenParams.getCurrentCornerRadius();
        mTempPoint[0] = visibleRadius;
        mTempPoint[1] = 0;
        mInversePositionMatrix.mapVectors(mTempPoint);

        // Ideally we should use square-root. This is an optimization as one of the dimension is 0.
        return Math.max(Math.abs(mTempPoint[0]), Math.abs(mTempPoint[1]));
    }
}