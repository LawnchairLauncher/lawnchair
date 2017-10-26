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

import static com.android.launcher3.AlphaUpdateListener.updateVisibility;
import static com.android.launcher3.Utilities.isAccessibilityEnabled;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.AccelerateInterpolator;
import android.widget.LinearLayout;

import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;

import java.util.ArrayList;

/*
 * The top bar containing various drop targets: Delete/App Info/Uninstall.
 */
public class DropTargetBar extends LinearLayout implements DragController.DragListener {

    protected static final int DEFAULT_DRAG_FADE_DURATION = 175;
    protected static final TimeInterpolator DEFAULT_INTERPOLATOR = new AccelerateInterpolator();

    private final Runnable mFadeAnimationEndRunnable = new Runnable() {

        @Override
        public void run() {
            updateVisibility(DropTargetBar.this, isAccessibilityEnabled(getContext()));
        }
    };

    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mDeferOnDragEnd;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mVisible = false;

    private ButtonDropTarget[] mDropTargets;
    private ViewPropertyAnimator mCurrentAnimation;

    public DropTargetBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Initialize with hidden state
        setAlpha(0f);
    }

    public void setup(DragController dragController) {
        dragController.addDragListener(this);
        ArrayList<ButtonDropTarget> outList = new ArrayList<>();
        findDropTargets(this, outList);

        mDropTargets = new ButtonDropTarget[outList.size()];
        for (int i = 0; i < mDropTargets.length; i++) {
            mDropTargets[i] = outList.get(i);
            mDropTargets[i].setDropTargetBar(this);
            dragController.addDragListener(mDropTargets[i]);
            dragController.addDropTarget(mDropTargets[i]);
        }
    }

    private static void findDropTargets(View view, ArrayList<ButtonDropTarget> outTargets) {
        if (view instanceof ButtonDropTarget) {
            outTargets.add((ButtonDropTarget) view);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = vg.getChildCount() - 1; i >= 0; i--) {
                findDropTargets(vg.getChildAt(i), outTargets);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        boolean hideText = hideTextHelper(false /* shouldUpdateText */, false /* no-op */);
        if (hideTextHelper(true /* shouldUpdateText */, hideText)) {
            // Text has changed, so we need to re-measure.
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * Helper method that iterates through the children and returns whether any of the visible
     * {@link ButtonDropTarget} has truncated text.
     *
     * @param shouldUpdateText If True, updates the text of all children.
     * @param hideText If True and {@param shouldUpdateText} is True, clears the text of all
     *                 children; otherwise it sets the original text value.
     *
     *
     * @return If shouldUpdateText is True, returns whether any of the children updated their text.
     *         Else, returns whether any of the children have truncated their text.
     */
    private boolean hideTextHelper(boolean shouldUpdateText, boolean hideText) {
        boolean result = false;
        View visibleView;
        ButtonDropTarget dropTarget;
        for (int i = getChildCount() - 1; i >= 0; --i) {
            if (getChildAt(i) instanceof ButtonDropTarget) {
                visibleView = dropTarget = (ButtonDropTarget) getChildAt(i);
            } else if (getChildAt(i) instanceof ViewGroup) {
                // The Drop Target is wrapped in a FrameLayout.
                visibleView = getChildAt(i);
                dropTarget = (ButtonDropTarget) ((ViewGroup) visibleView).getChildAt(0);
            } else {
                // Ignore other views.
                continue;
            }

            if (visibleView.getVisibility() == View.VISIBLE) {
                if (shouldUpdateText) {
                    result |= dropTarget.updateText(hideText);
                } else if (dropTarget.isTextTruncated()) {
                    result = true;
                    break;
                }
            }
        }

        return result;
    }

    private void animateToVisibility(boolean isVisible) {
        if (mVisible != isVisible) {
            mVisible = isVisible;

            // Cancel any existing animation
            if (mCurrentAnimation != null) {
                mCurrentAnimation.cancel();
                mCurrentAnimation = null;
            }

            float finalAlpha = mVisible ? 1 : 0;
            if (Float.compare(getAlpha(), finalAlpha) != 0) {
                setVisibility(View.VISIBLE);
                mCurrentAnimation = animate().alpha(finalAlpha)
                        .setInterpolator(DEFAULT_INTERPOLATOR)
                        .setDuration(DEFAULT_DRAG_FADE_DURATION)
                        .withEndAction(mFadeAnimationEndRunnable);
            }

        }
    }

    /*
     * DragController.DragListener implementation
     */
    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        animateToVisibility(true);
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
            animateToVisibility(false);
        } else {
            mDeferOnDragEnd = false;
        }
    }

    public ButtonDropTarget[] getDropTargets() {
        return mDropTargets;
    }
}
