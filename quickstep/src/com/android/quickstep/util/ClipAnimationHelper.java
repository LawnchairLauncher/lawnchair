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

import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.launcher3.Utilities;
import com.android.systemui.shared.recents.utilities.RectFEvaluator;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;

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
        mSourceInsets.set(target.getContentInsets());
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

    public void applyTransform(RemoteAnimationTargetCompat[] targets, float progress) {
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
        for (RemoteAnimationTargetCompat app : targets) {
            if (app.mode == MODE_CLOSING) {
                mTmpMatrix.postTranslate(app.position.x, app.position.y);
                transaction.setMatrix(app.leash, mTmpMatrix)
                        .setWindowCrop(app.leash, mClipRect);
                if (app.isNotInRecents) {
                    transaction.setAlpha(app.leash, 1 - progress);
                }

                transaction.show(app.leash);
            }
        }
        transaction.apply();
    }

    public void offsetTarget(float scale, float offsetX, float offsetY) {
        synchronized (mTargetRect) {
            mTargetRect.set(mInitialTargetRect);
            Utilities.scaleRectFAboutCenter(mTargetRect, scale);
            mTargetRect.offset(offsetX, offsetY);
        }
    }
}
