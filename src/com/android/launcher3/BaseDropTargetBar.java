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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import com.android.launcher3.dragndrop.DragController;

/**
 * Base class for drop target bars (where you can drop apps to do actions such as uninstall).
 */
public abstract class BaseDropTargetBar extends FrameLayout implements DragController.DragListener {
    protected static final int DEFAULT_DRAG_FADE_DURATION = 175;
    protected static final TimeInterpolator DEFAULT_INTERPOLATOR = new AccelerateInterpolator();

    protected View mDropTargetBar;
    protected boolean mAccessibilityEnabled = false;

    protected AnimatorSet mCurrentAnimation;
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
    }

    /**
     * Convenience method to animate the alpha of a view.
     */
    protected void animateAlpha(View v, float alpha, TimeInterpolator interpolator) {
        if (Float.compare(v.getAlpha(), alpha) != 0) {
            ObjectAnimator anim = ObjectAnimator.ofFloat(v, View.ALPHA, alpha);
            anim.setInterpolator(interpolator);
            anim.addListener(new ViewVisiblilyUpdateHandler(v));
            mCurrentAnimation.play(anim);
        }
    }

    protected void resetAnimation(int newAnimationDuration) {
        // Update the accessibility state
        AccessibilityManager am = (AccessibilityManager)
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAccessibilityEnabled = am.isEnabled();

        // Cancel any existing animation
        if (mCurrentAnimation != null) {
            mCurrentAnimation.cancel();
            mCurrentAnimation = null;
        }

        if (newAnimationDuration > 0) {
            mCurrentAnimation = new AnimatorSet();
            mCurrentAnimation.setDuration(newAnimationDuration);
        }
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DragSource source, ItemInfo info, int dragAction) {
        showDropTargets();
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
            hideDropTargets();
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public abstract void showDropTargets();

    public abstract void hideDropTargets();

    public abstract void enableAccessibleDrag(boolean enable);

    public abstract void setup(Launcher launcher, DragController dragController);

    private class ViewVisiblilyUpdateHandler extends AnimatorListenerAdapter {
        private final View mView;

        ViewVisiblilyUpdateHandler(View v) {
            mView = v;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            // Ensure that the view is visible for the animation
            mView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onAnimationEnd(Animator animation){
            AlphaUpdateListener.updateVisibility(mView, mAccessibilityEnabled);
        }

    }
}
