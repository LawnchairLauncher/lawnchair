/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import android.annotation.Nullable;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.wm.shell.bubbles.bar.BubbleBarLayerView;

/**
 * {@link BubbleTransitions} needs to perform various actions on the bubble expanded view and its
 * parent. There are two variants of the expanded view -- one belonging to {@link BubbleStackView}
 * and one belonging to {@link BubbleBarLayerView}. This interface is implemented by the view
 * parents to allow the transitions to modify and animate the expanded view.
 * <p>
 * TODO (b/349844986):
 * Ideally we could have a single type of expanded view (or view animator) used by both stackView
 * & layerView and then the transitions could perhaps operate on that directly.
 */
public interface BubbleExpandedViewTransitionAnimator {

    /**
     * Whether bubble UI is currently expanded.
     */
    boolean isExpanded();

    /**
     * Whether it's possible to expand {@param bubble} right now. This is {@code false} if the
     * bubble has no view or if the bubble is already showing.
     */
    boolean canExpandView(BubbleViewProvider bubble);

    /**
     * Call to prepare the provided {@param bubble} to be animated.
     *
     * <p>Should make the current expanded bubble visible immediately so it gets a surface that can
     * be animated. Since the surface may not be ready yet, it should keep the TaskView alpha=0.
     *
     * @return the currently expanded bubble if it exists so that it can be removed later in the
     *         transition
     */
    @Nullable
    BubbleViewProvider prepareConvertedView(BubbleViewProvider bubble);

    /**
     * Animates a visible task into the bubble expanded view.
     *
     * @param startT A transaction with first-frame work. This *must* be applied here!
     * @param startBounds The starting bounds of the task being converted into a bubble.
     * @param startScale The starting scale of the task being converted into a bubble.
     * @param snapshot A snapshot of the task being converted into a bubble.
     * @param taskLeash The taskLeash of the task being converted into a bubble.
     * @param animFinish A runnable to run at the end of the animation.
     */
    void animateConvert(@NonNull SurfaceControl.Transaction startT,
            @NonNull Rect startBounds, float startScale, @NonNull SurfaceControl snapshot,
            SurfaceControl taskLeash, Runnable animFinish);

    /**
     * Animates a non-visible task into the bubble expanded view -- since there's no task
     * visible this just needs to expand the bubble stack (or animate out the previously
     * selected bubble if already expanded).
     *
     * @param previousBubble If non-null, this is a bubble that is already showing before the new
     *                       bubble is expanded.
     * @param animFinish If non-null, the callback triggered after the expand animation completes
     */
    void animateExpand(@Nullable BubbleViewProvider previousBubble, @Nullable Runnable animFinish);

    /**
     * Bubble transitions calls this when a view should be removed from the parent.
     */
    void removeViewFromTransition(View view);
}
