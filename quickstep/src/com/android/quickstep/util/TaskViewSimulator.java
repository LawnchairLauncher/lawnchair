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

import static com.android.launcher3.Flags.enableGridOnlyOverview;
import static com.android.launcher3.states.RotationHelper.deltaRotation;
import static com.android.launcher3.touch.PagedOrientationHandler.MATRIX_POST_TRANSLATE;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;
import static com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import static com.android.quickstep.TaskAnimationManager.ENABLE_SHELL_TRANSITIONS;
import static com.android.quickstep.util.RecentsOrientedState.postDisplayRotation;
import static com.android.quickstep.util.RecentsOrientedState.preDisplayRotation;
import static com.android.quickstep.util.SplitScreenUtils.convertLauncherSplitBoundsToShell;

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

import com.android.app.animation.Interpolators;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.BaseActivityInterface;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.views.TaskView.FullscreenDrawParams;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper;

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
    @StagePosition
    private int mStagePosition = STAGE_POSITION_UNDEFINED;

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

    // Carousel properties
    public final AnimatedFloat carouselScale = new AnimatedFloat();
    public final AnimatedFloat carouselPrimaryTranslation = new AnimatedFloat();
    public final AnimatedFloat carouselSecondaryTranslation = new AnimatedFloat();

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
    private boolean mIsGridTask;
    private boolean mIsDesktopTask;
    private boolean mScaleToCarouselTaskSize = false;
    private int mTaskRectTranslationX;
    private int mTaskRectTranslationY;

    public TaskViewSimulator(Context context, BaseContainerInterface sizeStrategy) {
        mContext = context;
        mSizeStrategy = sizeStrategy;

        mOrientationState = TraceHelper.allowIpcs("TaskViewSimulator.init",
                () -> new RecentsOrientedState(context, sizeStrategy, i -> { }));
        mOrientationState.setGestureActive(true);
        mCurrentFullscreenParams = new FullscreenDrawParams(context);
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
        calculateTaskSize();
    }

    private void calculateTaskSize() {
        if (mDp == null) {
            return;
        }

        if (mIsGridTask) {
            mSizeStrategy.calculateGridTaskSize(mContext, mDp, mFullTaskSize,
                    mOrientationState.getOrientationHandler());
        } else {
            mSizeStrategy.calculateTaskSize(mContext, mDp, mFullTaskSize,
                    mOrientationState.getOrientationHandler());
        }

        if (enableGridOnlyOverview()) {
            mSizeStrategy.calculateCarouselTaskSize(mContext, mDp, mCarouselTaskSize,
                    mOrientationState.getOrientationHandler());
        }

        if (mSplitBounds != null) {
            // The task rect changes according to the staged split task sizes, but recents
            // fullscreen scale and pivot remains the same since the task fits into the existing
            // sized task space bounds
            mTaskRect.set(mFullTaskSize);
            mOrientationState.getOrientationHandler()
                    .setSplitTaskSwipeRect(mDp, mTaskRect, mSplitBounds, mStagePosition);
            mTaskRect.offset(mTaskRectTranslationX, mTaskRectTranslationY);
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
        } else {
            mTaskRect.set(mFullTaskSize);
            mTaskRect.offset(mTaskRectTranslationX, mTaskRectTranslationY);
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
        // Copy mFullTaskSize instead of updating it directly so it could be reused next time
        // without recalculating
        Rect scaleRect = new Rect();
        if (mScaleToCarouselTaskSize) {
            scaleRect.set(mCarouselTaskSize);
        } else {
            scaleRect.set(mFullTaskSize);
        }
        scaleRect.offset(mTaskRectTranslationX, mTaskRectTranslationY);
        float scale = mOrientationState.getFullScreenScaleAndPivot(scaleRect, mDp, mPivot);
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
                        ? runningTarget.screenSpaceBounds : runningTarget.startBounds,
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
            mStagePosition = STAGE_POSITION_UNDEFINED;
        } else {
            mStagePosition = runningTarget.taskId == splitInfo.leftTopTaskId
                    ? STAGE_POSITION_TOP_OR_LEFT : STAGE_POSITION_BOTTOM_OR_RIGHT;
            mPositionHelper.setSplitBounds(convertLauncherSplitBoundsToShell(mSplitBounds),
                    mStagePosition);
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

    /**
     * Sets whether the task is part of overview grid and not being focused.
     */
    public void setIsGridTask(boolean isGridTask) {
        mIsGridTask = isGridTask;
    }

    /**
     * Sets whether this task is part of desktop tasks in overview.
     */
    public void setIsDesktopTask(boolean desktop) {
        mIsDesktopTask = desktop;
    }

    /**
     * Apply translations on TaskRect's starting location.
     */
    public void setTaskRectTranslation(int taskRectTranslationX, int taskRectTranslationY) {
        mTaskRectTranslationX = taskRectTranslationX;
        mTaskRectTranslationY = taskRectTranslationY;
        // Re-calculate task size after changing translation
        calculateTaskSize();
    }

    /**
     * Adds animation for all the components corresponding to transition from an app to overview.
     */
    public void addAppToOverviewAnim(PendingAnimation pa, Interpolator interpolator) {
        pa.addFloat(fullScreenProgress, AnimatedFloat.VALUE, 1, 0, interpolator);
        float fullScreenScale;
        if (enableGridOnlyOverview() && mDp.isTablet && mDp.isGestureMode) {
            // Move pivot to top right edge of the screen, to avoid task scaling down in opposite
            // direction of app window movement, otherwise the animation will wiggle left and right.
            // Also translate the app window to top right edge of the screen to simplify
            // calculations.
            taskPrimaryTranslation.value = mIsRecentsRtl
                    ? mDp.widthPx - mFullTaskSize.right
                    : -mFullTaskSize.left;
            taskSecondaryTranslation.value = -mFullTaskSize.top;
            mPivotOverride = new PointF(mIsRecentsRtl ? mDp.widthPx : 0, 0);

            // Scale down to the carousel and use the carousel Rect to calculate fullScreenScale.
            mScaleToCarouselTaskSize = true;
            carouselScale.value = mCarouselTaskSize.width() / (float) mFullTaskSize.width();
            fullScreenScale = getFullScreenScale();

            float carouselPrimaryTranslationTarget = mIsRecentsRtl
                    ? mCarouselTaskSize.right - mDp.widthPx
                    : mCarouselTaskSize.left;
            float carouselSecondaryTranslationTarget = mCarouselTaskSize.top;

            // Expected carousel position's center is in the middle, and invariant of
            // recentsViewScale.
            float exceptedCarouselCenterX = mCarouselTaskSize.centerX();
            // Animating carousel translations linearly will result in a curved path, therefore
            // we'll need to calculate the expected translation at each recentsView scale. Luckily
            // primary and secondary follow the same translation, and primary is used here due to
            // it being simpler.
            Interpolator carouselTranslationInterpolator = t -> {
                // recentsViewScale is calculated rather than using recentsViewScale.value, so that
                // this interpolator works independently even if recentsViewScale don't animate.
                float recentsViewScale =
                        Utilities.mapToRange(t, 0, 1, fullScreenScale, 1, Interpolators.LINEAR);
                // Without the translation, the app window will animate from fullscreen into top
                // right corner.
                float expectedTaskCenterX = mIsRecentsRtl
                        ? mDp.widthPx - mCarouselTaskSize.width() * recentsViewScale / 2f
                        : mCarouselTaskSize.width() * recentsViewScale / 2f;
                // Calculate the expected translation, then work back the animatedFraction that
                // results in this value.
                float carouselPrimaryTranslation =
                        (exceptedCarouselCenterX - expectedTaskCenterX) / recentsViewScale;
                return carouselPrimaryTranslation / carouselPrimaryTranslationTarget;
            };

            // Use addAnimatedFloat so this animation can later be canceled and animate to a
            // different value in RecentsView.onPrepareGestureEndAnimation.
            pa.addAnimatedFloat(carouselPrimaryTranslation, 0, carouselPrimaryTranslationTarget,
                    carouselTranslationInterpolator);
            pa.addAnimatedFloat(carouselSecondaryTranslation, 0, carouselSecondaryTranslationTarget,
                    carouselTranslationInterpolator);
        } else {
            fullScreenScale = getFullScreenScale();
        }
        pa.addFloat(recentsViewScale, AnimatedFloat.VALUE, fullScreenScale, 1, interpolator);
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
        preDisplayRotation(mOrientationState.getDisplayRotation(), mDp.widthPx, mDp.heightPx,
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
     * Applies the rotation on the matrix to so that it maps from launcher coordinate space to
     * window coordinate space.
     */
    public void applyWindowToHomeRotation(Matrix matrix) {
        matrix.postTranslate(mDp.windowX, mDp.windowY);
        postDisplayRotation(deltaRotation(
                        mOrientationState.getRecentsActivityRotation(),
                        mOrientationState.getDisplayRotation()),
                mDp.widthPx, mDp.heightPx, matrix);
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
                    mDp.isTablet, mOrientationState.getRecentsActivityRotation(), isRtlEnabled);
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

        // Apply TaskView matrix: taskRect, translate
        mMatrix.postTranslate(mTaskRect.left, mTaskRect.top);
        mOrientationState.getOrientationHandler().setPrimary(mMatrix, MATRIX_POST_TRANSLATE,
                taskPrimaryTranslation.value);
        mOrientationState.getOrientationHandler().setSecondary(mMatrix, MATRIX_POST_TRANSLATE,
                taskSecondaryTranslation.value);

        mMatrix.postScale(carouselScale.value, carouselScale.value, mPivot.x, mPivot.y);
        mOrientationState.getOrientationHandler().setPrimary(mMatrix, MATRIX_POST_TRANSLATE,
                carouselPrimaryTranslation.value);
        mOrientationState.getOrientationHandler().setSecondary(mMatrix, MATRIX_POST_TRANSLATE,
                carouselSecondaryTranslation.value);

        mOrientationState.getOrientationHandler().setPrimary(
                mMatrix, MATRIX_POST_TRANSLATE, recentsViewScroll.value);

        // Apply RecentsView matrix
        mMatrix.postScale(recentsViewScale.value, recentsViewScale.value, mPivot.x, mPivot.y);
        mOrientationState.getOrientationHandler().setSecondary(mMatrix, MATRIX_POST_TRANSLATE,
                recentsViewSecondaryTranslation.value);
        mOrientationState.getOrientationHandler().setPrimary(mMatrix, MATRIX_POST_TRANSLATE,
                recentsViewPrimaryTranslation.value);
        applyWindowToHomeRotation(mMatrix);

        // Crop rect is the inverse of thumbnail matrix
        mTempRectF.set(0, 0, taskWidth, taskHeight);
        mInversePositionMatrix.mapRect(mTempRectF);
        mTempRectF.roundOut(mTmpCropRect);

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
                + " carouselPrimaryT: " + carouselPrimaryTranslation.value
                + " carouselSecondaryT: " + carouselSecondaryTranslation.value
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

        // If mDrawsBelowRecents is unset, no reordering will be enforced.
        if (mDrawsBelowRecents != null) {
            // In legacy transitions, the animation leashes remain in same hierarchy in the
            // TaskDisplayArea, so we don't want to bump the layer too high otherwise it will
            // conflict with layers that WM core positions (ie. the input consumers).  For shell
            // transitions, the animation leashes are reparented to an animation container so we
            // can bump layers as needed.
            if (ENABLE_SHELL_TRANSITIONS) {
                builder.setLayer(mDrawsBelowRecents
                        ? Integer.MIN_VALUE + app.prefixOrderIndex
                        // 1000 is an arbitrary number to give room for multiple layers.
                        : Integer.MAX_VALUE - 1000 + app.prefixOrderIndex);
            } else {
                builder.setLayer(mDrawsBelowRecents
                        ? Integer.MIN_VALUE + app.prefixOrderIndex
                        : 0);
            }
        }
    }

    /**
     * Returns the corner radius that should be applied to the target so that it matches the
     * TaskView
     */
    public float getCurrentCornerRadius() {
        float visibleRadius = mCurrentFullscreenParams.getCurrentDrawnCornerRadius();
        mTempPoint[0] = visibleRadius;
        mTempPoint[1] = 0;
        mInversePositionMatrix.mapVectors(mTempPoint);

        // Ideally we should use square-root. This is an optimization as one of the dimension is 0.
        return Math.max(Math.abs(mTempPoint[0]), Math.abs(mTempPoint[1]));
    }
}
