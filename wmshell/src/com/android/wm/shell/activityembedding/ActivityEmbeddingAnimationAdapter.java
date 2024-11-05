/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.activityembedding;

import static android.graphics.Matrix.MTRANS_X;
import static android.graphics.Matrix.MTRANS_Y;

import android.annotation.CallSuper;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;

import com.android.wm.shell.shared.TransitionUtil;

/**
 * Wrapper to handle the ActivityEmbedding animation update in one
 * {@link SurfaceControl.Transaction}.
 */
class ActivityEmbeddingAnimationAdapter {

    /**
     * If {@link #mOverrideLayer} is set to this value, we don't want to override the surface layer.
     */
    private static final int LAYER_NO_OVERRIDE = -1;

    @NonNull
    final Animation mAnimation;
    @NonNull
    final TransitionInfo.Change mChange;
    @NonNull
    final SurfaceControl mLeash;
    /** Area in absolute coordinate that the animation surface shouldn't go beyond. */
    @NonNull
    final Rect mWholeAnimationBounds = new Rect();
    /**
     * Area in absolute coordinate that should represent all the content to show for this window.
     * This should be the end bounds for opening window, and start bounds for closing window in case
     * the window is resizing during the open/close transition.
     */
    @NonNull
    private final Rect mContentBounds = new Rect();
    /** Offset relative to the window parent surface for {@link #mContentBounds}. */
    @NonNull
    final Point mContentRelOffset = new Point();

    @NonNull
    final Transformation mTransformation = new Transformation();
    @NonNull
    final float[] mMatrix = new float[9];
    @NonNull
    final float[] mVecs = new float[4];
    @NonNull
    final Rect mRect = new Rect();
    private int mOverrideLayer = LAYER_NO_OVERRIDE;

    ActivityEmbeddingAnimationAdapter(@NonNull Animation animation,
            @NonNull TransitionInfo.Change change, @NonNull TransitionInfo.Root root) {
        this(animation, change, change.getLeash(), change.getEndAbsBounds(), root);
    }

    /**
     * @param leash the surface to animate, which is not necessary the same as
     *              {@link TransitionInfo.Change#getLeash()}, it can be a screenshot for example.
     * @param wholeAnimationBounds  area in absolute coordinate that the animation surface shouldn't
     *                              go beyond.
     */
    ActivityEmbeddingAnimationAdapter(@NonNull Animation animation,
            @NonNull TransitionInfo.Change change, @NonNull SurfaceControl leash,
            @NonNull Rect wholeAnimationBounds, @NonNull TransitionInfo.Root root) {
        mAnimation = animation;
        mChange = change;
        mLeash = leash;
        mWholeAnimationBounds.set(wholeAnimationBounds);

        final Rect startBounds = change.getStartAbsBounds();
        final Rect endBounds = change.getEndAbsBounds();
        if (change.getParent() != null) {
            mContentRelOffset.set(change.getEndRelOffset());
        } else {
            // Change leash has been reparented to the root if its parent is not in the transition.
            // Because it is reparented to the root, the actual offset should be its relative
            // position to the root instead. See Transitions#setupAnimHierarchy.
            final Point rootOffset = root.getOffset();
            mContentRelOffset.set(endBounds.left - rootOffset.x, endBounds.top - rootOffset.y);
        }

        if (TransitionUtil.isClosingType(change.getMode())) {
            // When it is closing, we want to show the content at the start position in case the
            // window is resizing as well. For example, when the activities is changing from split
            // to stack, the bottom TaskFragment will be resized to fullscreen when hiding.
            mContentBounds.set(startBounds);
            mContentRelOffset.offset(
                        startBounds.left - endBounds.left,
                        startBounds.top - endBounds.top);
        } else {
            mContentBounds.set(change.getEndAbsBounds());
        }
    }

    /**
     * Surface layer to be set at the first frame of the animation. We will not set the layer if it
     * is set to {@link #LAYER_NO_OVERRIDE}.
     */
    final void overrideLayer(int layer) {
        mOverrideLayer = layer;
    }

    /** Called to prepare for the starting state. */
    final void prepareForFirstFrame(@NonNull SurfaceControl.Transaction startTransaction) {
        startTransaction.show(mLeash);
        if (mOverrideLayer != LAYER_NO_OVERRIDE) {
            startTransaction.setLayer(mLeash, mOverrideLayer);
        }
        mAnimation.getTransformationAt(0, mTransformation);
        onAnimationUpdateInner(startTransaction);
    }

    /** Called on frame update. */
    final void onAnimationUpdate(@NonNull SurfaceControl.Transaction t, long currentPlayTime) {
        mTransformation.clear();
        // Extract the transformation to the current time.
        mAnimation.getTransformation(Math.min(currentPlayTime, mAnimation.getDuration()),
                mTransformation);
        onAnimationUpdateInner(t);
    }

    /** To be overridden by subclasses to adjust the animation surface change. */
    void onAnimationUpdateInner(@NonNull SurfaceControl.Transaction t) {
        // Update the surface position and alpha.
        mTransformation.getMatrix().postTranslate(mContentRelOffset.x, mContentRelOffset.y);
        t.setMatrix(mLeash, mTransformation.getMatrix(), mMatrix);
        t.setAlpha(mLeash, mTransformation.getAlpha());

        // Get current surface bounds in absolute coordinate.
        // positionX/Y are in local coordinate, so minus the local offset to get the slide amount.
        final int positionX = Math.round(mMatrix[MTRANS_X]);
        final int positionY = Math.round(mMatrix[MTRANS_Y]);
        final Rect cropRect = new Rect(mContentBounds);
        cropRect.offset(positionX - mContentRelOffset.x, positionY - mContentRelOffset.y);

        // Store the current offset of the surface top left from (0,0) in absolute coordinate.
        final int offsetX = cropRect.left;
        final int offsetY = cropRect.top;

        // Intersect to make sure the animation happens within the whole animation bounds.
        if (!cropRect.intersect(mWholeAnimationBounds)) {
            // Hide the surface when it is outside of the animation area.
            t.setAlpha(mLeash, 0);
        } else if (mAnimation.hasExtension()) {
            // Allow the surface to be shown in its original bounds in case we want to use edge
            // extensions.
            cropRect.union(mContentBounds);
        }

        // cropRect is in absolute coordinate, so we need to translate it to surface top left.
        cropRect.offset(-offsetX, -offsetY);
        t.setCrop(mLeash, cropRect);
    }

    /** Called after animation finished. */
    @CallSuper
    void onAnimationEnd(@NonNull SurfaceControl.Transaction t) {
        onAnimationUpdate(t, mAnimation.getDuration());
    }

    final long getDurationHint() {
        return mAnimation.computeDurationHint();
    }

    /**
     * Should be used for the animation of the snapshot of a {@link TransitionInfo.Change} that has
     * size change.
     */
    static class SnapshotAdapter extends ActivityEmbeddingAnimationAdapter {

        SnapshotAdapter(@NonNull Animation animation, @NonNull TransitionInfo.Change change,
                @NonNull SurfaceControl snapshotLeash, @NonNull TransitionInfo.Root root) {
            super(animation, change, snapshotLeash, change.getEndAbsBounds(), root);
        }

        @Override
        void onAnimationUpdateInner(@NonNull SurfaceControl.Transaction t) {
            // Snapshot should always be placed at the top left of the animation leash.
            mTransformation.getMatrix().postTranslate(0, 0);
            t.setMatrix(mLeash, mTransformation.getMatrix(), mMatrix);
            t.setAlpha(mLeash, mTransformation.getAlpha());
        }

        @Override
        void onAnimationEnd(@NonNull SurfaceControl.Transaction t) {
            super.onAnimationEnd(t);
            // Remove the screenshot leash after animation is finished.
            if (mLeash.isValid()) {
                t.remove(mLeash);
            }
        }
    }

    /**
     * Should be used for the animation of the {@link TransitionInfo.Change} that has size change.
     */
    static class BoundsChangeAdapter extends ActivityEmbeddingAnimationAdapter {

        BoundsChangeAdapter(@NonNull Animation animation, @NonNull TransitionInfo.Change change,
                @NonNull TransitionInfo.Root root) {
            super(animation, change, root);
        }

        @Override
        void onAnimationUpdateInner(@NonNull SurfaceControl.Transaction t) {
            mTransformation.getMatrix().postTranslate(mContentRelOffset.x, mContentRelOffset.y);
            t.setMatrix(mLeash, mTransformation.getMatrix(), mMatrix);
            t.setAlpha(mLeash, mTransformation.getAlpha());
            t.setWindowCrop(mLeash, mWholeAnimationBounds.width(), mWholeAnimationBounds.height());
        }
    }
}
