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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.util.Thunk;

/*
 * This bar will manage the transition between the QSB search bar and the delete/uninstall drop
 * targets so that each of the individual ButtonDropTargets don't have to.
 */
public class SearchDropTargetBar extends BaseDropTargetBar {

    /** The different states that the search bar space can be in. */
    public enum State {
        INVISIBLE   (0f, 0f),
        SEARCH_BAR  (1f, 0f),
        DROP_TARGET (0f, 1f);

        private final float mSearchBarAlpha;
        private final float mDropTargetBarAlpha;

        State(float sbAlpha, float dtbAlpha) {
            mSearchBarAlpha = sbAlpha;
            mDropTargetBarAlpha = dtbAlpha;
        }

        float getSearchBarAlpha() {
            return mSearchBarAlpha;
        }

        float getDropTargetBarAlpha() {
            return mDropTargetBarAlpha;
        }
    }


    private LauncherViewPropertyAnimator mQSBSearchBarAnimator;

    private State mState = State.SEARCH_BAR;
    @Thunk View mQSB;

    // Drop targets
    private ButtonDropTarget mDeleteDropTarget;
    private ButtonDropTarget mUninstallDropTarget;

    public SearchDropTargetBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchDropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the individual components
        mDeleteDropTarget = (ButtonDropTarget) mDropTargetBar.findViewById(R.id.delete_target_text);
        mUninstallDropTarget = (ButtonDropTarget) mDropTargetBar
                .findViewById(R.id.uninstall_target_text);

        mDeleteDropTarget.setDropTargetBar(this);
        mUninstallDropTarget.setDropTargetBar(this);
    }

    @Override
    public void setup(Launcher launcher, DragController dragController) {
        dragController.addDragListener(this);
        dragController.setFlingToDeleteDropTarget(mDeleteDropTarget);

        dragController.addDragListener(mDeleteDropTarget);
        dragController.addDragListener(mUninstallDropTarget);

        dragController.addDropTarget(mDeleteDropTarget);
        dragController.addDropTarget(mUninstallDropTarget);

        mDeleteDropTarget.setLauncher(launcher);
        mUninstallDropTarget.setLauncher(launcher);
    }

    @Override
    public void showDropTarget() {
        animateToState(State.DROP_TARGET, DEFAULT_DRAG_FADE_DURATION);
    }

    @Override
    public void hideDropTarget() {
        animateToState(State.SEARCH_BAR, DEFAULT_DRAG_FADE_DURATION);
    }

    public void setQsbSearchBar(View qsb) {
        mQSB = qsb;
        if (mQSB != null) {
            // Update the search bar animation
            mQSBSearchBarAnimator = new LauncherViewPropertyAnimator(mQSB);
            mQSBSearchBarAnimator.setInterpolator(sAccelerateInterpolator);
            mQSBSearchBarAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    // Ensure that the view is visible for the animation
                    if (mQSB != null) {
                        mQSB.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mQSB != null) {
                        AlphaUpdateListener.updateVisibility(mQSB, mAccessibilityEnabled);
                    }
                }
            });
        } else {
            mQSBSearchBarAnimator = null;
        }
    }

    /**
     * Animates the current search bar state to a new state.  If the {@param duration} is 0, then
     * the state is applied immediately.
     */
    public void animateToState(State newState, int duration) {
        if (mState != newState) {
            mState = newState;

            // Update the accessibility state
            AccessibilityManager am = (AccessibilityManager)
                    getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            mAccessibilityEnabled = am.isEnabled();

            animateViewAlpha(mQSBSearchBarAnimator, mQSB, newState.getSearchBarAlpha(),
                    duration);
            animateViewAlpha(mDropTargetBarAnimator, mDropTargetBar, newState.getDropTargetBarAlpha(),
                    duration);
        }
    }

    /**
     * @return the bounds of the QSB search bar.
     */
    public Rect getSearchBarBounds() {
        if (mQSB != null) {
            final int[] pos = new int[2];
            mQSB.getLocationOnScreen(pos);

            final Rect rect = new Rect();
            rect.left = pos[0];
            rect.top = pos[1];
            rect.right = pos[0] + mQSB.getWidth();
            rect.bottom = pos[1] + mQSB.getHeight();
            return rect;
        } else {
            return null;
        }
    }

    @Override
    public void enableAccessibleDrag(boolean enable) {
        if (mQSB != null) {
            mQSB.setVisibility(enable ? View.GONE : View.VISIBLE);
        }
        mDeleteDropTarget.enableAccessibleDrag(enable);
        mUninstallDropTarget.enableAccessibleDrag(enable);
    }
}
