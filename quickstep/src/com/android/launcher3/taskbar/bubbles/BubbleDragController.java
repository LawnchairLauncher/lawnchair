/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar.bubbles;

import android.annotation.SuppressLint;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.wm.shell.common.bubbles.RelativeTouchListener;

/**
 * Controls bubble bar drag to dismiss interaction.
 * Interacts with {@link BubbleDismissController}, used by {@link BubbleBarViewController}.
 * Supported interactions:
 * - Drag a single bubble view into dismiss target to remove it.
 * - Drag the bubble stack into dismiss target to remove all.
 * Restores initial position of dragged view if released outside of the dismiss target.
 */
public class BubbleDragController {
    private final TaskbarActivityContext mActivity;
    private BubbleBarViewController mBubbleBarViewController;
    private BubbleDismissController mBubbleDismissController;

    public BubbleDragController(TaskbarActivityContext activity) {
        mActivity = activity;
    }

    /**
     * Initializes dependencies when bubble controllers are created.
     * Should be careful to only access things that were created in constructors for now, as some
     * controllers may still be waiting for init().
     */
    public void init(@NonNull BubbleControllers bubbleControllers) {
        mBubbleBarViewController = bubbleControllers.bubbleBarViewController;
        mBubbleDismissController = bubbleControllers.bubbleDismissController;
    }

    /**
     * Setup the bubble view for dragging and attach touch listener to it
     */
    @SuppressLint("ClickableViewAccessibility")
    public void setupBubbleView(@NonNull BubbleView bubbleView) {
        // Don't setup dragging for overflow bubble view
        if (bubbleView.getBubble() == null
                || !(bubbleView.getBubble() instanceof BubbleBarBubble)) return;
        bubbleView.setOnTouchListener(new BaseDragListener() {
            @Override
            protected void onDragStart() {
                super.onDragStart();
                mBubbleBarViewController.onDragStart(bubbleView);
            }

            @Override
            protected void onDragEnd() {
                super.onDragEnd();
                mBubbleBarViewController.onDragEnd(bubbleView);
            }
        });
    }

    /**
     * Setup the bubble bar view for dragging and attach touch listener to it
     */
    @SuppressLint("ClickableViewAccessibility")
    public void setupBubbleBarView(@NonNull BubbleBarView bubbleBarView) {
        PointF initialRelativePivot = new PointF();
        bubbleBarView.setOnTouchListener(new BaseDragListener() {
            @Override
            public boolean onDown(@NonNull View view, @NonNull MotionEvent event) {
                if (bubbleBarView.isExpanded()) return false;
                // Setup dragging only when bubble bar is collapsed
                return super.onDown(view, event);
            }

            @Override
            protected void onDragStart() {
                super.onDragStart();
                initialRelativePivot.set(bubbleBarView.getRelativePivotX(),
                        bubbleBarView.getRelativePivotY());
                bubbleBarView.setRelativePivot(/* x = */ 0.5f, /* y = */ 0.5f);
            }

            @Override
            protected void onDragEnd() {
                super.onDragEnd();
                bubbleBarView.setRelativePivot(initialRelativePivot.x, initialRelativePivot.y);
            }
        });
    }

    /**
     * Base drag listener for handling a single bubble view or bubble bar view dragging.
     * Controls dragging interaction and interacts with {@link BubbleDismissController}
     * to coordinate dismiss view presentation.
     * Lifecycle methods can be overridden do add extra setup/clean up steps
     */
    private class BaseDragListener extends RelativeTouchListener {
        private boolean mHandling;
        private boolean mDragging;

        @Override
        public boolean onDown(@NonNull View view, @NonNull MotionEvent event) {
            mHandling = true;
            mActivity.setTaskbarWindowFullscreen(true);
            mBubbleDismissController.setupDismissView(view);
            mBubbleDismissController.handleTouchEvent(event);
            return true;
        }

        @Override
        public void onMove(@NonNull View view, @NonNull MotionEvent event, float viewInitialX,
                float viewInitialY, float dx, float dy) {
            if (!mHandling) return;
            if (!mDragging) {
                // Start dragging
                mDragging = true;
                onDragStart();
            }
            if (!mBubbleDismissController.handleTouchEvent(event)) {
                // Drag the view if not processed by dismiss controller
                view.setTranslationX(viewInitialX + dx);
                view.setTranslationY(viewInitialY + dy);
            }
        }

        @Override
        public void onUp(@NonNull View view, @NonNull MotionEvent event, float viewInitialX,
                float viewInitialY, float dx, float dy, float velX, float velY) {
            onComplete(view, event, viewInitialX, viewInitialY);
        }

        @Override
        public void onCancel(@NonNull View view, @NonNull MotionEvent event, float viewInitialX,
                float viewInitialY, float dx, float dy) {
            onComplete(view, event, viewInitialX, viewInitialY);
        }

        /**
         * Prepares dismiss view for dragging.
         * This method can be overridden to add extra setup on drag start
         */
        protected void onDragStart() {
            mBubbleDismissController.showDismissView();
        }

        /**
         * Cleans up dismiss view after dragging.
         * This method can be overridden to add extra setup on drag end
         */
        protected void onDragEnd() {
            mBubbleDismissController.hideDismissView();
        }

        /**
         * Complete drag handling and clean up dependencies
         */
        private void onComplete(@NonNull View view, @NonNull MotionEvent event,
                float viewInitialX, float viewInitialY) {
            if (!mHandling) return;
            if (mDragging) {
                // Stop dragging
                mDragging = false;
                view.setTranslationX(viewInitialX);
                view.setTranslationY(viewInitialY);
                onDragEnd();
            }
            mBubbleDismissController.handleTouchEvent(event);
            mActivity.setTaskbarWindowFullscreen(false);
            mHandling = false;
        }
    }
}
