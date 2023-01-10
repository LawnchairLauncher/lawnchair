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
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;
import static com.android.launcher3.util.SplitConfigurationOptions.StagePosition;
import static com.android.quickstep.TaskAnimationManager.ENABLE_SHELL_TRANSITIONS;
import static com.android.quickstep.util.RecentsOrientedState.postDisplayRotation;
import static com.android.quickstep.util.RecentsOrientedState.preDisplayRotation;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.RemoteAnimationTarget;

import androidx.annotation.NonNull;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.BaseActivityInterface;
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
    private final BaseActivityInterface mSizeStrategy;

    @NonNull
    private RecentsOrientedState mOrientationState;
    private final boolean mIsRecentsRtl;

    private final Rect mTaskRect = new Rect();
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
    private int mTaskRectTranslationX;
    private int mTaskRectTranslationY;

    public TaskViewSimulator(Context context, BaseActivityInterface sizeStrategy) {
        mContext = context;
        mSizeStrategy = sizeStrategy;

        // TODO(b/187074722): Don't create this per-TaskViewSimulator
        mOrientationState = TraceHelper.allowIpcs("",
                () -> new RecentsOrientedState(context, sizeStrategy, i -> { }));
        mOrientationState.setGestureActive(true);
        mCurrentFullscreenParams = new FullscreenDrawParams(context);
        mOrientationStateId = mOrientationState.getStateId();
        Resources resources = context.getResources();
        mIsRecentsRtl = mOrientationState.getOrientationHandler().getRecentsRtlSetting(resources);
    }

    /**
     * Sets the device profile for the current state
     */
    public void setDp(DeviceProfile dp) {
        mDp = dp;
        mLayoutValid = false;
        mOrientationState.setDeviceProfile(dp);
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
        if (mIsGridTask) {
            mSizeStrategy.calculateGridTaskSize(mContext, mDp, mTaskRect,
                    mOrientationState.getOrientationHandler());
        } else {
            mSizeStrategy.calculateTaskSize(mContext, mDp, mTaskRect);
        }

        Rect fullTaskSize;
        if (mSplitBounds != null) {
            // The task rect changes according to the staged split task sizes, but recents
            // fullscreen scale and pivot remains the same since the task fits into the existing
            // sized task space bounds
            fullTaskSize = new Rect(mTaskRect);
            mOrientationState.getOrientationHandler()
                    .setSplitTaskSwipeRect(mDp, mTaskRect, mSplitBounds, mStagePosition);
            mTaskRect.offset(mTaskRectTranslationX, mTaskRectTranslationY);
        } else {
            fullTaskSize = mTaskRect;
        }
        fullTaskSize.offset(mTaskRectTranslationX, mTaskRectTranslationY);
        return mOrientationState.getFullScreenScaleAndPivot(fullTaskSize, mDp, mPivot);
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
            return;
        }
        mStagePosition = mThumbnailPosition.equals(splitInfo.leftTopBounds) ?
                STAGE_POSITION_TOP_OR_LEFT :
                STAGE_POSITION_BOTTOM_OR_RIGHT;
        mPositionHelper.setSplitBounds(convertSplitBounds(mSplitBounds), mStagePosition);
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
     * Apply translations on TaskRect's starting location.
     */
    public void setTaskRectTranslation(int taskRectTranslationX, int taskRectTranslationY) {
        mTaskRectTranslationX = taskRectTranslationX;
        mTaskRectTranslationY = taskRectTranslationY;
    }

    /**
     * Adds animation for all the components corresponding to transition from an app to overview.
     */
    public void addAppToOverviewAnim(PendingAnimation pa, TimeInterpolator interpolator) {
        pa.addFloat(fullScreenProgress, AnimatedFloat.VALUE, 1, 0, interpolator);
        pa.addFloat(recentsViewScale, AnimatedFloat.VALUE, getFullScreenScale(), 1, interpolator);
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
        RectF insets = mCurrentFullscreenParams.mCurrentDrawnInsets;
        mTempRectF.set(-insets.left, -insets.top,
                mTaskRect.width() + insets.right, mTaskRect.height() + insets.bottom);
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
                    mDp.widthPx, mDp.heightPx, mDp.taskbarSize, mDp.isTablet,
                    mOrientationState.getRecentsActivityRotation(), isRtlEnabled);
            mPositionHelper.getMatrix().invert(mInversePositionMatrix);
            if (DEBUG) {
                Log.d(TAG, " taskRect: " + mTaskRect);
            }
        }

        float fullScreenProgress = Utilities.boundToRange(this.fullScreenProgress.value, 0, 1);
        mCurrentFullscreenParams.setProgress(fullScreenProgress, recentsViewScale.value,
                /* taskViewScale= */1f, mTaskRect.width(), mDp, mPositionHelper);

        // Apply thumbnail matrix
        RectF insets = mCurrentFullscreenParams.mCurrentDrawnInsets;
        float scale = mCurrentFullscreenParams.mScale;
        float taskWidth = mTaskRect.width();
        float taskHeight = mTaskRect.height();

        mMatrix.set(mPositionHelper.getMatrix());
        mMatrix.postTranslate(insets.left, insets.top);
        mMatrix.postScale(scale, scale);

        // Apply TaskView matrix: taskRect, translate
        mMatrix.postTranslate(mTaskRect.left, mTaskRect.top);
        mOrientationState.getOrientationHandler().setPrimary(mMatrix, MATRIX_POST_TRANSLATE,
                taskPrimaryTranslation.value);
        mOrientationState.getOrientationHandler().setSecondary(mMatrix, MATRIX_POST_TRANSLATE,
                taskSecondaryTranslation.value);
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
        mTempRectF.set(-insets.left, -insets.top,
                taskWidth + insets.right, taskHeight + insets.bottom);
        mInversePositionMatrix.mapRect(mTempRectF);
        mTempRectF.roundOut(mTmpCropRect);

        params.applySurfaceParams(params.createSurfaceParams(this));

        if (!DEBUG) {
            return;
        }
        Log.d(TAG, "progress: " + fullScreenProgress
                + " scale: " + scale
                + " recentsViewScale: " + recentsViewScale.value
                + " crop: " + mTmpCropRect
                + " radius: " + getCurrentCornerRadius()
                + " taskW: " + taskWidth + " H: " + taskHeight
                + " taskRect: " + mTaskRect
                + " taskPrimaryT: " + taskPrimaryTranslation.value
                + " recentsPrimaryT: " + recentsViewPrimaryTranslation.value
                + " recentsSecondaryT: " + recentsViewSecondaryTranslation.value
                + " taskSecondaryT: " + taskSecondaryTranslation.value
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
            builder.setLayer(mDrawsBelowRecents
                    ? Integer.MIN_VALUE + 1
                    : ENABLE_SHELL_TRANSITIONS ? Integer.MAX_VALUE : 0);
        }
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

    /**
     * TODO(b/254378592): Remove this after consolidation of classes
     */
    public static com.android.wm.shell.util.SplitBounds convertSplitBounds(SplitBounds bounds) {
        return new com.android.wm.shell.util.SplitBounds(
                bounds.leftTopBounds,
                bounds.rightBottomBounds,
                bounds.leftTopTaskId,
                bounds.rightBottomTaskId
        );
    }
}
