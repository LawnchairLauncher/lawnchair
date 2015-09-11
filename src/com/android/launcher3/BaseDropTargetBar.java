/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import com.android.launcher3.dragndrop.DragController;

/**
 * Base class for drop target bars (where you can drop apps to do actions such as uninstall).
 */
public abstract class BaseDropTargetBar extends FrameLayout implements DragController.DragListener {
    protected static final int DEFAULT_DRAG_FADE_DURATION = 175;

    protected View mDropTargetBar;

    protected LauncherViewPropertyAnimator mDropTargetBarAnimator;
    protected static final AccelerateInterpolator sAccelerateInterpolator =
            new AccelerateInterpolator();
    protected boolean mAccessibilityEnabled = false;

    protected boolean mDeferOnDragEnd;

    public BaseDropTargetBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseDropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mDropTargetBar = findViewById(R.id.drag_target_bar);

        // Create the various fade animations
        mDropTargetBar.setAlpha(0f);
        mDropTargetBarAnimator = new LauncherViewPropertyAnimator(mDropTargetBar);
        mDropTargetBarAnimator.setInterpolator(sAccelerateInterpolator);
        mDropTargetBarAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Ensure that the view is visible for the animation
                mDropTargetBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (mDropTargetBar != null) {
                    AlphaUpdateListener.updateVisibility(mDropTargetBar, mAccessibilityEnabled);
                }
            }
        });
    }


    /**
     * Convenience method to animate the alpha of a view using hardware layers.
     */
    protected void animateViewAlpha(LauncherViewPropertyAnimator animator, View v, float alpha,
                                  int duration) {
        if (v == null) {
            return;
        }

        animator.cancel();
        if (Float.compare(v.getAlpha(), alpha) != 0) {
            if (duration > 0) {
                animator.alpha(alpha).withLayer().setDuration(duration).start();
            } else {
                v.setAlpha(alpha);
                AlphaUpdateListener.updateVisibility(v, mAccessibilityEnabled);
            }
        }
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DragSource source, ItemInfo info, int dragAction) {
        showDropTarget();
    }

    /**
     * This is called to defer hiding the delete drop target until the drop animation has completed,
     * instead of hiding immediately when the drag has ended.
     */
    protected void deferOnDragEnd() {
        mDeferOnDragEnd = true;
    }

    @Override
    public void onDragEnd() {
        if (!mDeferOnDragEnd) {
            hideDropTarget();
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public abstract void showDropTarget();

    public abstract void hideDropTarget();

    public abstract void enableAccessibleDrag(boolean enable);

    public abstract void setup(Launcher launcher, DragController dragController);
}
