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
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

/*
 * Ths bar will manage the transition between the QSB search bar and the delete drop
 * targets so that each of the individual IconDropTargets don't have to.
 */
public class SearchDropTargetBar extends FrameLayout implements DragController.DragListener {

    private static final int TRANSITION_DURATION = 200;

    private ObjectAnimator mShowDropTargetBarAnim;
    private ValueAnimator mHideSearchBarAnim;
    private static final AccelerateInterpolator sAccelerateInterpolator =
            new AccelerateInterpolator();

    private boolean mIsSearchBarHidden;
    private View mQSBSearchBar;
    private View mDropTargetBar;
    private boolean mDeferOnDragEnd = false;

    // Drop targets
    private ButtonDropTarget mInfoDropTarget;
    private ButtonDropTarget mDeleteDropTarget;
    private ButtonDropTarget mUninstallDropTarget;

    public SearchDropTargetBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchDropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setup(Launcher launcher, DragController dragController) {
        dragController.addDragListener(this);
        dragController.setFlingToDeleteDropTarget(mDeleteDropTarget);

        dragController.addDragListener(mInfoDropTarget);
        dragController.addDragListener(mDeleteDropTarget);
        dragController.addDragListener(mUninstallDropTarget);

        dragController.addDropTarget(mInfoDropTarget);
        dragController.addDropTarget(mDeleteDropTarget);
        dragController.addDropTarget(mUninstallDropTarget);

        mInfoDropTarget.setLauncher(launcher);
        mDeleteDropTarget.setLauncher(launcher);
        mUninstallDropTarget.setLauncher(launcher);
    }

    public void setQsbSearchBar(View qsb) {
        mQSBSearchBar = qsb;
        if (mQSBSearchBar != null) {
            mHideSearchBarAnim = LauncherAnimUtils.ofFloat(mQSBSearchBar, "alpha", 1f, 0f);
            setupAnimation(mHideSearchBarAnim, mQSBSearchBar);
        } else {
            // Create a no-op animation of the search bar is null
            mHideSearchBarAnim = ValueAnimator.ofFloat(0, 0);
            mHideSearchBarAnim.setDuration(TRANSITION_DURATION);
        }
    }

    private void prepareStartAnimation(View v) {
        // Enable the hw layers before the animation starts (will be disabled in the onAnimationEnd
        // callback below)
        if (v != null) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
    }

    private void setupAnimation(ValueAnimator anim, final View v) {
        anim.setInterpolator(sAccelerateInterpolator);
        anim.setDuration(TRANSITION_DURATION);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (v != null) {
                    v.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the individual components
        mDropTargetBar = findViewById(R.id.drag_target_bar);
        mInfoDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.info_target_text);
        mDeleteDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.delete_target_text);
        mUninstallDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.uninstall_target_text);

        mInfoDropTarget.setSearchDropTargetBar(this);
        mDeleteDropTarget.setSearchDropTargetBar(this);
        mUninstallDropTarget.setSearchDropTargetBar(this);

        // Create the various fade animations
        mDropTargetBar.setAlpha(0f);
        mShowDropTargetBarAnim = LauncherAnimUtils.ofFloat(mDropTargetBar, "alpha", 0f, 1f);
        setupAnimation(mShowDropTargetBarAnim, mDropTargetBar);
    }

    /**
     * Finishes all the animations on the search and drop target bars.
     */
    public void finishAnimations() {
        prepareStartAnimation(mDropTargetBar);
        mShowDropTargetBarAnim.reverse();
        prepareStartAnimation(mQSBSearchBar);
        mHideSearchBarAnim.reverse();
    }

    /**
     * Shows the search bar.
     */
    public void showSearchBar(boolean animated) {
        if (!mIsSearchBarHidden) return;
        if (animated) {
            prepareStartAnimation(mQSBSearchBar);
            mHideSearchBarAnim.reverse();
        } else {
            mHideSearchBarAnim.cancel();
            if (mQSBSearchBar != null) {
                mQSBSearchBar.setAlpha(1f);
            }
        }
        mIsSearchBarHidden = false;
    }

    /**
     * Hides the search bar.  We only use this for clings.
     */
    public void hideSearchBar(boolean animated) {
        if (mIsSearchBarHidden) return;
        if (animated) {
            prepareStartAnimation(mQSBSearchBar);
            mHideSearchBarAnim.start();
        } else {
            mHideSearchBarAnim.cancel();
            if (mQSBSearchBar != null) {
                mQSBSearchBar.setAlpha(0f);
            }
        }
        mIsSearchBarHidden = true;
    }

    /**
     * Shows the drop target bar.
     */
    public void showDeleteTarget() {
        // Animate out the QSB search bar, and animate in the drop target bar
        prepareStartAnimation(mDropTargetBar);
        mShowDropTargetBarAnim.start();
        hideSearchBar(true);
    }

    /**
     * Hides the drop target bar.
     */
    public void hideDeleteTarget() {
        // Restore the QSB search bar, and animate out the drop target bar
        prepareStartAnimation(mDropTargetBar);
        mShowDropTargetBarAnim.reverse();
        showSearchBar(true);
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        showDeleteTarget();
    }

    public void deferOnDragEnd() {
        mDeferOnDragEnd = true;
    }

    @Override
    public void onDragEnd() {
        if (!mDeferOnDragEnd) {
            hideDeleteTarget();
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public Rect getSearchBarBounds() {
        if (mQSBSearchBar != null) {
            final int[] pos = new int[2];
            mQSBSearchBar.getLocationOnScreen(pos);

            final Rect rect = new Rect();
            rect.left = pos[0];
            rect.top = pos[1];
            rect.right = pos[0] + mQSBSearchBar.getWidth();
            rect.bottom = pos[1] + mQSBSearchBar.getHeight();
            return rect;
        } else {
            return null;
        }
    }

    public void enableAccessibleDrag(boolean enable) {
        if (mQSBSearchBar != null) {
            mQSBSearchBar.setVisibility(enable ? View.GONE : View.VISIBLE);
        }
        mInfoDropTarget.enableAccessibleDrag(enable);
        mDeleteDropTarget.enableAccessibleDrag(enable);
        mUninstallDropTarget.enableAccessibleDrag(enable);
    }
}
