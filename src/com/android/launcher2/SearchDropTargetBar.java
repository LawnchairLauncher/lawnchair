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

package com.android.launcher2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher.R;

/*
 * Ths bar will manage the transition between the QSB search bar and the delete drop
 * targets so that each of the individual IconDropTargets don't have to.
 */
public class SearchDropTargetBar extends FrameLayout implements DragController.DragListener {

    private static final int sTransitionInDuration = 275;
    private static final int sTransitionOutDuration = 125;

    private ObjectAnimator mDropTargetBarFadeInAnim;
    private ObjectAnimator mDropTargetBarFadeOutAnim;
    private ObjectAnimator mQSBSearchBarFadeInAnim;
    private ObjectAnimator mQSBSearchBarFadeOutAnim;

    private boolean mIsSearchBarHidden;
    private View mQSBSearchBar;
    private View mDropTargetBar;
    private ButtonDropTarget mInfoDropTarget;
    private ButtonDropTarget mDeleteDropTarget;

    public SearchDropTargetBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchDropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setup(Launcher launcher, DragController dragController) {
        dragController.addDragListener(this);
        dragController.addDragListener(mInfoDropTarget);
        dragController.addDragListener(mDeleteDropTarget);
        dragController.addDropTarget(mInfoDropTarget);
        dragController.addDropTarget(mDeleteDropTarget);
        mInfoDropTarget.setLauncher(launcher);
        mDeleteDropTarget.setLauncher(launcher);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the individual components
        mQSBSearchBar = findViewById(R.id.qsb_search_bar);
        mDropTargetBar = findViewById(R.id.drag_target_bar);
        mInfoDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.info_target);
        mDeleteDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.delete_target);

        // Create the various fade animations
        mDropTargetBarFadeInAnim = ObjectAnimator.ofFloat(mDropTargetBar, "alpha", 1f);
        mDropTargetBarFadeInAnim.setDuration(sTransitionInDuration);
        mDropTargetBarFadeInAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mDropTargetBar.setVisibility(View.VISIBLE);
            }
        });
        mDropTargetBarFadeOutAnim = ObjectAnimator.ofFloat(mDropTargetBar, "alpha", 0f);
        mDropTargetBarFadeOutAnim.setDuration(sTransitionOutDuration);
        mDropTargetBarFadeOutAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDropTargetBar.setVisibility(View.GONE);
            }
        });
        mQSBSearchBarFadeInAnim = ObjectAnimator.ofFloat(mQSBSearchBar, "alpha", 1f);
        mQSBSearchBarFadeInAnim.setDuration(sTransitionInDuration);
        mQSBSearchBarFadeInAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mQSBSearchBar.setVisibility(View.VISIBLE);
            }
        });
        mQSBSearchBarFadeOutAnim = ObjectAnimator.ofFloat(mQSBSearchBar, "alpha", 0f);
        mQSBSearchBarFadeOutAnim.setDuration(sTransitionOutDuration);
        mQSBSearchBarFadeOutAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mQSBSearchBar.setVisibility(View.GONE);
            }
        });
    }

    private void cancelAnimations() {
        mDropTargetBarFadeInAnim.cancel();
        mDropTargetBarFadeOutAnim.cancel();
        mQSBSearchBarFadeInAnim.cancel();
        mQSBSearchBarFadeOutAnim.cancel();
    }

    /*
     * Shows and hides the search bar.
     */
    public void showSearchBar(boolean animated) {
        cancelAnimations();
        if (animated) {
            mQSBSearchBarFadeInAnim.start();
        } else {
            mQSBSearchBar.setVisibility(View.VISIBLE);
            mQSBSearchBar.setAlpha(1f);
        }
        mIsSearchBarHidden = false;
    }
    public void hideSearchBar(boolean animated) {
        cancelAnimations();
        if (animated) {
            mQSBSearchBarFadeOutAnim.start();
        } else {
            mQSBSearchBar.setVisibility(View.GONE);
            mQSBSearchBar.setAlpha(0f);
        }
        mIsSearchBarHidden = true;
    }

    /*
     * Gets various transition durations.
     */
    public int getTransitionInDuration() {
        return sTransitionInDuration;
    }
    public int getTransitionOutDuration() {
        return sTransitionOutDuration;
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        // Animate out the QSB search bar, and animate in the drop target bar
        mDropTargetBarFadeInAnim.start();
        if (!mIsSearchBarHidden) {
            mQSBSearchBarFadeOutAnim.start();
        }
    }

    @Override
    public void onDragEnd() {
        // Restore the QSB search bar, and animate out the drop target bar
        mDropTargetBarFadeOutAnim.start();
        if (!mIsSearchBarHidden) {
            mQSBSearchBarFadeInAnim.start();
        }
    }
}
