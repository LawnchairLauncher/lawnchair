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

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.systemui.shared.recents.utilities.RectFEvaluator;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Utility class to handle window clip animation
 */
public class ClipAnimationHelper {

    // The bounds of the source app in device coordinates
    private final Rect mSourceStackBounds = new Rect();
    // The insets of the source app
    private final Rect mSourceInsets = new Rect();
    // The source app bounds with the source insets applied, in the source app window coordinates
    private final RectF mSourceRect = new RectF();
    // The bounds of the task view in launcher window coordinates
    private final RectF mTargetRect = new RectF();
    // Doesn't change after initialized, used as an anchor when changing mTargetRect
    private final RectF mInitialTargetRect = new RectF();
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

    public void updateSource(Rect homeStackBounds, RemoteAnimationTargetCompat target) {
        mHomeStackBounds.set(homeStackBounds);
        mSourceInsets.set(target.contentInsets);
        mSourceStackBounds.set(target.sourceContainerBounds);

        // TODO: Should sourceContainerBounds already have this offset?
        mSourceStackBounds.offsetTo(target.position.x, target.position.y);
    }

    public void updateTargetRect(Rect targetRect) {
        mSourceRect.set(mSourceInsets.left, mSourceInsets.top,
                mSourceStackBounds.width() - mSourceInsets.right,
                mSourceStackBounds.height() - mSourceInsets.bottom);
        mTargetRect.set(targetRect);
        mTargetRect.offset(mHomeStackBounds.left - mSourceStackBounds.left,
                mHomeStackBounds.top - mSourceStackBounds.top);

        mInitialTargetRect.set(mTargetRect);

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

    public void applyTransform(RemoteAnimationTargetSet targetSet, float progress) {
        RectF currentRect;
        synchronized (mTargetRect) {
            currentRect =  mRectFEvaluator.evaluate(progress, mSourceRect, mTargetRect);
            // Stay lined up with the center of the target, since it moves for quick scrub.
            currentRect.offset(mTargetRect.centerX() - currentRect.centerX(), 0);
        }

        mClipRect.left = (int) (mSourceWindowClipInsets.left * progress);
        mClipRect.top = (int) (mSourceWindowClipInsets.top * progress);
        mClipRect.right = (int)
                (mSourceStackBounds.width() - (mSourceWindowClipInsets.right * progress));
        mClipRect.bottom = (int)
                (mSourceStackBounds.height() - (mSourceWindowClipInsets.bottom * progress));

        mTmpMatrix.setRectToRect(mSourceRect, currentRect, ScaleToFit.FILL);

        TransactionCompat transaction = new TransactionCompat();
        for (RemoteAnimationTargetCompat app : targetSet.apps) {
            mTmpMatrix.postTranslate(app.position.x, app.position.y);
            transaction.setMatrix(app.leash, mTmpMatrix)
                    .setWindowCrop(app.leash, mClipRect);
            if (app.isNotInRecents) {
                transaction.setAlpha(app.leash, 1 - progress);
            }
            transaction.show(app.leash);
        }
        transaction.setEarlyWakeup();
        transaction.apply();
    }

    public void offsetTarget(float scale, float offsetX, float offsetY) {
        synchronized (mTargetRect) {
            mTargetRect.set(mInitialTargetRect);
            Utilities.scaleRectFAboutCenter(mTargetRect, scale);
            mTargetRect.offset(offsetX, offsetY);
        }
    }

    public void fromTaskThumbnailView(TaskThumbnailView ttv, RecentsView rv) {
        BaseDraggingActivity activity = BaseDraggingActivity.fromContext(ttv.getContext());
        BaseDragLayer dl = activity.getDragLayer();

        int[] pos = new int[2];
        dl.getLocationOnScreen(pos);
        mHomeStackBounds.set(0, 0, dl.getWidth(), dl.getHeight());
        mHomeStackBounds.offset(pos[0], pos[1]);

        if (rv.shouldUseMultiWindowTaskSizeStrategy()) {
            // TODO: Fetch multi-window target bounds from system-ui
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

            mSourceStackBounds.set(0, 0, taskWidth, taskHeight);
            // Align the task to bottom right (probably not true for seascape).
            mSourceStackBounds.offset(insets.left + fullDp.availableWidthPx - taskWidth,
                    insets.top + fullDp.availableHeightPx - taskHeight);
        } else {
            mSourceStackBounds.set(mHomeStackBounds);
            mSourceInsets.set(activity.getDeviceProfile().getInsets());
        }

        Rect targetRect = new Rect();
        dl.getDescendantRectRelativeToSelf(ttv, targetRect);
        updateTargetRect(targetRect);

        // Transform the clip relative to the target rect.
        float scale = mTargetRect.width() / mSourceRect.width();
        mSourceWindowClipInsets.left = mSourceWindowClipInsets.left * scale;
        mSourceWindowClipInsets.top = mSourceWindowClipInsets.top * scale;
        mSourceWindowClipInsets.right = mSourceWindowClipInsets.right * scale;
        mSourceWindowClipInsets.bottom = mSourceWindowClipInsets.bottom * scale;
    }

    public void drawForProgress(TaskThumbnailView ttv, Canvas canvas, float progress) {
        RectF currentRect;
        synchronized (mTargetRect) {
            currentRect =  mRectFEvaluator.evaluate(progress, mSourceRect, mTargetRect);
        }

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
}
