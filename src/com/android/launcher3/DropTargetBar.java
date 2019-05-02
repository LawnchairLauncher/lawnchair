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

import static com.android.launcher3.ButtonDropTarget.TOOLTIP_DEFAULT;
import static com.android.launcher3.ButtonDropTarget.TOOLTIP_LEFT;
import static com.android.launcher3.ButtonDropTarget.TOOLTIP_RIGHT;
import static com.android.launcher3.anim.AlphaUpdateListener.updateVisibility;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;

import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragController.DragListener;
import com.android.launcher3.dragndrop.DragOptions;

/*
 * The top bar containing various drop targets: Delete/App Info/Uninstall.
 */
public class DropTargetBar extends FrameLayout
        implements DragListener, Insettable {

    protected static final int DEFAULT_DRAG_FADE_DURATION = 175;
    protected static final TimeInterpolator DEFAULT_INTERPOLATOR = Interpolators.ACCEL;

    private final Runnable mFadeAnimationEndRunnable =
            () -> updateVisibility(DropTargetBar.this);

    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mDeferOnDragEnd;

    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mVisible = false;

    private ButtonDropTarget[] mDropTargets;
    private ViewPropertyAnimator mCurrentAnimation;

    private boolean mIsVertical = true;

    public DropTargetBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DropTargetBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDropTargets = new ButtonDropTarget[getChildCount()];
        for (int i = 0; i < mDropTargets.length; i++) {
            mDropTargets[i] = (ButtonDropTarget) getChildAt(i);
            mDropTargets[i].setDropTargetBar(this);
        }
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = Launcher.getLauncher(getContext()).getDeviceProfile();
        mIsVertical = grid.isVerticalBarLayout();

        lp.leftMargin = insets.left;
        lp.topMargin = insets.top;
        lp.bottomMargin = insets.bottom;
        lp.rightMargin = insets.right;
        int tooltipLocation = TOOLTIP_DEFAULT;

        if (grid.isVerticalBarLayout()) {
            lp.width = grid.dropTargetBarSizePx;
            lp.height = grid.availableHeightPx - 2 * grid.edgeMarginPx;
            lp.gravity = grid.isSeascape() ? Gravity.RIGHT : Gravity.LEFT;
            tooltipLocation = grid.isSeascape() ? TOOLTIP_LEFT : TOOLTIP_RIGHT;
        } else {
            int gap;
            if (grid.isTablet) {
                // XXX: If the icon size changes across orientations, we will have to take
                //      that into account here too.
                gap = ((grid.widthPx - 2 * grid.edgeMarginPx
                        - (grid.inv.numColumns * grid.cellWidthPx))
                        / (2 * (grid.inv.numColumns + 1)))
                        + grid.edgeMarginPx;
            } else {
                gap = grid.desiredWorkspaceLeftRightMarginPx - grid.inv.defaultWidgetPadding.right;
            }
            lp.width = grid.availableWidthPx - 2 * gap;

            lp.topMargin += grid.edgeMarginPx;
            lp.height = grid.dropTargetBarSizePx;
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        }
        setLayoutParams(lp);
        for (ButtonDropTarget button : mDropTargets) {
            button.setToolTipLocation(tooltipLocation);
        }
    }

    public void setup(DragController dragController) {
        dragController.addDragListener(this);
        for (int i = 0; i < mDropTargets.length; i++) {
            dragController.addDragListener(mDropTargets[i]);
            dragController.addDropTarget(mDropTargets[i]);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        if (mIsVertical) {
            int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
            int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);

            for (ButtonDropTarget button : mDropTargets) {
                if (button.getVisibility() != GONE) {
                    button.setTextVisible(false);
                    button.measure(widthSpec, heightSpec);
                }
            }
        } else {
            int visibleCount = getVisibleButtonsCount();
            int availableWidth = width / visibleCount;
            boolean textVisible = true;
            for (ButtonDropTarget buttons : mDropTargets) {
                if (buttons.getVisibility() != GONE) {
                    textVisible = textVisible && !buttons.isTextTruncated(availableWidth);
                }
            }

            int widthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST);
            int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
            for (ButtonDropTarget button : mDropTargets) {
                if (button.getVisibility() != GONE) {
                    button.setTextVisible(textVisible);
                    button.measure(widthSpec, heightSpec);
                }
            }
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mIsVertical) {
            int gap = getResources().getDimensionPixelSize(R.dimen.drop_target_vertical_gap);
            int start = gap;
            int end;

            for (ButtonDropTarget button : mDropTargets) {
                if (button.getVisibility() != GONE) {
                    end = start + button.getMeasuredHeight();
                    button.layout(0, start, button.getMeasuredWidth(), end);
                    start = end + gap;
                }
            }
        } else {
            int visibleCount = getVisibleButtonsCount();
            int frameSize = (right - left) / visibleCount;

            int start = frameSize / 2;
            int halfWidth;
            for (ButtonDropTarget button : mDropTargets) {
                if (button.getVisibility() != GONE) {
                    halfWidth = button.getMeasuredWidth() / 2;
                    button.layout(start - halfWidth, 0,
                            start + halfWidth, button.getMeasuredHeight());
                    start = start + frameSize;
                }
            }
        }
    }

    private int getVisibleButtonsCount() {
        int visibleCount = 0;
        for (ButtonDropTarget buttons : mDropTargets) {
            if (buttons.getVisibility() != GONE) {
                visibleCount++;
            }
        }
        return visibleCount;
    }

    public void animateToVisibility(boolean isVisible) {
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
