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

import android.animation.Animator;
import android.animation.RectEvaluator;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.util.Rational;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.window.PictureInPictureSurfaceTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.jank.Cuj;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.icons.IconProvider;
import com.android.quickstep.TaskAnimationManager;
import com.android.systemui.shared.pip.PipSurfaceTransactionHelper;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.wm.shell.shared.pip.PipContentOverlay;

/**
 * Subclass of {@link RectFSpringAnim} that animates an Activity to PiP (picture-in-picture) window
 * when swiping up (in gesture navigation mode).
 */
public class SwipePipToHomeAnimator extends RectFSpringAnim {
    private static final String TAG = "SwipePipToHomeAnimator";

    private static final float END_PROGRESS = 1.0f;

    private final int mTaskId;
    private final ActivityInfo mActivityInfo;
    private final SurfaceControl mLeash;
    private final Rect mSourceRectHint = new Rect();
    private final Rect mAppBounds = new Rect();
    private final Matrix mHomeToWindowPositionMap = new Matrix();
    private final Rect mStartBounds = new Rect();
    private final RectF mCurrentBoundsF = new RectF();
    private final Rect mCurrentBounds = new Rect();
    private final Rect mDestinationBounds = new Rect();
    private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    /**
     * For calculating transform in
     * {@link #onAnimationUpdate(SurfaceControl.Transaction, RectF, float)}
     */
    private final RectEvaluator mInsetsEvaluator = new RectEvaluator(new Rect());
    private final Rect mSourceHintRectInsets;
    private final Rect mSourceInsets = new Rect();

    /** for rotation calculations */
    private final @RecentsOrientedState.SurfaceRotation int mFromRotation;
    private final Rect mDestinationBoundsTransformed = new Rect();

    /**
     * Flag to avoid the double-end problem since the leash would have been released
     * after the first end call and any further operations upon it would lead to NPE.
     */
    private boolean mHasAnimationEnded;

    /**
     * Wrapper of {@link SurfaceControl} that is used when entering PiP without valid
     * source rect hint.
     */
    @Nullable
    private PipContentOverlay mPipContentOverlay;

    /**
     * @param context {@link Context} provides Launcher resources
     * @param taskId Task id associated with this animator, see also {@link #getTaskId()}
     * @param activityInfo {@link ActivityInfo} associated with this animator,
     *                      see also {@link #getComponentName()}
     * @param appIconSizePx The size in pixel for the app icon in content overlay
     * @param leash {@link SurfaceControl} this animator operates on
     * @param sourceRectHint See the definition in {@link android.app.PictureInPictureParams}
     * @param appBounds Bounds of the application, sourceRectHint is based on this bounds
     * @param homeToWindowPositionMap {@link Matrix} to map a Rect from home to window space
     * @param startBounds Bounds of the application when this animator starts. This can be
     *                    different from the appBounds if user has swiped a certain distance and
     *                    Launcher has performed transform on the leash.
     * @param destinationBounds Bounds of the destination this animator ends to
     * @param fromRotation From rotation if different from final rotation, ROTATION_0 otherwise
     * @param destinationBoundsTransformed Destination bounds in window space
     * @param cornerRadius Corner radius in pixel value for PiP window
     * @param shadowRadius Shadow radius in pixel value for PiP window
     * @param view Attached view for logging purpose
     */
    private SwipePipToHomeAnimator(@NonNull Context context,
            int taskId,
            @NonNull ActivityInfo activityInfo,
            int appIconSizePx,
            @NonNull SurfaceControl leash,
            @NonNull Rect sourceRectHint,
            @NonNull Rect appBounds,
            @NonNull Matrix homeToWindowPositionMap,
            @NonNull RectF startBounds,
            @NonNull Rect destinationBounds,
            @RecentsOrientedState.SurfaceRotation int fromRotation,
            @NonNull Rect destinationBoundsTransformed,
            int cornerRadius,
            int shadowRadius,
            @NonNull View view) {
        super(new DefaultSpringConfig(context, null, startBounds,
                new RectF(destinationBoundsTransformed)));
        mTaskId = taskId;
        mActivityInfo = activityInfo;
        mLeash = leash;
        mAppBounds.set(appBounds);
        mHomeToWindowPositionMap.set(homeToWindowPositionMap);
        startBounds.round(mStartBounds);
        mDestinationBounds.set(destinationBounds);
        mFromRotation = fromRotation;
        mDestinationBoundsTransformed.set(destinationBoundsTransformed);
        mSurfaceTransactionHelper = new PipSurfaceTransactionHelper(cornerRadius, shadowRadius);

        final Rational aspectRatio = new Rational(
                destinationBounds.width(), destinationBounds.height());
        String reasonForCreateOverlay = null; // For debugging purpose.

        // Slightly larger app bounds to allow for off by 1 pixel source-rect-hint errors.
        Rect overflowAppBounds = new Rect(appBounds.left - 1, appBounds.top - 1,
                        appBounds.right + 1, appBounds.bottom + 1);
        if (sourceRectHint.isEmpty()) {
            reasonForCreateOverlay = "Source rect hint is empty";
        } else if (sourceRectHint.width() < destinationBounds.width()
                || sourceRectHint.height() < destinationBounds.height()) {
            // This is a situation in which the source hint rect on at least one axis is smaller
            // than the destination bounds, which presents a problem because we would have to scale
            // up that axis to fit the bounds. So instead, just fallback to the non-source hint
            // animation in this case.
            reasonForCreateOverlay = "Source rect hint is too small " + sourceRectHint;
            sourceRectHint.setEmpty();
        } else if (!overflowAppBounds.contains(sourceRectHint)) {
            // This is a situation in which the source hint rect is outside the app bounds, so it is
            // not a valid rectangle to use for cropping app surface
            reasonForCreateOverlay = "Source rect hint exceeds display bounds " + sourceRectHint;
            sourceRectHint.setEmpty();
        } else {
            final Rational srcAspectRatio = new Rational(
                    sourceRectHint.width(), sourceRectHint.height());
            if (!PictureInPictureParams.isSameAspectRatio(destinationBounds, srcAspectRatio)) {
                // The aspect ratio of destination bounds does not match source rect hint.
                // We use the aspect ratio of source rect hint to check against destination bounds
                // here to avoid upscaling error.
                reasonForCreateOverlay = "Source rect hint:" + sourceRectHint
                        + " does not match destination bounds:" + destinationBounds;
                sourceRectHint.setEmpty();
            }
        }

        if (sourceRectHint.isEmpty()) {
            mSourceRectHint.set(
                    getEnterPipWithOverlaySrcRectHint(appBounds, aspectRatio.floatValue()));
            mPipContentOverlay = new PipContentOverlay.PipAppIconOverlay(view.getContext(),
                    mAppBounds, mDestinationBounds,
                    new IconProvider(context).getIcon(mActivityInfo), appIconSizePx);
            final SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
            mPipContentOverlay.attach(tx, mLeash);
            Log.d(TAG, getContentOverlay() + " is created: " + reasonForCreateOverlay);
        } else {
            mSourceRectHint.set(sourceRectHint);
        }
        mSourceHintRectInsets = new Rect(mSourceRectHint.left - appBounds.left,
                mSourceRectHint.top - appBounds.top,
                appBounds.right - mSourceRectHint.right,
                appBounds.bottom - mSourceRectHint.bottom);

        addAnimatorListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                InteractionJankMonitorWrapper.begin(view, Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_PIP);
                super.onAnimationStart(animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_PIP);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_PIP);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mHasAnimationEnded) return;
                super.onAnimationEnd(animation);
                mHasAnimationEnded = true;
            }
        });
        addOnUpdateListener(this::onAnimationUpdate);
    }

    /**
     * Crop a Rect matches the aspect ratio and pivots at the center point.
     */
    private Rect getEnterPipWithOverlaySrcRectHint(Rect appBounds, float aspectRatio) {
        final float appBoundsAspectRatio = appBounds.width() / (float) appBounds.height();
        final int width, height;
        int left = appBounds.left;
        int top = appBounds.top;
        if (appBoundsAspectRatio < aspectRatio) {
            width = appBounds.width();
            height = (int) (width / aspectRatio);
            top = appBounds.top + (appBounds.height() - height) / 2;
        } else {
            height = appBounds.height();
            width = (int) (height * aspectRatio);
            left = appBounds.left + (appBounds.width() - width) / 2;
        }
        return new Rect(left, top, left + width, top + height);
    }

    private void onAnimationUpdate(RectF currentRect, float progress) {
        if (mHasAnimationEnded) return;
        final SurfaceControl.Transaction tx =
                PipSurfaceTransactionHelper.newSurfaceControlTransaction();
        mHomeToWindowPositionMap.mapRect(mCurrentBoundsF, currentRect);
        onAnimationUpdate(tx, mCurrentBoundsF, progress);
        tx.apply();
    }

    private PictureInPictureSurfaceTransaction onAnimationUpdate(SurfaceControl.Transaction tx,
            RectF currentRect, float progress) {
        currentRect.round(mCurrentBounds);
        if (mPipContentOverlay != null) {
            mPipContentOverlay.onAnimationUpdate(tx, mCurrentBounds, progress);
        }
        return onAnimationScaleAndCrop(progress, tx, mCurrentBounds);
    }

    /** scale and crop the window with source rect hint */
    private PictureInPictureSurfaceTransaction onAnimationScaleAndCrop(
            float progress, SurfaceControl.Transaction tx,
            Rect bounds) {
        final Rect insets = mInsetsEvaluator.evaluate(progress, mSourceInsets,
                mSourceHintRectInsets);
        if (mFromRotation == Surface.ROTATION_90 || mFromRotation == Surface.ROTATION_270) {
            final RotatedPosition rotatedPosition = getRotatedPosition(progress);
            return mSurfaceTransactionHelper.scaleAndRotate(tx, mLeash, mAppBounds, bounds, insets,
                    rotatedPosition.degree, rotatedPosition.positionX, rotatedPosition.positionY);
        } else {
            return mSurfaceTransactionHelper.scaleAndCrop(tx, mLeash, mSourceRectHint, mAppBounds,
                    bounds, insets, progress);
        }
    }

    public int getTaskId() {
        return mTaskId;
    }

    public ComponentName getComponentName() {
        return mActivityInfo.getComponentName();
    }

    public Rect getDestinationBounds() {
        return mDestinationBounds;
    }

    public Rect getAppBounds() {
        return mAppBounds;
    }

    public Rect getSourceRectHint() {
        return mSourceRectHint;
    }

    @Nullable
    public SurfaceControl getContentOverlay() {
        return mPipContentOverlay == null ? null : mPipContentOverlay.getLeash();
    }

    /** @return {@link PictureInPictureSurfaceTransaction} for the final leash transaction. */
    public PictureInPictureSurfaceTransaction getFinishTransaction() {
        // get the final leash operations but do not apply to the leash.
        final SurfaceControl.Transaction tx =
                PipSurfaceTransactionHelper.newSurfaceControlTransaction();
        final PictureInPictureSurfaceTransaction pipTx =
                onAnimationUpdate(tx, new RectF(mDestinationBounds), END_PROGRESS);
        try {
            pipTx.setShouldDisableCanAffectSystemUiFlags(true);
        } catch (NoSuchMethodError error) {
            Log.w(TAG, "not android 13 qpr1 : ", error);
        }
        return pipTx;
    }

    private RotatedPosition getRotatedPosition(float progress) {
        final float degree, positionX, positionY;
        if (TaskAnimationManager.SHELL_TRANSITIONS_ROTATION) {
            if (mFromRotation == Surface.ROTATION_90) {
                degree = -90 * (1 - progress);
                positionX = progress * (mDestinationBoundsTransformed.left - mStartBounds.left)
                        + mStartBounds.left;
                positionY = progress * (mDestinationBoundsTransformed.top - mStartBounds.top)
                        + mStartBounds.top + mStartBounds.bottom * (1 - progress);
            } else {
                degree = 90 * (1 - progress);
                positionX = progress * (mDestinationBoundsTransformed.left - mStartBounds.left)
                        + mStartBounds.left + mStartBounds.right * (1 - progress);
                positionY = progress * (mDestinationBoundsTransformed.top - mStartBounds.top)
                        + mStartBounds.top;
            }
        } else {
            if (mFromRotation == Surface.ROTATION_90) {
                degree = -90 * progress;
                positionX = progress * (mDestinationBoundsTransformed.left - mStartBounds.left)
                        + mStartBounds.left;
                positionY = progress * (mDestinationBoundsTransformed.bottom - mStartBounds.top)
                        + mStartBounds.top;
            } else {
                degree = 90 * progress;
                positionX = progress * (mDestinationBoundsTransformed.right - mStartBounds.left)
                        + mStartBounds.left;
                positionY = progress * (mDestinationBoundsTransformed.top - mStartBounds.top)
                        + mStartBounds.top;
            }
        }

        return new RotatedPosition(degree, positionX, positionY);
    }

    /** Builder class for {@link SwipePipToHomeAnimator} */
    public static class Builder {
        private Context mContext;
        private int mTaskId;
        private ActivityInfo mActivityInfo;
        private int mAppIconSizePx;
        private SurfaceControl mLeash;
        private Rect mSourceRectHint;
        private Rect mDisplayCutoutInsets;
        private Rect mAppBounds;
        private Matrix mHomeToWindowPositionMap;
        private RectF mStartBounds;
        private Rect mDestinationBounds;
        private int mCornerRadius;
        private int mShadowRadius;
        private View mAttachedView;
        private @RecentsOrientedState.SurfaceRotation int mFromRotation = Surface.ROTATION_0;
        private final Rect mDestinationBoundsTransformed = new Rect();

        public Builder setContext(Context context) {
            mContext = context;
            return this;
        }

        public Builder setTaskId(int taskId) {
            mTaskId = taskId;
            return this;
        }

        public Builder setActivityInfo(ActivityInfo activityInfo) {
            mActivityInfo = activityInfo;
            return this;
        }

        public Builder setAppIconSizePx(int appIconSizePx) {
            mAppIconSizePx = appIconSizePx;
            return this;
        }

        public Builder setLeash(SurfaceControl leash) {
            mLeash = leash;
            return this;
        }

        public Builder setSourceRectHint(Rect sourceRectHint) {
            mSourceRectHint = new Rect(sourceRectHint);
            return this;
        }

        public Builder setAppBounds(Rect appBounds) {
            mAppBounds = new Rect(appBounds);
            return this;
        }

        public Builder setHomeToWindowPositionMap(Matrix homeToWindowPositionMap) {
            mHomeToWindowPositionMap = new Matrix(homeToWindowPositionMap);
            return this;
        }

        public Builder setStartBounds(RectF startBounds) {
            mStartBounds = new RectF(startBounds);
            return this;
        }

        public Builder setDestinationBounds(Rect destinationBounds) {
            mDestinationBounds = new Rect(destinationBounds);
            return this;
        }

        public Builder setCornerRadius(int cornerRadius) {
            mCornerRadius = cornerRadius;
            return this;
        }

        public Builder setShadowRadius(int shadowRadius) {
            mShadowRadius = shadowRadius;
            return this;
        }

        public Builder setAttachedView(View attachedView) {
            mAttachedView = attachedView;
            return this;
        }

        public Builder setFromRotation(TaskViewSimulator taskViewSimulator,
                @RecentsOrientedState.SurfaceRotation int fromRotation,
                Rect displayCutoutInsets) {
            if (fromRotation != Surface.ROTATION_90 && fromRotation != Surface.ROTATION_270) {
                Log.wtf(TAG, "Not a supported rotation, rotation=" + fromRotation);
                return this;
            }
            final Matrix matrix = new Matrix();
            taskViewSimulator.applyWindowToHomeRotation(matrix);

            // map the destination bounds into window space. mDestinationBounds is always calculated
            // in the final home space and the animation runs in original window space.
            final RectF transformed = new RectF(mDestinationBounds);
            matrix.mapRect(transformed, new RectF(mDestinationBounds));
            transformed.round(mDestinationBoundsTransformed);

            mFromRotation = fromRotation;
            if (displayCutoutInsets != null) {
                mDisplayCutoutInsets = new Rect(displayCutoutInsets);
            }
            return this;
        }

        public Builder setDisplayCutoutInsets(@NonNull Rect displayCutoutInsets) {
            mDisplayCutoutInsets = new Rect(displayCutoutInsets);
            return this;
        }

        public SwipePipToHomeAnimator build() {
            if (mDestinationBoundsTransformed.isEmpty()) {
                mDestinationBoundsTransformed.set(mDestinationBounds);
            }
            // adjust the mSourceRectHint / mAppBounds by display cutout if applicable.
            if (mSourceRectHint != null && mDisplayCutoutInsets != null) {
                if (mFromRotation == Surface.ROTATION_0) {
                    // TODO: this is to special case the issues on Foldable device
                    // with display cutout.
                    mSourceRectHint.offset(mDisplayCutoutInsets.left, mDisplayCutoutInsets.top);
                } else if (mFromRotation == Surface.ROTATION_90) {
                    mSourceRectHint.offset(mDisplayCutoutInsets.left, mDisplayCutoutInsets.top);
                } else if (mFromRotation == Surface.ROTATION_270) {
                    mAppBounds.inset(mDisplayCutoutInsets);
                }
            }
            return new SwipePipToHomeAnimator(mContext, mTaskId, mActivityInfo, mAppIconSizePx,
                    mLeash, mSourceRectHint, mAppBounds,
                    mHomeToWindowPositionMap, mStartBounds, mDestinationBounds,
                    mFromRotation, mDestinationBoundsTransformed,
                    mCornerRadius, mShadowRadius, mAttachedView);
        }
    }

    private record RotatedPosition(float degree, float positionX, float positionY) {
    }
}
