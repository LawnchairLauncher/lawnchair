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

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.QuickScrubController.QUICK_SCRUB_TRANSLATION_Y_FACTOR;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.view.Surface;
import android.view.animation.Interpolator;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.utilities.RectFEvaluator;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplier;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplier.SurfaceParams;
import com.android.systemui.shared.system.TransactionCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

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
    // Set when the final window destination is changed, such as offsetting for quick scrub
    private final PointF mTargetOffset = new PointF();
    // The insets to be used for clipping the app window, which can be larger than mSourceInsets
    // if the aspect ratio of the target is smaller than the aspect ratio of the source rect. In
    // app window coordinates.
    private final RectF mSourceWindowClipInsets = new RectF();

    // The bounds of launcher (not including insets) in device coordinates
    public final Rect mHomeStackBounds = new Rect();

    // The clip rect in source app window coordinates
    private final Rect mClipRect = new Rect();
    private final RectFEvaluator mRectFEvaluator = new RectFEvaluator();
    private final Matrix mTmpMatrix = new Matrix();
    private final RectF mTmpRectF = new RectF();

    private float mTargetScale = 1f;
    private float mOffsetScale = 1f;
    private Interpolator mInterpolator = LINEAR;
    // We translate y slightly faster than the rest of the animation for quick scrub.
    private Interpolator mOffsetYInterpolator = LINEAR;

    // Whether to boost the opening animation target layers, or the closing
    private int mBoostModeTargetLayers = -1;
    // Wether or not applyTransform has been called yet since prepareAnimation()
    private boolean mIsFirstFrame = true;

    private BiFunction<RemoteAnimationTargetCompat, Float, Float> mTaskAlphaCallback =
            (t, a1) -> a1;

    private void updateSourceStack(RemoteAnimationTargetCompat target) {
        mSourceInsets.set(target.contentInsets);
        mSourceStackBounds.set(target.sourceContainerBounds);

        // TODO: Should sourceContainerBounds already have this offset?
        mSourceStackBounds.offsetTo(target.position.x, target.position.y);

    }

    public void updateSource(Rect homeStackBounds, RemoteAnimationTargetCompat target) {
        mHomeStackBounds.set(homeStackBounds);
        updateSourceStack(target);
    }

    public void updateTargetRect(TransformedRect targetRect) {
        mOffsetScale = targetRect.scale;
        mSourceRect.set(mSourceInsets.left, mSourceInsets.top,
                mSourceStackBounds.width() - mSourceInsets.right,
                mSourceStackBounds.height() - mSourceInsets.bottom);
        mTargetRect.set(targetRect.rect);
        Utilities.scaleRectFAboutCenter(mTargetRect, targetRect.scale);
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
        mSourceRect.set(scaledTargetRect);
    }

    public void prepareAnimation(boolean isOpening) {
        mBoostModeTargetLayers = isOpening ? MODE_OPENING : MODE_CLOSING;
    }

    public RectF applyTransform(RemoteAnimationTargetSet targetSet, float progress,
            @Nullable SyncRtSurfaceTransactionApplier syncTransactionApplier) {
        RectF currentRect;
        mTmpRectF.set(mTargetRect);
        Utilities.scaleRectFAboutCenter(mTmpRectF, mTargetScale);
        float offsetYProgress = mOffsetYInterpolator.getInterpolation(progress);
        progress = mInterpolator.getInterpolation(progress);
        currentRect =  mRectFEvaluator.evaluate(progress, mSourceRect, mTmpRectF);

        synchronized (mTargetOffset) {
            // Stay lined up with the center of the target, since it moves for quick scrub.
            currentRect.offset(mTargetOffset.x * mOffsetScale * progress,
                    mTargetOffset.y  * offsetYProgress);
        }

        mClipRect.left = (int) (mSourceWindowClipInsets.left * progress);
        mClipRect.top = (int) (mSourceWindowClipInsets.top * progress);
        mClipRect.right = (int)
                (mSourceStackBounds.width() - (mSourceWindowClipInsets.right * progress));
        mClipRect.bottom = (int)
                (mSourceStackBounds.height() - (mSourceWindowClipInsets.bottom * progress));

        SurfaceParams[] params = new SurfaceParams[targetSet.unfilteredApps.length];
        for (int i = 0; i < targetSet.unfilteredApps.length; i++) {
            RemoteAnimationTargetCompat app = targetSet.unfilteredApps[i];
            mTmpMatrix.setTranslate(app.position.x, app.position.y);
            Rect crop = app.sourceContainerBounds;
            float alpha = 1f;
            if (app.mode == targetSet.targetMode) {
                if (app.activityType != RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                    mTmpMatrix.setRectToRect(mSourceRect, currentRect, ScaleToFit.FILL);
                    mTmpMatrix.postTranslate(app.position.x, app.position.y);
                    crop = mClipRect;
                }

                if (app.isNotInRecents
                        || app.activityType == RemoteAnimationTargetCompat.ACTIVITY_TYPE_HOME) {
                    alpha = 1 - progress;
                }

                alpha = mTaskAlphaCallback.apply(app, alpha);
            }

            params[i] = new SurfaceParams(app.leash, alpha, mTmpMatrix, crop,
                    RemoteAnimationProvider.getLayer(app, mBoostModeTargetLayers));
        }
        applyParams(syncTransactionApplier, params);
        return currentRect;
    }

    private void applyParams(@Nullable SyncRtSurfaceTransactionApplier syncTransactionApplier,
            SurfaceParams[] params) {
        if (syncTransactionApplier != null) {
            syncTransactionApplier.scheduleApply(params);
        } else {
            TransactionCompat t = new TransactionCompat();
            for (SurfaceParams param : params) {
                SyncRtSurfaceTransactionApplier.applyParams(t, param);
            }
            t.setEarlyWakeup();
            t.apply();
        }
    }

    public void setTaskAlphaCallback(
            BiFunction<RemoteAnimationTargetCompat, Float, Float> callback) {
        mTaskAlphaCallback = callback;
    }

    public void offsetTarget(float scale, float offsetX, float offsetY, Interpolator interpolator) {
        synchronized (mTargetOffset) {
            mTargetOffset.set(offsetX, offsetY);
        }
        mTargetScale = scale;
        mInterpolator = interpolator;
        mOffsetYInterpolator = Interpolators.clampToProgress(mInterpolator, 0,
                QUICK_SCRUB_TRANSLATION_Y_FACTOR);
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
            mSourceInsets.set(activity.getDeviceProfile().getInsets());
        }

        TransformedRect targetRect = new TransformedRect();
        dl.getDescendantRectRelativeToSelf(ttv, targetRect.rect);
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
        ISystemUiProxy sysUiProxy = RecentsModel.getInstance(activity).getSystemUiProxy();
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

    public void drawForProgress(TaskThumbnailView ttv, Canvas canvas, float progress) {
        RectF currentRect =  mRectFEvaluator.evaluate(progress, mSourceRect, mTargetRect);
        canvas.translate(mSourceStackBounds.left - mHomeStackBounds.left,
                mSourceStackBounds.top - mHomeStackBounds.top);
        mTmpMatrix.setRectToRect(mTargetRect, currentRect, ScaleToFit.FILL);

        canvas.concat(mTmpMatrix);
        canvas.translate(mTargetRect.left, mTargetRect.top);

        float insetProgress = (1 - progress);
        ttv.drawOnCanvas(canvas,
                -mSourceWindowClipInsets.left * insetProgress,
                -mSourceWindowClipInsets.top * insetProgress,
                ttv.getMeasuredWidth() + mSourceWindowClipInsets.right * insetProgress,
                ttv.getMeasuredHeight() + mSourceWindowClipInsets.bottom * insetProgress,
                ttv.getCornerRadius() * progress);
    }

    public RectF getTargetRect() {
        return mTargetRect;
    }

    public RectF getSourceRect() {
        return mSourceRect;
    }
}
